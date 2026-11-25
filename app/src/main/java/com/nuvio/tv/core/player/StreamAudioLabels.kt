package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.AUDIO_LANGUAGES
import java.util.Locale

private val SUPPORTED_AUDIO_CODES = AUDIO_LANGUAGES.map { it.code }.toSet()
private const val SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"
private const val ASCII_CAPS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

private const val SUBTITLE_LABEL =
    """(?:subs?|subtitles?|[sꜱ][uᴜ][bʙ]s?|subt[ií]tulos?|subtitulad[oa]s?|sous[ -]titres?|untertitel|ondertitels|napisy|napisami|legend(?:as?|ad[oa]s?)|vost(?:fr|f)?)"""
private const val AUDIO_MARK = """(?:(?<!\p{L})(?:audio|ᴀᴜᴅɪᴏ|dub(?:bing|bed)?)\s*[:=]|🔊|🎧|🗣️?)"""
private const val SUBTITLE_MARK = """(?:(?<!\p{L})(?:multi[\s.-]?)?$SUBTITLE_LABEL(?!\p{L})|💬|📜|🔤)"""
private val SUBTITLE_SEGMENT =
    Regex("""(?iu)$SUBTITLE_MARK[^\n|\]\[]*?(?=$|[\n|\]\[]|$AUDIO_MARK)""")
private val BRACKETED_SUBTITLE_CODES =
    Regex("""(?iu)$SUBTITLE_MARK[ \t]*[:=]?(?:[ \t]*\[(?![ \t]*$AUDIO_MARK)[^\[\]\n]*])+""")
private val SUBTITLE_SUFFIX =
    Regex("""(?iu)(?<!\p{L})[\p{L}]{2,3}(?:[._-]$SUBTITLE_LABEL|subs?)(?!\p{L})""")
private val SUBTITLE_LANGUAGE_PREFIX = Regex(
    AUDIO_LANGUAGES.flatMap { listOf(it.englishName, it.nativeName) }
        .distinct().joinToString("|", prefix = """(?iu)(?<!\p{L})(?:pl|""", postfix = ")") { Regex.escape(it) } +
        """(?=[ ._-]+(?:subtitles?|napisy|untertitel|sous[ -]titres?|ondertitels|legendas?|subt[ií]tulos?)(?!\p{L}))"""
)
private val POLISH_SUBTITLE_MARK = Regex("""(?iu)(?<!\p{L})napisypl(?!\p{L})""")
private val SITE_LABEL = Regex("""(?i)(?:https?://|www\.)\S+|(?:🌐|»)[^\n|]*""")
private val CODE_WORD = Regex("""(?<![\p{L}\p{N}])\p{L}{2}(?![\p{L}\p{N}])""")
private val CODE_LIST_SEPARATOR = Regex("""[\s,;/+·&-]+""")
private val BRACKETED_LABEL = Regex("""\[([^\[\]\n]+)]|\(([^()\n]+)\)""")
private val AUDIO_LABEL = Regex("""(?iu)$AUDIO_MARK[^\n|\[\]]*""")
private val MULTI_AUDIO_LABEL =
    Regex("""(?iu)(?<!\p{L})(?:multi|dual)(?!\p{L})(?:[ ._:-]+audio(?!\p{L}))?[ ._:-]+([^\n|\[\]]+)""")
private val TECHNICAL_TAG = Regex("""(?i)(?<!\d)(?:2160[pᴘ]|1080[pᴘ]|720[pᴘ]|576[pᴘ]|480[pᴘ]|4k)(?![\p{L}\p{N}])""")

internal fun withoutStreamSites(text: String): String = SITE_LABEL.replace(text, " ")

internal fun withoutSubtitleLabels(text: String): String {
    val withoutSuffix = SUBTITLE_SUFFIX.replace(text, " ")
    val withoutPrefix = SUBTITLE_LANGUAGE_PREFIX.replace(withoutSuffix, " ")
    val withoutBracketedLabels = BRACKETED_SUBTITLE_CODES.replace(withoutPrefix, " ")
    return SUBTITLE_SEGMENT.replace(POLISH_SUBTITLE_MARK.replace(withoutBracketedLabels, " "), " ")
}

/** Bare two-letter words count only in a recognizable audio label or a video release name. */
internal fun shortStreamAudioCodes(audioText: String, description: String): Set<String> = buildSet {
    fun code(token: String): String? {
        if (token.length != 2) return null
        val normalized = token.lowercase(Locale.ROOT).map { character ->
            when (character) {
                'ғ' -> 'f'
                'ѕ' -> 's'
                else -> SMALL_CAPS.indexOf(character).takeIf { it >= 0 }
                    ?.let { ASCII_CAPS[it].lowercaseChar() } ?: character
            }
        }.joinToString("").let {
            when (it) {
                "iw" -> "he"
                "in" -> "id"
                "nb", "nn" -> "no"
                else -> it
            }
        }
        return normalized.takeIf { it in SUPPORTED_AUDIO_CODES }
    }

    fun addCodeList(label: String, allowSingle: Boolean) {
        val tokens = label.trim().split(CODE_LIST_SEPARATOR)
        if (tokens.size < 2 && !allowSingle) return
        val codes = tokens.map(::code)
        if (codes.all { it != null }) addAll(codes.filterNotNull())
    }

    audioText.lineSequence().forEach { line ->
        val quality = TECHNICAL_TAG.find(line)
        CODE_WORD.findAll(line).forEach { match ->
            val token = match.value
            val styled = token.any { it !in 'a'..'z' && it !in 'A'..'Z' }
            val before = line.getOrNull(match.range.first - 1)
            val after = line.getOrNull(match.range.last + 1)
            val releaseTag = quality != null && (before == '.' || before == '_') &&
                (after == '.' || after == '_' || after == '-' || after == null)
            if (styled || releaseTag) code(token)?.let(::add)
        }
        BRACKETED_LABEL.findAll(line).forEach { match ->
            addCodeList(match.groupValues[1].ifEmpty { match.groupValues[2] }, allowSingle = true)
        }
        AUDIO_LABEL.findAll(line).forEach { match ->
            CODE_WORD.findAll(match.value).forEach { code(it.value)?.let(::add) }
        }
        MULTI_AUDIO_LABEL.findAll(line).forEach { addCodeList(it.groupValues[1], allowSingle = true) }
        if (line.all { !it.isLetter() || it.isUpperCase() }) addCodeList(line, allowSingle = false)
        quality?.let {
            val suffix = line.substring(it.range.last + 1).trim()
            if (suffix.all { !it.isLetter() || it.isUpperCase() }) addCodeList(suffix, allowSingle = true)
        }
    }
    description.lineSequence().filter { line -> line.all { !it.isLetter() || it.isUpperCase() } }
        .forEach { line -> addCodeList(line, allowSingle = true) }
}
