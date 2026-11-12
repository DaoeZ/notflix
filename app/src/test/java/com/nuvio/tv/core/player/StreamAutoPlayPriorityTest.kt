package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.AUDIO_LANGUAGES
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlayPriority
import com.nuvio.tv.data.local.StreamAutoPlayPriorityRule
import com.nuvio.tv.data.local.StreamAutoPlayPriorityConfig
import com.nuvio.tv.data.local.StreamAutoPlayQuality
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAutoPlayPriorityTest {

    @Test
    fun `rules are evaluated in order`() {
        val firstRuleMatch = stream(addonName = "AddonA", name = "1080p")
        val laterRuleMatch = stream(addonName = "AddonB", name = "2160p")

        val selected = select(
            streams = listOf(laterRuleMatch, firstRuleMatch),
            rules = listOf(
                StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.FHD_1080),
                StreamAutoPlayPriorityRule("AddonB", StreamAutoPlayQuality.UHD_2160)
            )
        )

        assertEquals(firstRuleMatch, selected)
    }

    @Test
    fun `falls through to the next addon when the first has no 4k stream`() {
        val addonA1080 = stream(addonName = "AddonA", name = "1080p")
        val addonB1080 = stream(addonName = "AddonB", name = "1080p")

        val selected = select(
            streams = listOf(addonA1080, addonB1080),
            rules = listOf(
                StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.UHD_2160),
                StreamAutoPlayPriorityRule("AddonB", StreamAutoPlayQuality.FHD_1080)
            )
        )

        assertEquals(addonB1080, selected)
    }

    @Test
    fun `empty list keeps manual behavior`() {
        val stream = stream(addonName = "AddonA", name = "2160p")

        assertNull(select(streams = listOf(stream), rules = emptyList()))
    }

    @Test
    fun `other captures streams without 4k or 1080`() {
        val fourK = stream(addonName = "AddonA", name = "2160p")
        val sevenTwenty = stream(addonName = "AddonB", name = "720p")

        val selected = select(
            streams = listOf(fourK, sevenTwenty),
            rules = listOf(StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.OTHER))
        )

        assertEquals(sevenTwenty, selected)
    }

    @Test
    fun `any addon rule takes the first playable match`() {
        val first = stream(addonName = "AddonA", title = "4K HDR")
        val second = stream(addonName = "AddonB", name = "2160p")

        val selected = select(
            streams = listOf(first, second),
            rules = listOf(StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.UHD_2160))
        )

        assertEquals(first, selected)
    }

    @Test
    fun `quality comes from the stream name or title and defaults to other`() {
        assertEquals(StreamAutoPlayQuality.UHD_2160, quality(name = "2160p"))
        assertEquals(StreamAutoPlayQuality.UHD_2160, quality(title = "4K HDR"))
        assertEquals(StreamAutoPlayQuality.FHD_1080, quality(name = "1080p"))
        assertEquals(StreamAutoPlayQuality.FHD_1080, quality(title = "Full HD"))
        assertEquals(StreamAutoPlayQuality.OTHER, quality(name = "720p"))
        assertEquals(StreamAutoPlayQuality.OTHER, quality())
    }

    @Test
    fun `allowed addons filter does not hide streams named by a priority rule`() {
        val addonA4k = stream(addonName = "AddonA", name = "2160p")
        val addonB1080 = stream(addonName = "AddonB", name = "1080p")

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(addonA4k, addonB1080),
            mode = StreamAutoPlayMode.PRIORITY_LIST,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("AddonA", "AddonB"),
            selectedAddons = setOf("AddonB"),
            selectedPlugins = emptySet(),
            priorityList = listOf(StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.UHD_2160))
        )

        assertEquals(addonA4k, selected)
    }

    @Test
    fun `ultra hd counts as 4k`() {
        assertEquals(StreamAutoPlayQuality.UHD_2160, quality(title = "Ultra HD"))
        assertEquals(StreamAutoPlayQuality.UHD_2160, quality(name = "UHD BluRay"))
    }

    @Test
    fun `quality is the outer order and add-on the inner one`() {
        val config = StreamAutoPlayPriorityConfig(
            addons = listOf("AddonA", "AddonB"),
            qualities = listOf(StreamAutoPlayQuality.UHD_2160, StreamAutoPlayQuality.FHD_1080)
        )

        assertEquals(
            listOf(
                StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.UHD_2160),
                StreamAutoPlayPriorityRule("AddonB", StreamAutoPlayQuality.UHD_2160),
                StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.FHD_1080),
                StreamAutoPlayPriorityRule("AddonB", StreamAutoPlayQuality.FHD_1080)
            ),
            config.toRules()
        )
    }

    @Test
    fun `empty add-on list means any add-on and empty quality list means no rules`() {
        val config = StreamAutoPlayPriorityConfig(qualities = listOf(StreamAutoPlayQuality.UHD_2160))

        assertEquals(listOf(StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.UHD_2160)), config.toRules())
        assertEquals(emptyList<StreamAutoPlayPriorityRule>(), StreamAutoPlayPriorityConfig(addons = listOf("A")).toRules())
    }

    @Test
    fun `fourK of a later add-on beats 1080p of the first one`() {
        val addonA1080 = stream(addonName = "AddonA", name = "1080p")
        val addonB4k = stream(addonName = "AddonB", name = "2160p")

        val selected = select(
            streams = listOf(addonA1080, addonB4k),
            rules = StreamAutoPlayPriorityConfig(
                addons = listOf("AddonA", "AddonB"),
                qualities = listOf(StreamAutoPlayQuality.UHD_2160, StreamAutoPlayQuality.FHD_1080)
            ).toRules()
        )

        assertEquals(addonB4k, selected)
    }

    @Test
    fun `cached only skips torrents that would need a download`() {
        val torrent = Stream(
            name = "2160p", title = null, description = null, url = null, ytId = null,
            infoHash = "abc123", fileIdx = null, externalUrl = null, behaviorHints = null,
            addonName = "AddonA", addonLogo = null
        )
        val cached = Stream(
            name = "2160p", title = null, description = null, url = "https://example.invalid/cached.m3u8",
            ytId = null, infoHash = null, fileIdx = null, externalUrl = null, behaviorHints = null,
            addonName = "AddonB", addonLogo = null,
            debridCacheStatus = StreamDebridCacheStatus("rd", "Real-Debrid", StreamDebridCacheState.CACHED)
        )

        val selected = select(
            streams = listOf(torrent, cached),
            rules = listOf(StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.UHD_2160, cachedOnly = true))
        )

        assertEquals(cached, selected)
    }

    @Test
    fun `spanish only skips 4k streams that do not say they are Spanish`() {
        val english4k = stream(addonName = "AddonA", name = "2160p", title = "English")
        val spanish4k = stream(addonName = "AddonB", name = "2160p", title = "[ES] Castellano")

        val selected = select(
            streams = listOf(english4k, spanish4k),
            rules = listOf(StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.UHD_2160, audioLanguage = "es"))
        )

        assertEquals(spanish4k, selected)
    }

    @Test
    fun `spanish subtitles alone are not spanish audio`() {
        assertEquals(false, StreamAutoPlaySelector.isSpanishForPriority(stream("A", "2160p", "Movie 2160p\n🇬🇧 English | Subs: ES, EN")))
        assertEquals(false, StreamAutoPlaySelector.isSpanishForPriority(stream("A", "2160p", "Movie VOSE 2160p")))
        assertEquals(false, StreamAutoPlaySelector.isSpanishForPriority(stream("A", "2160p", "Movie 2160p VOSE ES")))
        assertEquals(true, StreamAutoPlaySelector.isSpanishForPriority(stream("A", "2160p", "Movie 2160p Dual Castellano + VOSE")))
        assertEquals(false, StreamAutoPlaySelector.isSpanishForPriority(stream("A", "2160p", "Movie\n💬 🇪🇸 🇬🇧")))
        assertEquals(true, StreamAutoPlaySelector.isSpanishForPriority(stream("A", "2160p", "Movie\n🇪🇸 Castellano | Subs: EN")))
    }

    @Test
    fun `every offered language matches its own native label`() {
        AUDIO_LANGUAGES.forEach { language ->
            assertTrue(
                "${language.code} no coincide con ${language.nativeName}",
                matches(stream("A", "1080p", "Movie ${language.nativeName} 1080p"), setOf(language.code))
            )
        }
    }

    @Test
    fun `the table covers every app language without ambiguous two-letter codes`() {
        val expected = setOf(
            "ar", "bn", "bs", "bg", "zh", "hr", "cs", "da", "nl", "en", "fr", "de", "el", "he",
            "hi", "hu", "id", "it", "ja", "lt", "no", "pl", "pt", "ro", "ru", "sr", "sk", "sl",
            "es", "sq", "sv", "ta", "tr", "uk", "vi"
        )
        assertEquals(expected, AUDIO_LANGUAGES.map { it.code }.toSet())
        assertEquals(AUDIO_LANGUAGES.size, AUDIO_LANGUAGES.map { it.nativeName }.toSet().size)
        val ambiguous = setOf("it", "de", "en", "no", "in", "da", "es")
        AUDIO_LANGUAGES.forEach { language ->
            val tokens = language.codes + language.names
            assertTrue(
                "${language.code} usa un código ambiguo: $tokens",
                tokens.none { it.lowercase() in ambiguous }
            )
        }
    }

    @Test
    fun `common words and subtitle-only marks never count as audio`() {
        assertFalse(matches(stream("A", "1080p", "It (2017) 1080p"), setOf("it")))
        assertFalse(matches(stream("A", "1080p", "Dual 1080p"), setOf("en")))
        assertFalse(matches(stream("A", "1080p", "Dual 1080p"), setOf("es")))
        assertFalse(matches(stream("A", "1080p", "Movie VOSTFR"), setOf("fr")))
        assertFalse(matches(stream("A", "1080p", "Movie SUB ITA"), setOf("it")))
        assertFalse(matches(stream("A", "1080p", "Movie Legendado"), setOf("pt")))
        assertTrue(matches(stream("A", "1080p", "MULTI VFF"), setOf("fr")))
        assertFalse(matches(stream("A", "1080p", "MULTI VFF"), setOf("it")))
    }

    @Test
    fun `english and spanish selection accepts castilian and unmarked but not french`() {
        val codes = setOf("en", "es")
        assertTrue(matches(stream("A", "1080p", "Movie Castellano"), codes))
        assertTrue(matches(stream("A", "1080p", "Movie 1080p"), codes))
        assertFalse(matches(stream("A", "1080p", "Movie Français"), codes))
    }

    @Test
    fun `an empty selection accepts any language`() {
        assertTrue(matches(stream("A", "1080p", "Movie Français"), emptySet()))
    }

    @Test
    fun `quality first expands quality language and addon in that order`() {
        val rules = StreamAutoPlayPriorityConfig(
            addons = listOf("AddonA", "AddonB"),
            qualities = listOf(
                StreamAutoPlayQuality.UHD_2160,
                StreamAutoPlayQuality.FHD_1080,
                StreamAutoPlayQuality.OTHER
            ),
            audioLanguages = listOf("it", "es", "en"),
            priority = StreamAutoPlayPriority.CALIDAD
        ).toRules()

        assertRuleOrder("""
            UHD_2160/it/AddonA
            UHD_2160/it/AddonB
            UHD_2160/es/AddonA
            UHD_2160/es/AddonB
            UHD_2160/en/AddonA
            UHD_2160/en/AddonB
            FHD_1080/it/AddonA
            FHD_1080/it/AddonB
            FHD_1080/es/AddonA
            FHD_1080/es/AddonB
            FHD_1080/en/AddonA
            FHD_1080/en/AddonB
            OTHER/it/AddonA
            OTHER/it/AddonB
            OTHER/es/AddonA
            OTHER/es/AddonB
            OTHER/en/AddonA
            OTHER/en/AddonB
        """, rules)
    }

    @Test
    fun `language first expands language quality and addon in that order`() {
        val rules = StreamAutoPlayPriorityConfig(
            addons = listOf("AddonA", "AddonB"),
            qualities = listOf(
                StreamAutoPlayQuality.UHD_2160,
                StreamAutoPlayQuality.FHD_1080,
                StreamAutoPlayQuality.OTHER
            ),
            audioLanguages = listOf("it", "es", "en"),
            priority = StreamAutoPlayPriority.IDIOMA
        ).toRules()

        assertRuleOrder("""
            UHD_2160/it/AddonA
            UHD_2160/it/AddonB
            FHD_1080/it/AddonA
            FHD_1080/it/AddonB
            OTHER/it/AddonA
            OTHER/it/AddonB
            UHD_2160/es/AddonA
            UHD_2160/es/AddonB
            FHD_1080/es/AddonA
            FHD_1080/es/AddonB
            OTHER/es/AddonA
            OTHER/es/AddonB
            UHD_2160/en/AddonA
            UHD_2160/en/AddonB
            FHD_1080/en/AddonA
            FHD_1080/en/AddonB
            OTHER/en/AddonA
            OTHER/en/AddonB
        """, rules)
    }

    @Test
    fun `quality first lets a 4k English stream beat a 1080 Italian one`() {
        val english4k = stream("AddonA", "2160p", "English")
        val italian1080 = stream("AddonA", "1080p", "Italiano")
        val rules = StreamAutoPlayPriorityConfig(
            qualities = listOf(StreamAutoPlayQuality.UHD_2160, StreamAutoPlayQuality.FHD_1080),
            audioLanguages = listOf("it", "en"),
            priority = StreamAutoPlayPriority.CALIDAD
        ).toRules()

        assertEquals(english4k, select(listOf(english4k, italian1080), rules))
    }

    @Test
    fun `language first lets a 1080 Italian stream beat a 4k English one`() {
        val english4k = stream("AddonA", "2160p", "English")
        val italian1080 = stream("AddonA", "1080p", "Italiano")
        val rules = StreamAutoPlayPriorityConfig(
            qualities = listOf(StreamAutoPlayQuality.UHD_2160, StreamAutoPlayQuality.FHD_1080),
            audioLanguages = listOf("it", "en"),
            priority = StreamAutoPlayPriority.IDIOMA
        ).toRules()

        assertEquals(italian1080, select(listOf(english4k, italian1080), rules))
    }

    @Test
    fun `without languages quality order is unchanged`() {
        val english4k = stream("AddonA", "2160p", "English")
        val italian1080 = stream("AddonA", "1080p", "Italiano")
        val rules = StreamAutoPlayPriorityConfig(
            qualities = listOf(StreamAutoPlayQuality.UHD_2160, StreamAutoPlayQuality.FHD_1080)
        ).toRules()

        assertTrue(rules.all { it.audioLanguage == null })
        assertEquals(english4k, select(listOf(english4k, italian1080), rules))
        val config = StreamAutoPlayPriorityConfig(
            addons = listOf("AddonA", "AddonB"),
            qualities = StreamAutoPlayPriorityConfig.DEFAULT_QUALITIES
        )
        val qualityRules = config.toRules()
        assertRuleOrder("""
            UHD_2160/null/AddonA
            UHD_2160/null/AddonB
            FHD_1080/null/AddonA
            FHD_1080/null/AddonB
            OTHER/null/AddonA
            OTHER/null/AddonB
        """, qualityRules)
        assertEquals(qualityRules, config.copy(priority = StreamAutoPlayPriority.IDIOMA).toRules())
    }

    @Test
    fun `language aliases do not expand into duplicate rules`() {
        val config = StreamAutoPlayPriorityConfig(
            qualities = listOf(StreamAutoPlayQuality.OTHER),
            audioLanguages = listOf("iw", "he", "en", "he", " ", " en ")
        )
        assertEquals(listOf("he", "en"), config.toRules().map { it.audioLanguage })
    }

    @Test
    fun `many rules classify each source only once`() {
        val streams = listOf(stream("AddonA", "2160p", "English"), stream("AddonB", "1080p", "Italiano"))
        val rules = StreamAutoPlayPriorityConfig(
            addons = listOf("AddonA", "AddonB"),
            qualities = StreamAutoPlayPriorityConfig.DEFAULT_QUALITIES,
            audioLanguages = listOf("fr", "de", "ru"),
            cachedOnly = true
        ).toRules()
        mockkObject(StreamAutoPlaySelector)
        try {
            assertNull(select(streams, rules)) // Exhaust all 18 rules.
            streams.forEach { stream ->
                verify(exactly = 1) { StreamAutoPlaySelector.streamPriorityQuality(stream) }
                verify(exactly = 1) { StreamAutoPlaySelector.classifyPriorityAudio(stream) }
                verify(exactly = 1) { StreamAutoPlaySelector.isCachedForPriority(stream) }
            }
        } finally {
            unmockkObject(StreamAutoPlaySelector)
        }
    }

    @Test
    fun `Portuguese subtitle labels do not become audio`() {
        assertFalse(matches(stream("A", "1080p", "Movie Legendado Português"), setOf("pt")))
        assertFalse(matches(stream("A", "1080p", "Movie Legendado ITA"), setOf("it")))
        assertFalse(matches(stream("A", "1080p", "Movie VOST ITA"), setOf("it")))
    }

    @Test
    fun `explicit dub after subtitles still counts as audio`() {
        assertTrue(matches(stream("A", "1080p", "Movie SUB ITA + DUB ITA"), setOf("it")))
        assertTrue(matches(stream("A", "1080p", "Movie VOSTFR + French Dub"), setOf("fr")))
        assertFalse(matches(stream("A", "1080p", "Movie VOSTFR French"), setOf("fr")))
    }

    @Test
    fun `ISO Hebrew selection recognizes Hebrew audio`() {
        assertTrue(matches(stream("A", "1080p", "Movie HEB"), setOf("he")))
        assertTrue(matches(stream("A", "1080p", "Movie עברית"), setOf("he")))
    }

    @Test
    fun `every offered language rejects its own subtitle-only label`() {
        AUDIO_LANGUAGES.forEach { language ->
            val otherAudio = if (language.code == "fr") "German" else "French"
            assertFalse("${language.code} was accepted from subtitles",
                matches(stream("A", "1080p", "Movie $otherAudio | Subs: ${language.nativeName}"), setOf(language.code)))
        }
    }

    @Test
    fun `non Spanish native names match Unicode uppercase labels`() {
        AUDIO_LANGUAGES.filter { it.code != "es" }.forEach { language ->
            assertTrue("${language.code} uppercase native label did not match",
                matches(stream("A", "1080p", "Movie ${language.nativeName.uppercase(java.util.Locale.ROOT)}"), setOf(language.code)))
        }
    }

    @Test
    fun `debrid download links are not cached even though they are http`() {
        assertEquals(false, StreamAutoPlaySelector.isCachedForPriority(stream("Torrentio", "[RD download] Torrentio\n4k")))
        assertEquals(false, StreamAutoPlaySelector.isCachedForPriority(stream("Comet", "[RD⬇️] Comet 2160p")))
        assertEquals(true, StreamAutoPlaySelector.isCachedForPriority(stream("Torrentio", "[RD+] Torrentio\n4k")))
    }

    @Test
    fun `legacy pairs are kept as they were`() {
        val legacy = listOf(
            StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.UHD_2160),
            StreamAutoPlayPriorityRule("AddonB", StreamAutoPlayQuality.FHD_1080)
        )

        val config = StreamAutoPlayPriorityConfig.fromRules(legacy)

        assertEquals(listOf("AddonA", "AddonB"), config.addons)
        assertEquals(listOf(StreamAutoPlayQuality.UHD_2160, StreamAutoPlayQuality.FHD_1080), config.qualities)
    }

    private fun assertRuleOrder(expected: String, rules: List<StreamAutoPlayPriorityRule>) {
        assertEquals(expected.trimIndent().lines(), rules.map { "${it.quality}/${it.audioLanguage}/${it.addonName}" })
    }

    private fun matches(stream: Stream, codes: Set<String>): Boolean =
        StreamAutoPlaySelector.matchesAudioLanguages(stream, codes)

    private fun select(
        streams: List<Stream>,
        rules: List<StreamAutoPlayPriorityRule>
    ): Stream? = StreamAutoPlaySelector.selectAutoPlayStream(
        streams = streams,
        mode = StreamAutoPlayMode.PRIORITY_LIST,
        regexPattern = "",
        source = StreamAutoPlaySource.ALL_SOURCES,
        installedAddonNames = streams.map { it.addonName }.toSet(),
        selectedAddons = emptySet(),
        selectedPlugins = emptySet(),
        priorityList = rules
    )

    private fun quality(name: String? = null, title: String? = null): StreamAutoPlayQuality =
        StreamAutoPlaySelector.streamPriorityQuality(stream(addonName = "AddonA", name = name, title = title))

    private fun stream(
        addonName: String,
        name: String? = null,
        title: String? = null
    ): Stream = Stream(
        name = name,
        title = title,
        description = null,
        url = "https://example.invalid/$addonName.m3u8",
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = addonName,
        addonLogo = null
    )
}
