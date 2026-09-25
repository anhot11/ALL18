package com.all18.nativeapp.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object AppNetworkClient {
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(AppDns)
            .addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host.lowercase()
                val builder = original.newBuilder()

                // Inject standard browser User-Agent if absent
                if (original.header("User-Agent") == null) {
                    builder.header("User-Agent", DEFAULT_UA)
                }

                // Inject dynamic anti-hotlink Referer headers to ensure all CDN thumbnails load (403 bypass)
                if (original.header("Referer") == null) {
                    when {
                        host.contains("phncdn.com") || host.contains("pornhub.com") -> {
                            builder.header("Referer", "https://www.pornhub.com/")
                        }
                        host.contains("ypncdn.com") || host.contains("youporn.com") -> {
                            builder.header("Referer", "https://www.youporn.com/")
                        }
                        host.contains("rdtcdn.com") || host.contains("redtube.com") -> {
                            builder.header("Referer", "https://www.redtube.com/")
                        }
                        host.contains("eporner.com") -> {
                            builder.header("Referer", "https://www.eporner.com/")
                        }
                        host.contains("porn.com") -> {
                            builder.header("Referer", "https://es.porn.com/")
                        }
                        host.contains("justporn.com") || host.contains("mjedge.net") -> {
                            builder.header("Referer", "https://www.justporn.com/")
                        }
                        host.contains("xvideos") || host.contains("xnxx") -> {
                            builder.header("Referer", "https://www.xvideos.com/")
                        }
                        host.contains("xhamster") || host.contains("xhcdn") -> {
                            builder.header("Referer", "https://xhamster.com/")
                        }
                        host.contains("porn300") -> {
                            builder.header("Referer", "https://es.porn300.com/")
                        }
                        host.contains("porntrex") || host.contains("cdntrex") -> {
                            builder.header("Referer", "https://www.porntrex.com/")
                        }
                        host.contains("pichunter") -> {
                            builder.header("Referer", "https://www.pichunter.com/")
                        }
                    }
                }

                chain.proceed(builder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
