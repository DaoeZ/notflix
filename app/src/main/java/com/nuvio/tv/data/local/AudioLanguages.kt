package com.nuvio.tv.data.local

/**
 * M59: one row of the audio-language table. The same entry feeds the settings list (names) and the
 * selector (codes, dub marks and flags). Tokens are matched as whole words, so "It (2017)" never
 * matches Italian and "de" is never used for German.
 *
 * [dubMarks] only matter when the source also shows an [originalVersionMarkers] marker (VOSTFR...):
 * a subtitle-only language label must not pass as audio, while an explicit dub code still does.
 */
internal data class AudioLanguageEntry(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val codes: List<String>,
    val names: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
    val dubMarks: List<String> = emptyList(),
    val originalVersionMarkers: List<String> = emptyList()
) {
    private val markPattern = tokenPattern(codes + englishName + nativeName + names)
    private val dubPattern = tokenPattern(dubMarks)
    private val originalPattern = tokenPattern(originalVersionMarkers)
    private val explicitDubPattern = explicitDubPattern(codes + englishName + nativeName + names)

    fun matches(text: String, raw: String): Boolean {
        // A separately named dub remains audio even on a line that also lists subtitles.
        if (explicitDubPattern?.containsMatchIn(raw) == true) return true
        if (originalPattern?.containsMatchIn(raw) == true) {
            return dubPattern?.containsMatchIn(text) == true
        }
        if (markPattern?.containsMatchIn(text) == true) return true
        return flags.any { text.contains(it) }
    }
}

internal fun audioLanguagesFor(codes: Set<String>): List<AudioLanguageEntry> =
    AUDIO_LANGUAGES.filter { it.code in codes }

/** Android's historical Hebrew locale alias is not the ISO 639-1 code stored by this feature. */
internal fun normalizeAudioLanguageCodes(codes: Set<String>): Set<String> =
    codes.map { if (it == "iw") "he" else it }.toSet()

private fun explicitDubPattern(tokens: List<String>): Regex? {
    val languages = tokens.filter { it.isNotBlank() }.distinct().joinToString("|") { Regex.escape(it) }
    if (languages.isEmpty()) return null
    val dub = "(?:dub(?:bed)?|dublad[oa]|doubl[eé]|audio)"
    val separator = "\\s*[:=.-]?\\s*"
    return Regex("(?iu)(?<!\\p{L})(?:$dub$separator(?:$languages)|(?:$languages)$separator$dub)(?!\\p{L})")
}

private fun tokenPattern(tokens: List<String>): Regex? {
    val cleaned = tokens.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (cleaned.isEmpty()) return null
    val alternatives = cleaned.joinToString("|") { Regex.escape(it) }
    return Regex("(?iu)(?<!\\p{L})(?:$alternatives)(?!\\p{L})")
}

/**
 * One entry per app language: `values-*` merges Portuguese (BR/PT), Chinese (CN/TW) and Spanish
 * (ES/419). Order is by English name; the settings list shows [AudioLanguageEntry.nativeName].
 */
internal val AUDIO_LANGUAGES: List<AudioLanguageEntry> = listOf(
    AudioLanguageEntry(
        code = "ar", englishName = "Arabic", nativeName = "العربية",
        codes = listOf("ara"), flags = listOf("🇸🇦")
    ),
    AudioLanguageEntry(
        code = "bn", englishName = "Bengali", nativeName = "বাংলা",
        codes = listOf("ben")
    ),
    AudioLanguageEntry(
        code = "bs", englishName = "Bosnian", nativeName = "Bosanski",
        codes = listOf("bos"), flags = listOf("🇧🇦")
    ),
    AudioLanguageEntry(
        code = "bg", englishName = "Bulgarian", nativeName = "Български",
        codes = listOf("bul"), flags = listOf("🇧🇬")
    ),
    AudioLanguageEntry(
        code = "zh", englishName = "Chinese", nativeName = "中文",
        codes = listOf("zho", "chi"), names = listOf("普通话", "國語"),
        flags = listOf("🇨🇳", "🇹🇼")
    ),
    AudioLanguageEntry(
        code = "hr", englishName = "Croatian", nativeName = "Hrvatski",
        codes = listOf("hrv"), flags = listOf("🇭🇷")
    ),
    AudioLanguageEntry(
        code = "cs", englishName = "Czech", nativeName = "Čeština",
        codes = listOf("cze", "ces"), flags = listOf("🇨🇿")
    ),
    AudioLanguageEntry(
        code = "da", englishName = "Danish", nativeName = "Dansk",
        codes = listOf("dan"), flags = listOf("🇩🇰")
    ),
    AudioLanguageEntry(
        code = "nl", englishName = "Dutch", nativeName = "Nederlands",
        codes = listOf("dut", "nld"), flags = listOf("🇳🇱")
    ),
    AudioLanguageEntry(
        code = "en", englishName = "English", nativeName = "English",
        codes = listOf("eng"), flags = listOf("🇬🇧", "🇺🇸")
    ),
    AudioLanguageEntry(
        code = "fr", englishName = "French", nativeName = "Français",
        codes = listOf("fre", "fra"),
        names = listOf("vf", "vff", "vfq", "truefrench"),
        flags = listOf("🇫🇷"),
        dubMarks = listOf("vf", "vff", "vfq", "truefrench"),
        originalVersionMarkers = listOf("vostfr", "vostf", "vo stfr")
    ),
    AudioLanguageEntry(
        code = "de", englishName = "German", nativeName = "Deutsch",
        codes = listOf("ger", "deu"), flags = listOf("🇩🇪")
    ),
    AudioLanguageEntry(
        code = "el", englishName = "Greek", nativeName = "Ελληνικά",
        codes = listOf("ell", "gre"), flags = listOf("🇬🇷")
    ),
    AudioLanguageEntry(
        code = "he", englishName = "Hebrew", nativeName = "עברית",
        codes = listOf("heb"), flags = listOf("🇮🇱")
    ),
    AudioLanguageEntry(
        code = "hi", englishName = "Hindi", nativeName = "हिन्दी",
        codes = listOf("hin")
    ),
    AudioLanguageEntry(
        code = "hu", englishName = "Hungarian", nativeName = "Magyar",
        codes = listOf("hun"), flags = listOf("🇭🇺")
    ),
    AudioLanguageEntry(
        code = "id", englishName = "Indonesian", nativeName = "Bahasa Indonesia",
        codes = listOf("ind"), flags = listOf("🇮🇩")
    ),
    AudioLanguageEntry(
        code = "it", englishName = "Italian", nativeName = "Italiano",
        codes = listOf("ita"), flags = listOf("🇮🇹")
    ),
    AudioLanguageEntry(
        code = "ja", englishName = "Japanese", nativeName = "日本語",
        codes = listOf("jpn"), flags = listOf("🇯🇵")
    ),
    AudioLanguageEntry(
        code = "lt", englishName = "Lithuanian", nativeName = "Lietuvių",
        codes = listOf("lit"), flags = listOf("🇱🇹")
    ),
    AudioLanguageEntry(
        code = "no", englishName = "Norwegian", nativeName = "Norsk",
        codes = listOf("nor"), flags = listOf("🇳🇴")
    ),
    AudioLanguageEntry(
        code = "pl", englishName = "Polish", nativeName = "Polski",
        codes = listOf("pol"), flags = listOf("🇵🇱")
    ),
    AudioLanguageEntry(
        code = "pt", englishName = "Portuguese", nativeName = "Português",
        codes = listOf("por"),
        names = listOf("dublado", "dublada", "pt-br", "pt-pt"),
        flags = listOf("🇧🇷", "🇵🇹")
    ),
    AudioLanguageEntry(
        code = "ro", englishName = "Romanian", nativeName = "Română",
        codes = listOf("ron", "rum"), flags = listOf("🇷🇴")
    ),
    AudioLanguageEntry(
        code = "ru", englishName = "Russian", nativeName = "Русский",
        codes = listOf("rus"), flags = listOf("🇷🇺")
    ),
    AudioLanguageEntry(
        code = "sr", englishName = "Serbian", nativeName = "Srpski",
        codes = listOf("srp"), flags = listOf("🇷🇸")
    ),
    AudioLanguageEntry(
        code = "sk", englishName = "Slovak", nativeName = "Slovenčina",
        codes = listOf("slo", "slk"), flags = listOf("🇸🇰")
    ),
    AudioLanguageEntry(
        code = "sl", englishName = "Slovenian", nativeName = "Slovenščina",
        codes = listOf("slv"), flags = listOf("🇸🇮")
    ),
    AudioLanguageEntry(
        code = "es", englishName = "Spanish", nativeName = "Español",
        codes = listOf("spa", "esp"),
        names = listOf("castellano", "latino", "lat", "spanish"),
        flags = listOf("🇪🇸", "🇲🇽", "🇦🇷", "🇨🇱", "🇨🇴", "🇵🇪", "🇻🇪", "🇺🇾"),
        dubMarks = listOf("castellano", "español", "latino", "spanish"),
        originalVersionMarkers = listOf("vose", "v.o.s.e.")
    ),
    AudioLanguageEntry(
        code = "sq", englishName = "Albanian", nativeName = "Shqip",
        codes = listOf("alb", "sqi"), flags = listOf("🇦🇱")
    ),
    AudioLanguageEntry(
        code = "sv", englishName = "Swedish", nativeName = "Svenska",
        codes = listOf("swe"), flags = listOf("🇸🇪")
    ),
    AudioLanguageEntry(
        code = "ta", englishName = "Tamil", nativeName = "தமிழ்",
        codes = listOf("tam")
    ),
    AudioLanguageEntry(
        code = "tr", englishName = "Turkish", nativeName = "Türkçe",
        codes = listOf("tur"), flags = listOf("🇹🇷")
    ),
    AudioLanguageEntry(
        code = "uk", englishName = "Ukrainian", nativeName = "Українська",
        codes = listOf("ukr"), flags = listOf("🇺🇦")
    ),
    AudioLanguageEntry(
        code = "vi", englishName = "Vietnamese", nativeName = "Tiếng Việt",
        codes = listOf("vie"), flags = listOf("🇻🇳")
    )
)
