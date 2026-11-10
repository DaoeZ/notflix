package com.nuvio.tv.data.wikipedia

import com.nuvio.tv.domain.model.AppearanceContext
import java.text.Normalizer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Exact public identifier only. A title is never used to guess an identity. */
data class WikipediaIdentity(val imdbId: String, val type: String) {
    companion object {
        fun resolve(contentId: String, imdbId: String?, type: String): WikipediaIdentity? {
            if (type !in setOf("movie", "series")) return null
            val ids = listOfNotNull(contentId, imdbId).filter { WikipediaPolicy.IMDB.matches(it) }.distinct()
            return ids.singleOrNull()?.let { WikipediaIdentity(it, type) }
        }
    }
    fun valid() = WikipediaPolicy.IMDB.matches(imdbId) && type in setOf("movie", "series")
}

data class WikipediaBinding(val context: AppearanceContext, val selectionGeneration: Long, val identity: WikipediaIdentity) {
    fun cacheKey() = "wiki:1:parser${WikipediaPolicy.PARSER_VERSION}:p${context.profileId}:${identity.type}:${identity.imdbId}:es,en"
}

data class WikipediaExtract(
    val imdbId: String,
    val type: String,
    val entityId: String,
    val language: String,
    val articleTitle: String,
    val articleUrl: String,
    val section: String,
    val anchor: String,
    val pageId: Long,
    val revisionId: Long,
    val fetchedAtEpochMs: Long,
    val paragraphs: List<String>,
    val adapted: Boolean = true
) {
    fun revisionUrl(): String? = articleUrl.toHttpUrlOrNull()?.newBuilder()
        ?.encodedPath("/w/index.php")?.query(null)?.addQueryParameter("oldid", revisionId.toString())?.fragment(anchor)?.build()?.toString()

    fun validFor(identity: WikipediaIdentity): Boolean = runCatching {
        identity.valid() && imdbId == identity.imdbId && type == identity.type &&
            Regex("Q[1-9][0-9]{0,15}").matches(entityId) && language in listOf("es", "en") &&
            WikipediaPolicy.safeLabel(articleTitle, 512) && WikipediaPolicy.safeLabel(section, 200) &&
            WikipediaPolicy.safeLabel(anchor, 300) && pageId > 0 && revisionId > 0 && fetchedAtEpochMs > 0 &&
            WikipediaPolicy.article(articleUrl, language)?.second == articleTitle &&
            paragraphs.size in 1..3 && paragraphs.all { WikipediaPolicy.safeLabel(it, 900) && it.length >= 60 } &&
            paragraphs.joinToString("\n\n").length <= 900 && adapted
    }.getOrDefault(false)
}

interface WikipediaExtractRepository {
    val available: Boolean
    suspend fun find(binding: WikipediaBinding, authorized: () -> Boolean): WikipediaExtract?
}

fun interface WikipediaCacheWriter {
    suspend fun write(binding: WikipediaBinding, authorized: () -> Boolean, action: () -> Unit)
}

object WikipediaPolicy {
    const val PARSER_VERSION = 2
    val IMDB = Regex("tt[0-9]{7,12}")
    const val LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
    const val USER_AGENT = "NotflixLab/0.1 (private local evaluation; operator contact not published)"
    const val MAX_RESPONSE_BYTES = 1024 * 1024
    const val MAX_URL_LENGTH = 8192
    val hosts = setOf("query.wikidata.org", "es.wikipedia.org", "en.wikipedia.org")

    fun safeLabel(value: String, max: Int): Boolean = value.isNotBlank() && value.length <= max && value.none {
        it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()
    }

    fun requestAllowed(url: HttpUrl): Boolean = url.toString().length <= MAX_URL_LENGTH &&
        url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        url.fragment == null && url.host in hosts &&
        ((url.host == "query.wikidata.org" && url.encodedPath == "/sparql") ||
            (url.host != "query.wikidata.org" && url.encodedPath == "/w/api.php"))

    /** Returned URLs are decoded as data; only a freshly built fixed API endpoint is requested. */
    fun article(raw: String, language: String): Pair<HttpUrl, String>? {
        if (language !in listOf("es", "en") || raw.length > 2048) return null
        val url = raw.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.host != "$language.wikipedia.org" || url.port != 443 ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null ||
            url.pathSegments.size < 2 || url.pathSegments.first() != "wiki") return null
        val title = url.pathSegments.drop(1).joinToString("/").replace('_', ' ')
        if (!safeLabel(title, 512) || title.contains('\\') || title.startsWith(":") ||
            Regex("^(Special|User|Talk|Template|File|Media|Category|Wikipedia|Especial|Usuario|Discusión|Plantilla|Archivo|Categoría):", RegexOption.IGNORE_CASE).containsMatchIn(title)) return null
        return url to title
    }

    fun normalizedHeading(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ").trim()
}
