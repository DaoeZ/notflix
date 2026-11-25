package com.nuvio.tv.core.tmdb

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

internal data class Top10RankedCandidate(
    val tmdbId: Int,
    val title: String,
    val platforms: List<String>,
    val score: Double,
    val releaseYear: Int? = null,
    val originalTitle: String? = null
)

internal data class TudumWeeklyLists(
    val movies: List<String>,
    val series: List<String>,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

/**
 * Redesigned international multi-platform Top 10 engine for TV:
 * Crosses three real-time signals in parallel:
 *   1. JustWatch regional trending (titles actively streaming in the user's country)
 *   2. Trakt global trending momentum
 *   3. Netflix official weekly country Top 10 (Tudum public country page, cached 60 min)
 * Enforces max 1 title per franchise/saga and caps single-platform exclusivity (max 4/10)
 * so the Top 10 stays balanced across Netflix, Prime Video, HBO Max, Disney+, Apple TV+, etc.
 */
internal object NotflixTop10Ranker {
    private const val TAG = "NotflixTop10Ranker"
    private const val JUSTWATCH_URL = "https://apis.justwatch.com/graphql"
    private const val TIMEOUT_MS = 4_000L
    private const val MAX_EXCLUSIVE_PER_PLATFORM = 4
    private const val NETFLIX_WEIGHT = 1.2
    private const val CACHE_TTL_MS = 60L * 60L * 1000L

    private val SAGA_SPLIT_REGEX = Regex("\\s*[:\\-–—]\\s+")
    private val TRAILING_SEQUEL_REGEX = Regex("\\s+(?:part\\s+)?(?:\\d+|[ivx]{2,4})$", RegexOption.IGNORE_CASE)
    private val SEASON_SUFFIX_REGEX = Regex(":\\s*(?:Season \\d+|Limited Series|Part \\d+|Volume \\d+)$", RegexOption.IGNORE_CASE)
    private val TUDUM_ENTITY_REGEX = Regex("\"PulseTop10ItemEntity:top10-[^\"]*\":\\{\"__typename\":\"PulseTop10ItemEntity\"")
    private val TUDUM_META_REGEX = Regex("\"weekEndDate\":\"([^\"]+)\",\"category\":\"(\\w+)\",\"weeklyRank\":(\\d+)")
    private val TUDUM_TITLE_REGEX = Regex("\"title\":\"([^\"]+)\"")

    private val tudumCache = ConcurrentHashMap<String, TudumWeeklyLists>()
    private val tudumInFlight = ConcurrentHashMap<String, CompletableDeferred<TudumWeeklyLists?>>()

    private val TUDUM_COUNTRY_SLUGS: Map<String, String> = mapOf(
        "ES" to "spain", "MX" to "mexico", "AR" to "argentina", "CO" to "colombia",
        "CL" to "chile", "PE" to "peru", "EC" to "ecuador", "UY" to "uruguay",
        "VE" to "venezuela", "CR" to "costa-rica", "DO" to "dominican-republic",
        "US" to "united-states", "GB" to "united-kingdom", "CA" to "canada",
        "FR" to "france", "DE" to "germany", "IT" to "italy", "PT" to "portugal",
        "BR" to "brazil", "NL" to "netherlands", "BE" to "belgium", "CH" to "switzerland",
        "AT" to "austria", "PL" to "poland", "SE" to "sweden", "NO" to "norway",
        "DK" to "denmark", "FI" to "finland", "IE" to "ireland", "GR" to "greece",
        "TR" to "turkey", "RO" to "romania", "CZ" to "czech-republic", "HU" to "hungary",
        "UA" to "ukraine", "JP" to "japan", "KR" to "south-korea", "IN" to "india",
        "AU" to "australia", "NZ" to "new-zealand", "ZA" to "south-africa",
        "SA" to "saudi-arabia", "AE" to "united-arab-emirates", "EG" to "egypt"
    )

    internal fun sagaKey(title: String): String {
        val base = SAGA_SPLIT_REGEX.split(title.trim()).firstOrNull().orEmpty().trim()
        return base.replace(TRAILING_SEQUEL_REGEX, "").trim().lowercase()
    }

    internal fun cleanNetflixTitle(raw: String): String {
        val unescaped = raw
            .replace("\\u0027", "'")
            .replace("\\u2019", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .trim()
        return unescaped.replace(SEASON_SUFFIX_REGEX, "").trim()
    }

    internal fun parseNetflixTudumHtml(html: String): TudumWeeklyLists {
        val normalized = html.replace("\\\"", "\"")
        data class Entry(val week: String, val category: String, val rank: Int, val title: String)
        val entries = mutableListOf<Entry>()
        for (match in TUDUM_ENTITY_REGEX.findAll(normalized)) {
            val start = match.range.first
            val chunk = normalized.substring(start, (start + 6000).coerceAtMost(normalized.length))
            val meta = TUDUM_META_REGEX.find(chunk) ?: continue
            val top10Idx = chunk.indexOf("\"top10\"")
            if (top10Idx < 0) continue
            val titleMatch = TUDUM_TITLE_REGEX.find(chunk, top10Idx) ?: continue
            val week = meta.groupValues[1]
            val category = meta.groupValues[2]
            val rank = meta.groupValues[3].toIntOrNull() ?: continue
            val title = cleanNetflixTitle(titleMatch.groupValues[1])
            if (title.isNotBlank()) {
                entries += Entry(week, category, rank, title)
            }
        }
        val latestWeek = entries.maxOfOrNull { it.week } ?: return TudumWeeklyLists(emptyList(), emptyList())
        fun extract(cat: String): List<String> = entries
            .asSequence()
            .filter { it.week == latestWeek && it.category.equals(cat, ignoreCase = true) }
            .sortedBy { it.rank }
            .map { it.title }
            .distinctBy { sagaKey(it).ifBlank { it.lowercase() } }
            .take(10)
            .toList()
        return TudumWeeklyLists(movies = extract("MOVIES"), series = extract("SERIES"))
    }

    internal fun normalizePlatform(rawName: String): String {
        val clean = rawName.trim().replace(" Amazon Channel", "", ignoreCase = true)
        val lower = clean.lowercase()
        return when {
            "netflix" in lower -> "Netflix"
            "hbo" in lower || lower == "max" || lower.startsWith("max ") -> "HBO Max"
            "disney" in lower -> "Disney+"
            "prime video" in lower || "amazon prime" in lower -> "Prime Video"
            "apple tv" in lower -> "Apple TV+"
            "skyshowtime" in lower || "sky showtime" in lower -> "SkyShowtime"
            "movistar" in lower -> "Movistar Plus+"
            "paramount" in lower -> "Paramount+"
            "filmin" in lower -> "Filmin"
            "atresplayer" in lower -> "Atresplayer"
            "rtve" in lower -> "RTVE Play"
            "crunchyroll" in lower -> "Crunchyroll"
            else -> clean
        }
    }

    internal fun recencyMultiplier(releaseYear: Int?, isTv: Boolean, currentYear: Int = LocalDate.now().year): Double {
        if (releaseYear == null || releaseYear <= 0) return if (isTv) 1.0 else 0.90
        val age = (currentYear - releaseYear).coerceAtLeast(0)
        return if (isTv) {
            when {
                age <= 2 -> 1.10
                age <= 8 -> 1.00
                else -> 0.55
            }
        } else {
            when {
                age <= 2 -> 1.15
                age <= 5 -> 0.80
                else -> 0.22
            }
        }
    }

    internal fun selectBalancedTop10(
        candidates: List<Top10RankedCandidate>,
        limit: Int = 10,
        maxExclusivePerPlatform: Int = MAX_EXCLUSIVE_PER_PLATFORM
    ): List<Top10RankedCandidate> {
        val sorted = candidates.distinctBy { it.tmdbId }.sortedByDescending { it.score }
        val uniqueSaga = mutableListOf<Top10RankedCandidate>()
        val seenSagas = mutableSetOf<String>()
        for (c in sorted) {
            val saga = sagaKey(c.title).ifBlank { c.tmdbId.toString() }
            if (seenSagas.add(saga)) {
                uniqueSaga += c
            }
        }

        val selected = mutableListOf<Top10RankedCandidate>()
        val deferred = mutableListOf<Top10RankedCandidate>()
        val exclusiveCounts = mutableMapOf<String, Int>()

        for (c in uniqueSaga) {
            if (selected.size >= limit) break
            val exclusivePlatform = c.platforms.singleOrNull()
            if (exclusivePlatform != null) {
                val count = exclusiveCounts[exclusivePlatform] ?: 0
                if (count >= maxExclusivePerPlatform) {
                    deferred += c
                    continue
                }
                exclusiveCounts[exclusivePlatform] = count + 1
            }
            selected += c
        }
        if (selected.size < limit && deferred.isNotEmpty()) {
            for (c in deferred) {
                if (selected.size >= limit) break
                selected += c
            }
        }
        return selected
    }

    internal suspend fun fetchRankedTmdbIds(
        mediaType: String,
        region: String,
        language: String,
        traktClientId: String = "",
        resolveTmdbIdByTitle: suspend (title: String) -> Int? = { null }
    ): List<Int> = withContext(Dispatchers.IO) {
        val isTv = mediaType == "tv"
        val jwType = if (isTv) "SHOW" else "MOVIE"
        val traktKind = if (isTv) "shows" else "movies"
        val lang2 = language.substringBefore('-').substringBefore('_').lowercase().ifBlank { "en" }

        coroutineScope {
            val jwDeferred = async {
                withTimeoutOrNull(TIMEOUT_MS) { fetchJustWatchTrending(region, lang2, jwType) }.orEmpty()
            }
            val traktDeferred = async {
                if (traktClientId.isBlank()) emptyMap()
                else withTimeoutOrNull(TIMEOUT_MS) { fetchTraktTrendingScores(traktKind, traktClientId) }.orEmpty()
            }
            val netflixDeferred = async {
                withTimeoutOrNull(TIMEOUT_MS) { fetchNetflixWeeklyCountry(region) }
            }
            val jwItems = jwDeferred.await()
            val traktScores = traktDeferred.await()
            val tudum = netflixDeferred.await()
            val netflixTitles = if (isTv) tudum?.series.orEmpty() else tudum?.movies.orEmpty()

            if (jwItems.size < 5 && netflixTitles.isEmpty()) return@coroutineScope emptyList()

            val byTmdb = linkedMapOf<Int, Top10RankedCandidate>()
            val sagaToTmdb = mutableMapOf<String, Int>()
            jwItems.forEachIndexed { rank, item ->
                val jwScore = (jwItems.size - rank).toDouble() / jwItems.size.toDouble()
                val traktBoost = traktScores[item.tmdbId] ?: 0.0
                val recency = recencyMultiplier(item.releaseYear, isTv = isTv)
                byTmdb[item.tmdbId] = item.copy(score = (jwScore + traktBoost) * recency)
                val sk = sagaKey(item.title)
                if (sk.isNotBlank()) sagaToTmdb.putIfAbsent(sk, item.tmdbId)
                val origSk = item.originalTitle?.let(::sagaKey).orEmpty()
                if (origSk.isNotBlank()) sagaToTmdb.putIfAbsent(origSk, item.tmdbId)
            }

            // Fuse Netflix weekly country Top 10: boost matching JustWatch entries and resolve up to 5 missing titles
            val unresolvedNetflix = mutableListOf<Pair<Int, String>>()
            netflixTitles.forEachIndexed { rank, title ->
                val nfBoost = NETFLIX_WEIGHT * (10 - rank).coerceAtLeast(1).toDouble() / 10.0
                val sk = sagaKey(title)
                val existingId = sagaToTmdb[sk]
                if (existingId != null) {
                    val existing = byTmdb[existingId]
                    if (existing != null) {
                        val plats = if ("Netflix" in existing.platforms) existing.platforms else existing.platforms + "Netflix"
                        byTmdb[existingId] = existing.copy(score = existing.score + nfBoost, platforms = plats)
                    }
                } else if (unresolvedNetflix.size < 5) {
                    unresolvedNetflix += rank to title
                }
            }

            if (unresolvedNetflix.isNotEmpty()) {
                val permits = Semaphore(3)
                val resolved = unresolvedNetflix.map { (rank, title) ->
                    async {
                        permits.withPermit {
                            val id = runCatching { resolveTmdbIdByTitle(title) }.getOrNull() ?: return@withPermit null
                            Triple(rank, title, id)
                        }
                    }
                }.awaitAll().filterNotNull()
                for ((rank, title, id) in resolved) {
                    val nfBoost = NETFLIX_WEIGHT * (10 - rank).coerceAtLeast(1).toDouble() / 10.0
                    val existing = byTmdb[id]
                    if (existing != null) {
                        val plats = if ("Netflix" in existing.platforms) existing.platforms else existing.platforms + "Netflix"
                        byTmdb[id] = existing.copy(score = existing.score + nfBoost, platforms = plats)
                    } else {
                        byTmdb[id] = Top10RankedCandidate(
                            tmdbId = id,
                            title = title,
                            platforms = listOf("Netflix"),
                            score = nfBoost + (traktScores[id] ?: 0.0)
                        )
                    }
                }
            }

            selectBalancedTop10(byTmdb.values.toList(), limit = 10).map { it.tmdbId }
        }
    }

    private suspend fun fetchNetflixWeeklyCountry(region: String): TudumWeeklyLists? {
        val now = System.currentTimeMillis()
        tudumCache[region]?.takeIf { now - it.fetchedAtMs <= CACHE_TTL_MS }?.let { return it }
        val slug = TUDUM_COUNTRY_SLUGS[region] ?: return null
        val created = CompletableDeferred<TudumWeeklyLists?>()
        val existing = tudumInFlight.putIfAbsent(region, created)
        if (existing != null) {
            return existing.await()
        }
        return try {
            val url = "https://www.netflix.com/tudum/top10/$slug"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Notflix/1.0")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            val parsed = try {
                if (conn.responseCode !in 200..299) {
                    runCatching { conn.errorStream?.close() }
                    null
                } else {
                    val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseNetflixTudumHtml(html).takeIf { it.movies.isNotEmpty() || it.series.isNotEmpty() }
                }
            } finally {
                conn.disconnect()
            }
            if (parsed != null) {
                if (tudumCache.size >= 16) tudumCache.clear()
                tudumCache[region] = parsed
            }
            created.complete(parsed)
            parsed
        } catch (e: CancellationException) {
            created.complete(null)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Netflix Tudum fetch failed ($region): ${e.message}")
            created.complete(null)
            null
        } finally {
            tudumInFlight.remove(region, created)
        }
    }

    private fun fetchJustWatchTrending(region: String, lang: String, objectType: String): List<Top10RankedCandidate> {
        return try {
            val query = """
                query {
                  popularTitles(country: $region, first: 35, sortBy: TRENDING, filter: {objectTypes: [$objectType]}) {
                    edges {
                      node {
                        content(country: $region, language: "$lang") {
                          title
                          originalTitle
                          originalReleaseYear
                          externalIds { tmdbId }
                        }
                        offers(country: $region, platform: WEB) {
                          monetizationType
                          package { clearName }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
            val payload = JSONObject().put("query", query).toString()
            val conn = (URL(JUSTWATCH_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3500
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Notflix-TV/1.0")
            }
            val body = try {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) {
                    runCatching { conn.errorStream?.close() }
                    return emptyList()
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val edges = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("popularTitles")
                ?.optJSONArray("edges") ?: return emptyList()

            val out = mutableListOf<Top10RankedCandidate>()
            for (i in 0 until edges.length()) {
                val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                val content = node.optJSONObject("content") ?: continue
                val title = content.optString("title").takeIf { it.isNotBlank() } ?: continue
                val originalTitle = content.optString("originalTitle").takeIf { it.isNotBlank() }
                val releaseYear = content.optInt("originalReleaseYear", 0).takeIf { it > 0 }
                val tmdbId = content.optJSONObject("externalIds")?.optString("tmdbId")?.toIntOrNull() ?: continue
                val offers = node.optJSONArray("offers") ?: continue
                val plats = linkedSetOf<String>()
                for (j in 0 until offers.length()) {
                    val offer = offers.optJSONObject(j) ?: continue
                    val mType = offer.optString("monetizationType")
                    if (mType == "FLATRATE" || mType == "FREE" || mType == "ADS") {
                        val clearName = offer.optJSONObject("package")?.optString("clearName").orEmpty()
                        if (clearName.isNotBlank()) plats += normalizePlatform(clearName)
                    }
                }
                if (plats.isEmpty()) continue
                out += Top10RankedCandidate(
                    tmdbId = tmdbId,
                    title = title,
                    platforms = plats.toList(),
                    score = 0.0,
                    releaseYear = releaseYear,
                    originalTitle = originalTitle
                )
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "JustWatch trending failed ($region/$objectType): ${e.message}")
            emptyList()
        }
    }

    private fun fetchTraktTrendingScores(kind: String, clientId: String): Map<Int, Double> {
        if (clientId.isBlank()) return emptyMap()
        return try {
            val url = "https://api.trakt.tv/$kind/trending?limit=50"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3500
                setRequestProperty("trakt-api-version", "2")
                setRequestProperty("trakt-api-key", clientId)
                setRequestProperty("User-Agent", "Notflix-TV/1.0")
            }
            val body = try {
                if (conn.responseCode !in 200..299) {
                    runCatching { conn.errorStream?.close() }
                    return emptyMap()
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val arr = JSONArray(body)
            val key = if (kind == "shows") "show" else "movie"
            val total = arr.length().coerceAtLeast(1)
            val map = HashMap<Int, Double>(total)
            for (i in 0 until arr.length()) {
                val tmdbId = arr.optJSONObject(i)
                    ?.optJSONObject(key)
                    ?.optJSONObject("ids")
                    ?.optInt("tmdb", -1)
                    ?.takeIf { it > 0 } ?: continue
                map.putIfAbsent(tmdbId, (total - i).toDouble() / total.toDouble())
            }
            map
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Trakt trending failed ($kind): ${e.message}")
            emptyMap()
        }
    }
}
