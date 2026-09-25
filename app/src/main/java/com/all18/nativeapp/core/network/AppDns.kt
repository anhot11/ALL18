package com.all18.nativeapp.core.network

import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Custom DNS implementation that bypasses ISP censorship / DNS sinkholes (such as 127.0.0.1 on eporner.com)
 * by falling back to Google DoH, Cloudflare DoH, and direct authoritative IP addresses.
 */
object AppDns : Dns {
    private const val TAG = "AppDns"
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    // Direct fallback IPs for eporner.com and subdomains
    private val epornerMainIps by lazy {
        listOf(
            InetAddress.getByName("94.75.220.4"),
            InetAddress.getByName("94.75.220.8"),
            InetAddress.getByName("94.75.220.10"),
            InetAddress.getByName("94.75.220.6")
        )
    }

    private val bootstrapClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // Direct handling for DNS providers to avoid recursion
        if (hostname == "dns.google") {
            return listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
        }
        if (hostname == "cloudflare-dns.com") {
            return listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
        }

        // 1. Return from cache if available
        cache[hostname]?.let { return it }

        // 2. Try system DNS first, filtering out ISP sinkhole loopback addresses (127.0.0.1, 0.0.0.0)
        try {
            val systemAddrs = Dns.SYSTEM.lookup(hostname)
            val clean = systemAddrs.filter { !it.isLoopbackAddress && it.hostAddress != "0.0.0.0" }
            if (clean.isNotEmpty()) {
                cache[hostname] = clean
                return clean
            }
        } catch (_: Exception) {}

        // 3. Resolve via Google DNS-over-HTTPS (DoH)
        try {
            val dohResult = resolveDnsOverHttps(hostname)
            if (dohResult.isNotEmpty()) {
                cache[hostname] = dohResult
                return dohResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google DoH lookup failed for $hostname: ${e.message}")
        }

        // 4. Resolve via Cloudflare DNS-over-HTTPS (DoH)
        try {
            val cfResult = resolveCloudflareDoh(hostname)
            if (cfResult.isNotEmpty()) {
                cache[hostname] = cfResult
                return cfResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloudflare DoH lookup failed for $hostname: ${e.message}")
        }

        // 5. Hardcoded fallbacks for eporner
        if (hostname.endsWith("eporner.com")) {
            return epornerMainIps
        }

        throw UnknownHostException("Unable to resolve host: $hostname")
    }

    private fun resolveDnsOverHttps(hostname: String): List<InetAddress> {
        val url = "https://dns.google/resolve?name=$hostname&type=A"
        val req = Request.Builder().url(url).build()
        val resp = bootstrapClient.newCall(req).execute()
        val body = resp.body?.string() ?: return emptyList()
        return parseDnsResponse(body)
    }

    private fun resolveCloudflareDoh(hostname: String): List<InetAddress> {
        val url = "https://cloudflare-dns.com/dns-query?name=$hostname&type=A"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()
        val resp = bootstrapClient.newCall(req).execute()
        val body = resp.body?.string() ?: return emptyList()
        return parseDnsResponse(body)
    }

    private fun parseDnsResponse(json: String): List<InetAddress> {
        val obj = JSONObject(json)
        val answers = obj.optJSONArray("Answer") ?: return emptyList()
        val list = mutableListOf<InetAddress>()
        for (i in 0 until answers.length()) {
            val item = answers.getJSONObject(i)
            if (item.optInt("type") == 1) { // Type A record
                val ip = item.optString("data", "")
                if (ip.isNotEmpty() && ip != "127.0.0.1" && ip != "0.0.0.0") {
                    try {
                        list.add(InetAddress.getByName(ip))
                    } catch (_: Exception) {}
                }
            }
        }
        return list
    }
}
