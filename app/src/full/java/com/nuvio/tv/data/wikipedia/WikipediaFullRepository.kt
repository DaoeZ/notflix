package com.nuvio.tv.data.wikipedia

import com.google.gson.JsonParser
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl

internal class WikipediaFullRepository(
    private val transport: WikipediaTransport,
    private val cache: WikipediaExtractCache,
    private val budget: WikipediaLabBudget,
    private val writer: WikipediaCacheWriter,
    private val parser: WikipediaHtmlParser = WikipediaHtmlParser(),
    override val available: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val metric: (String, Int) -> Unit = { _, _ -> },
    private val operationTimeoutMs: Long = 30_000
) : WikipediaExtractRepository {
    private val serial = Mutex()
    private class Deferred : IOException()
    private data class Articles(val entity: String, val links: Map<String, String>)

    override suspend fun find(binding: WikipediaBinding, authorized: () -> Boolean): WikipediaExtract? = withContext(Dispatchers.IO) {
        if (!available || !binding.identity.valid() || !authorized()) return@withContext null
        // Serial requests are polite to Wikimedia and coalesce concurrent reads through the cache.
        serial.withLock {
            currentCoroutineContext().ensureActive()
            if (!authorized()) return@withLock null
            try {
                var cached: WikipediaExtractCache.Entry? = null
                writer.write(binding, authorized) { cached = cache.read(binding) }
                cached?.let { metric("cache", budget.snapshot()?.requests ?: 0); return@withLock it.result }
                val result = withTimeout(operationTimeoutMs.coerceIn(1, 30_000)) {
                    val resolved = resolve(request(binding, wikidataUrl(binding.identity.imdbId), "identity", authorized))
                    if (resolved == null) metric("no_unique_identity", budget.snapshot()?.requests ?: 0)
                    var found: WikipediaExtract? = null
                    if (resolved != null) for (language in listOf("es", "en")) {
                        val article = resolved.links[language] ?: continue
                        val title = WikipediaPolicy.article(article, language)?.second ?: continue
                        val payload = request(binding, articleUrl(language, title), language, authorized)
                        currentCoroutineContext().ensureActive()
                        if (!authorized()) throw CancellationException("Wikipedia context revoked")
                        found = parser.parse(payload, binding.identity, resolved.entity, language, article, now())
                        if (found != null) break
                    }
                    if (resolved != null) metric(if (found == null) "no_safe_text" else "extract_${found.language}", budget.snapshot()?.requests ?: 0)
                    found
                }
                currentCoroutineContext().ensureActive()
                if (!authorized()) throw CancellationException("Wikipedia context revoked")
                save(binding, result, if (result == null) "absent" else "found", authorized)
                result
            } catch (cancelled: CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) metric("operation_timeout", budget.snapshot()?.requests ?: 0)
                throw cancelled
            } catch (_: Deferred) { null
            } catch (error: Exception) {
                metric("error_${error.javaClass.simpleName}", budget.snapshot()?.requests ?: 0)
                currentCoroutineContext().ensureActive()
                if (authorized()) save(binding, null, "error", authorized)
                null
            }
        }
    }

    private suspend fun save(binding: WikipediaBinding, result: WikipediaExtract?, outcome: String, authorized: () -> Boolean) {
        try {
            writer.write(binding, authorized) { cache.write(binding, result, outcome, authorized) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { /* Optional cache failure never blocks the official detail. */ }
    }

    private suspend fun request(binding: WikipediaBinding, url: HttpUrl, stage: String, authorized: () -> Boolean): String {
        currentCoroutineContext().ensureActive()
        if (!authorized()) throw CancellationException("Wikipedia context revoked")
        check(WikipediaPolicy.requestAllowed(url))
        val reserved = budget.reserve(binding.identity.imdbId) ?: throw Deferred()
        currentCoroutineContext().ensureActive()
        if (!authorized()) throw CancellationException("Wikipedia context revoked")
        metric(stage, reserved.requests)
        val response = transport.get(url)
        metric("http_${response.status}", reserved.requests)
        currentCoroutineContext().ensureActive()
        if (!authorized()) throw CancellationException("Wikipedia context revoked")
        if (response.status == 429 || response.status == 503) {
            budget.deferUntil(retryAt(response.retryAfter, now())); throw Deferred()
        }
        if (response.status != 200) throw IOException("Wikipedia API status ${response.status}")
        val json = JsonParser.parseString(response.body).asJsonObject
        if (json.getAsJsonObject("error")?.get("code")?.asString == "maxlag") {
            budget.deferUntil(retryAt(response.retryAfter, now())); throw Deferred()
        }
        return response.body
    }

    private fun resolve(payload: String): Articles? = runCatching {
        val rows = JsonParser.parseString(payload).asJsonObject.getAsJsonObject("results")?.getAsJsonArray("bindings") ?: return null
        if (rows.size() !in 1..3) return null
        fun value(row: com.google.gson.JsonElement, key: String): String? {
            val field = row.asJsonObject.getAsJsonObject(key) ?: return null
            if (field.get("type")?.asString != "uri") throw IOException("Unexpected identity response")
            return field.get("value")?.asString
        }
        val entities = rows.map { value(it, "item") ?: return null }.distinct()
        if (entities.size != 1 || !Regex("https?://www\\.wikidata\\.org/entity/Q[1-9][0-9]{0,15}").matches(entities.single())) return null
        val links = mutableMapOf<String, String>()
        for (lang in listOf("es", "en")) {
            val urls = rows.mapNotNull { value(it, lang) }.distinct()
            if (urls.size > 1) return null
            urls.singleOrNull()?.let { url ->
                if (WikipediaPolicy.article(url, lang) == null) return null
                links[lang] = url
            }
        }
        Articles(entities.single().substringAfterLast('/'), links)
    }.getOrNull()

    companion object {
        internal fun wikidataUrl(imdb: String): HttpUrl {
            require(WikipediaPolicy.IMDB.matches(imdb))
            val query = """PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX schema: <http://schema.org/>
                SELECT ?item ?es ?en WHERE {
                  { SELECT DISTINCT ?item WHERE { ?item wdt:P345 "$imdb" } LIMIT 2 }
                  OPTIONAL { ?es schema:about ?item; schema:isPartOf <https://es.wikipedia.org/> }
                  OPTIONAL { ?en schema:about ?item; schema:isPartOf <https://en.wikipedia.org/> }
                } LIMIT 3""".trimIndent()
            return HttpUrl.Builder().scheme("https").host("query.wikidata.org").addPathSegment("sparql")
                .addQueryParameter("format", "json").addQueryParameter("query", query).build()
        }
        internal fun articleUrl(language: String, title: String): HttpUrl {
            require(language in listOf("es", "en") && WikipediaPolicy.safeLabel(title, 512))
            return HttpUrl.Builder().scheme("https").host("$language.wikipedia.org").encodedPath("/w/api.php")
                .addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
                .addQueryParameter("action", "parse").addQueryParameter("prop", "tocdata|text|revid")
                .addQueryParameter("disableeditsection", "1").addQueryParameter("disablelimitreport", "1")
                .addQueryParameter("maxlag", "5").addQueryParameter("page", title).build()
        }
        internal fun retryAt(header: String?, time: Long): Long {
            val seconds = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.coerceAtLeast(60)
            if (seconds != null) return if (seconds > (Long.MAX_VALUE - time) / 1000) Long.MAX_VALUE else time + seconds * 1000
            val date = runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            val fallback = if (time > Long.MAX_VALUE - 60_000) Long.MAX_VALUE else time + 60_000
            return maxOf(fallback, date ?: 0)
        }
    }
}
