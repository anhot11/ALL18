package com.all18.nativeapp.core.util

import com.all18.nativeapp.core.model.VideoItem
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Gestor centralizado de frescura y anti-repeticion de contenidos para All18n.
 *
 * Funcionalidades clave:
 * 1. Seguimiento en memoria (LRU) de IDs de videos/publicaciones vistas (hasta 3000 IDs).
 * 2. Contador dinamico de ciclos de recarga para variar las paginas efectivas de los extractores.
 * 3. Rotacion y mezclado aleatorio (diversidad) del orden de plataformas antes del entrelazado.
 * 4. Priorizacion de videos no vistos previamente al recargar la pagina.
 */
object FeedFreshnessManager {
    private const val MAX_SEEN_CAPACITY = 3000
    private val seenIds = Collections.synchronizedSet(LinkedHashSet<String>())
    private val reloadCycle = AtomicInteger(0)

    /**
     * Incrementa y retorna el ciclo de recarga actual.
     * Al recargar (pull-to-refresh, clic en pestana o logo), este ciclo cambia.
     */
    fun nextReloadCycle(): Int = reloadCycle.incrementAndGet()

    fun currentCycle(): Int = reloadCycle.get()

    /**
     * Registra un ID de contenido visto para evitar repeticiones en posiciones superiores.
     */
    fun markSeen(id: String) {
        if (id.isBlank()) return
        seenIds.add(id)
        if (seenIds.size > MAX_SEEN_CAPACITY) {
            val it = seenIds.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    fun markAllSeen(ids: Collection<String>) {
        for (id in ids) {
            markSeen(id)
        }
    }

    fun isSeen(id: String): Boolean = seenIds.contains(id)

    /**
     * Calcula la pagina efectiva para una fuente concreta en funcion del ciclo de recarga.
     * Permite que cada fuente empiece en una pagina distinta (1..6) en cada recarga,
     * garantizando que nunca se pidan los mismos videos en la pagina 1.
     */
    fun getEffectivePage(source: String, basePage: Int): Int {
        val cycle = currentCycle()
        if (basePage > 1) {
            val offset = (cycle % 3)
            return basePage + offset
        }
        // basePage == 1: Ciclo dinamico por plataforma
        if (cycle == 0) return 1

        val salt = when (source.lowercase()) {
            "pornhub" -> 1
            "xhamster" -> 2
            "xvideos" -> 3
            "xnxx" -> 4
            "youporn" -> 5
            "eporner" -> 6
            "redtube" -> 7
            "porn300" -> 8
            "porntrex" -> 9
            "justporn" -> 10
            "porn.com", "porncom" -> 11
            "redgifs" -> 12
            else -> kotlin.math.abs(source.hashCode()) % 12 + 1
        }
        val pageOffset = ((cycle * salt) % 6)
        return 1 + pageOffset
    }

    private val BRAND_REGEX = Regex("(?i)\\b(pornhub|xvideos|xnxx|eporner|youporn|redtube|justporn|porn300|porntrex|xhamster|porn\\.com|redgifs|chaturbate|stripchat)\\b")
    private val BRACKET_REGEX = Regex("(?i)\\[.*?\\]|\\(.*?\\)|720p|1080p|4k|60fps|\\bhd\\b|full hd")
    private val NON_ALPHANUM_REGEX = Regex("[^\\p{L}\\p{Nd}\\s]")

    /**
     * Normaliza un título a un conjunto de tokens significativos para deduplicación cruzada entre plataformas.
     */
    fun normalizeTitle(title: String): Set<String> {
        val cleaned = title
            .replace(BRACKET_REGEX, " ")
            .replace(BRAND_REGEX, " ")
            .replace(NON_ALPHANUM_REGEX, " ")
            .lowercase()
        return cleaned.split("\\s+".toRegex())
            .filter { it.length > 2 }
            .toSet()
    }

    private fun isTitleDuplicate(tokens: Set<String>, seenTokenSets: List<Set<String>>): Boolean {
        if (tokens.size < 3) return false
        for (seen in seenTokenSets) {
            val intersection = tokens.intersect(seen).size
            val minSize = minOf(tokens.size, seen.size)
            if (minSize >= 3 && (intersection.toFloat() / minSize) >= 0.75f) {
                return true
            }
        }
        return false
    }

    /**
     * Algoritmo de interleave de máxima entropía y anti-clustering:
     * - Distribución uniforme que da paso justo y variado a todas las webs conectadas.
     * - Garantiza separación mínima entre videos de la misma plataforma (nunca dos consecutivos si hay >=2 webs).
     * - Deduplicación cruzada difusa por huella de título (evita clones idénticos re-subidos entre tubes).
     * - Priorización inteligente de contenidos no vistos sin agrupar los vistos al final.
     */
    fun interweaveDiverse(sourceLists: List<List<VideoItem>>, cycleSeed: Int = currentCycle()): List<VideoItem> {
        val nonEmpties = sourceLists.filter { it.isNotEmpty() }
        if (nonEmpties.isEmpty()) return emptyList()

        // Cada fuente tiene una cola de items únicos (por ID y por título)
        val queues = mutableMapOf<String, ArrayDeque<VideoItem>>()
        val globalSeenInBatch = mutableSetOf<String>()
        val seenTitleTokens = mutableListOf<Set<String>>()

        for (list in nonEmpties) {
            for (item in list) {
                if (item.id.isBlank() || globalSeenInBatch.contains(item.id)) continue

                val tokens = normalizeTitle(item.title)
                if (isTitleDuplicate(tokens, seenTitleTokens)) {
                    continue
                }

                globalSeenInBatch.add(item.id)
                if (tokens.size >= 3) {
                    seenTitleTokens.add(tokens)
                }

                val src = item.source.ifBlank { "Desconocido" }
                val q = queues.getOrPut(src) { ArrayDeque() }
                q.add(item)
            }
        }

        if (queues.isEmpty()) return emptyList()

        val result = mutableListOf<VideoItem>()
        val recentSources = ArrayDeque<String>() // Buffer FIFO de las últimas fuentes emitidas
        val maxRecentDistance = 2 // Distancia anti-clustering: evitar repetir fuente en los últimos 2 puestos

        val rng = Random(cycleSeed + System.currentTimeMillis().toInt())

        while (queues.isNotEmpty()) {
            // Filtrar candidatos que respeten la distancia anti-clustering
            var candidates = queues.keys.filter { it !in recentSources }

            if (candidates.isEmpty() && recentSources.isNotEmpty()) {
                // Relajar: evitar al menos la fuente inmediata anterior
                val lastSource = recentSources.lastOrNull()
                candidates = queues.keys.filter { it != lastSource }
            }

            if (candidates.isEmpty()) {
                // Último recurso cuando solo queda 1 fuente con items
                candidates = queues.keys.toList()
            }

            // Seleccionar entre los candidatos ordenando por mayor cantidad restante
            // y agregando entropía aleatoria entre los top 3 para máxima variedad
            val sortedCandidates = candidates.sortedByDescending { queues[it]?.size ?: 0 }
            val topN = minOf(3, sortedCandidates.size)
            val chosenSource = sortedCandidates.take(topN).random(rng)

            val queue = queues[chosenSource]
            if (queue != null && queue.isNotEmpty()) {
                // Preferir items no vistos previamente si existen en los primeros 3 de la cola elegida
                var chosenIndex = 0
                for (idx in 0 until minOf(3, queue.size)) {
                    val candidateItem = queue.elementAt(idx)
                    if (!isSeen(candidateItem.id)) {
                        chosenIndex = idx
                        break
                    }
                }

                val item = if (chosenIndex == 0) {
                    queue.removeFirst()
                } else {
                    val itList = queue.toMutableList()
                    val it = itList.removeAt(chosenIndex)
                    queue.clear()
                    queue.addAll(itList)
                    it
                }

                result.add(item)

                recentSources.addLast(chosenSource)
                while (recentSources.size > maxRecentDistance) {
                    recentSources.removeFirst()
                }

                if (queue.isEmpty()) {
                    queues.remove(chosenSource)
                }
            } else {
                queues.remove(chosenSource)
            }
        }

        return result
    }
}
