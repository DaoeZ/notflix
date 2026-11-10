package com.nuvio.tv.data.wikipedia

import com.google.gson.JsonParser
import java.text.BreakIterator
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Offline DOM parsing only. The original third-party quotes are discarded as whole paragraphs. */
internal class WikipediaHtmlParser {
    private val excludedTags = setOf("script", "style", "table", "figure", "blockquote", "q", "cite", "iframe", "svg", "math", "form", "noscript", "template", "aside")
    private val excludedClasses = setOf("reference", "mw-ref", "noprint", "hatnote", "navbox", "infobox", "metadata", "reflist", "mw-references-wrap", "quotebox", "mw-empty-elt", "error")
    private val quote = Regex("[\"“”«»„‟]")
    private val singleQuotation = Regex("[‘'][^’'\\n]{20,}[’']")
    private val thirdPartyConsensus = Regex("rotten\\s+tomatoes|metacritic|tomatometer|critics['’]?\\s+consensus|consenso\\s+(de\\s+los\\s+)?cr[ií]ticos", RegexOption.IGNORE_CASE)
    private val anaphora = Regex("^(however|nevertheless|similarly|moreover|he|she|they|it|this|that|these|those|the latter|sin embargo|no obstante|por su parte|además|asimismo|él|ella|ellos|estas?|estos?|su|sus)\\b", RegexOption.IGNORE_CASE)
    // Reception may mix reviews with commercial results and awards even without subheadings.
    // Omit the whole paragraph rather than presenting those facts as critical opinion.
    private val nonCriticalResults = Regex("taquilla|recaud|presupuesto|box[ -]office|gross(?:ed|ing)?\\b|revenue|budget|profits?\\b|millions? of dollars|millones? de d[oó]lares|premios?\\b|nominaci[oó]n|nominad[oa]|awards?\\b|nominat|[oó]scars?\\b|palma de oro|oso de oro|globos? de oro|golden globes?", RegexOption.IGNORE_CASE)
    private val criticalContext = Regex("cr[ií]tic|reseñ|elogi|aclam|opini[oó]n|reviews?\\b|acclaim|prais", RegexOption.IGNORE_CASE)
    private data class Section(val line: String, val anchor: String, val heading: Element, val rank: Int)

    fun parse(payload: String, identity: WikipediaIdentity, entityId: String, language: String,
        articleUrl: String, fetchedAt: Long): WikipediaExtract? = runCatching {
        val expectedTitle = WikipediaPolicy.article(articleUrl, language)?.second ?: return null
        val response = JsonParser.parseString(payload).asJsonObject
        if (response.has("error")) return null
        val parsed = response.getAsJsonObject("parse") ?: return null
        val title = parsed.get("title")?.asString ?: return null
        if (title.replace('_', ' ') != expectedTitle) return null
        fun positiveNumber(key: String): Long? = parsed.get(key)?.asString
            ?.takeIf { Regex("[1-9][0-9]{0,17}").matches(it) }?.toLongOrNull()
        val revision = positiveNumber("revid") ?: return null
        val pageId = positiveNumber("pageid") ?: return null
        val html = parsed.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (html.toByteArray(Charsets.UTF_8).size > WikipediaPolicy.MAX_RESPONSE_BYTES) return null
        val sections = parsed.getAsJsonObject("tocdata")?.getAsJsonArray("sections") ?: return null
        if (sections.size() > 256) return null
        val document = Jsoup.parse(html)
        val choices = sections.mapNotNull { raw ->
            val s = raw.asJsonObject
            val line = Jsoup.parse(s.get("line")?.asString.orEmpty()).text()
            val anchor = s.get("anchor")?.asString.orEmpty()
            val from = s.get("fromTitle")?.takeUnless { it.isJsonNull }?.asString
            if (from != null && from.replace('_', ' ') != expectedTitle) return@mapNotNull null
            if (!WikipediaPolicy.safeLabel(line, 200) || !WikipediaPolicy.safeLabel(anchor, 300)) return@mapNotNull null
            val rank = rank(line, language) ?: return@mapNotNull null
            val target = document.getElementById(anchor) ?: return@mapNotNull null
            val heading = (listOf(target) + target.parents()).firstOrNull { it.tagName().matches(Regex("h[1-6]")) } ?: return@mapNotNull null
            val headingText = heading.clone().apply { select(".mw-editsection").remove() }.text()
            if (WikipediaPolicy.normalizedHeading(headingText) != WikipediaPolicy.normalizedHeading(line)) return@mapNotNull null
            Section(line, anchor, heading, rank)
        }.sortedBy { it.rank }
        for (section in choices) {
            val paragraphs = extractParagraphs(document.getAllElements().toList(), section.heading, language, section.rank > 0)
            if (paragraphs.isNotEmpty()) {
                val result = WikipediaExtract(identity.imdbId, identity.type, entityId, language, title, articleUrl,
                    section.line, section.anchor, pageId, revision, fetchedAt, paragraphs)
                if (result.validFor(identity)) return result
            }
        }
        null
    }.getOrNull()

    private fun rank(line: String, language: String): Int? {
        val n = WikipediaPolicy.normalizedHeading(line)
        return when (language) {
            "es" -> when (n) {
                "recepcion critica", "recepcion y critica", "critica", "criticas", "acogida critica" -> 0
                "recepcion" -> 1
                else -> null
            }
            "en" -> when (n) {
                "critical response", "critical reception", "critical reaction", "critical assessment" -> 0
                "reception", "criticism" -> 1
                else -> null
            }
            else -> null
        }
    }

    private fun extractParagraphs(elements: List<Element>, heading: Element, language: String, requireCriticalContext: Boolean): List<String> {
        val result = mutableListOf<String>(); var length = 0; var skipped = false
        val start = elements.indexOf(heading)
        if (start < 0) return emptyList()
        for (p in elements.drop(start + 1)) {
            // Stop even at a subsection: do not merge box-office/awards prose into criticism.
            if (p.tagName().matches(Regex("h[1-6]"))) break
            if (p.tagName() != "p") continue
            val ancestors = listOf(p) + p.parents()
            val unsafe = ancestors.any { it.tagName() in excludedTags || it.classNames().any(excludedClasses::contains) ||
                it.hasAttr("hidden") || it.attr("aria-hidden").equals("true", true) ||
                Regex("display\\s*:\\s*none|visibility\\s*:\\s*hidden", RegexOption.IGNORE_CASE).containsMatchIn(it.attr("style")) } ||
                p.select("q,cite,blockquote,script,style,table,figure,iframe,svg,math,form").isNotEmpty()
            val text = p.clone().apply { select("sup,.reference,.mw-ref,.noprint").remove() }.text().replace(Regex("\\s+"), " ").trim()
            if (unsafe || text.length < 60 || !WikipediaPolicy.safeLabel(text, 16_384) || quote.containsMatchIn(text) || singleQuotation.containsMatchIn(text) ||
                thirdPartyConsensus.containsMatchIn(text) || nonCriticalResults.containsMatchIn(text) ||
                (requireCriticalContext && !criticalContext.containsMatchIn(text)) ||
                (skipped && anaphora.containsMatchIn(text))) { skipped = true; continue }
            val separator = if (result.isEmpty()) 0 else 2
            val available = 900 - length - separator
            val clipped = completeSentences(text, available, language)
            if (clipped.length < 60) { if (available < 60) break; skipped = true; continue }
            result += clipped; length += clipped.length + separator; skipped = false
            if (result.size == 3 || clipped.length < text.length) break
        }
        return result
    }

    private fun completeSentences(text: String, limit: Int, language: String): String {
        val iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag(language)); iterator.setText(text)
        var end = iterator.first(); var valid = 0
        while (end != BreakIterator.DONE && end <= limit) {
            if (end > 0 && text.substring(0, end).trimEnd().lastOrNull() in listOf('.', '!', '?', '…')) valid = end
            end = iterator.next()
        }
        return text.substring(0, valid).trim()
    }
}
