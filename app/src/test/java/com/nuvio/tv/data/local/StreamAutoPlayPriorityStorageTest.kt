package com.nuvio.tv.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAutoPlayPriorityStorageTest {
    @Test
    fun `rules survive a json round trip in order`() {
        val rules = listOf(
            StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.UHD_2160, audioLanguage = "it"),
            StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.FHD_1080),
            StreamAutoPlayPriorityRule("AddonB", StreamAutoPlayQuality.OTHER, audioLanguage = "es")
        )

        assertEquals(rules, decodeStreamAutoPlayPriorityList(encodeStreamAutoPlayPriorityList(rules)))
    }

    @Test
    fun `empty and invalid storage decode to an empty list`() {
        assertTrue(decodeStreamAutoPlayPriorityList(null).isEmpty())
        assertTrue(decodeStreamAutoPlayPriorityList("").isEmpty())
        assertTrue(decodeStreamAutoPlayPriorityList("not-json").isEmpty())
    }

    @Test
    fun `old spanish only json becomes the es language`() {
        val legacyRules =
            """[{"addonName":"AddonA","quality":"UHD_2160","cachedOnly":false,"spanishOnly":true}]"""
        val rule = decodeStreamAutoPlayPriorityList(legacyRules).single()
        assertEquals("es", rule.audioLanguage)
        assertFalse(rule.cachedOnly)

        val legacyConfig =
            """{"addons":["AddonA"],"qualities":["UHD_2160"],"cachedOnly":false,"spanishOnly":true}"""
        val config = decodeStreamAutoPlayPriorityConfig(legacyConfig)!!
        assertEquals(listOf("es"), config.audioLanguages)
        assertEquals(StreamAutoPlayPriority.CALIDAD, config.priority)
    }

    @Test
    fun `audio languages round trip in the user order and the old key is no longer written`() {
        val config = StreamAutoPlayPriorityConfig(
            addons = listOf("AddonA", "AddonB"),
            qualities = listOf(StreamAutoPlayQuality.UHD_2160, StreamAutoPlayQuality.FHD_1080),
            cachedOnly = true,
            audioLanguages = listOf("es", "en", "fr"),
            priority = StreamAutoPlayPriority.IDIOMA
        )
        val rawConfig = encodeStreamAutoPlayPriorityConfig(config)
        assertFalse(rawConfig.contains("spanishOnly"))
        val decodedConfig = decodeStreamAutoPlayPriorityConfig(rawConfig)!!
        assertEquals(listOf("es", "en", "fr"), decodedConfig.audioLanguages)
        assertEquals(StreamAutoPlayPriority.IDIOMA, decodedConfig.priority)

        val rules = config.toRules()
        val rawRules = encodeStreamAutoPlayPriorityList(rules)
        assertFalse(rawRules.contains("spanishOnly"))
        assertEquals(rules, decodeStreamAutoPlayPriorityList(rawRules))
    }

    @Test
    fun `explicit new empty selection wins over the legacy spanish flag`() {
        val config = """{"audioLanguages":[],"spanishOnly":true}"""
        val rules = """[{"audioLanguages":[],"spanishOnly":true}]"""
        assertEquals(emptyList<String>(), decodeStreamAutoPlayPriorityConfig(config)!!.audioLanguages)
        assertNull(decodeStreamAutoPlayPriorityList(rules).single().audioLanguage)
    }

    @Test
    fun `hebrew uses ISO he and accepts the legacy Android alias`() {
        val config = """{"audioLanguages":["iw","he"]}"""
        val rules = """[{"audioLanguages":["iw"]}]"""
        assertEquals(listOf("he"), decodeStreamAutoPlayPriorityConfig(config)!!.audioLanguages)
        assertEquals("he", decodeStreamAutoPlayPriorityList(rules).single().audioLanguage)
    }

    @Test
    fun `M59 set is ordered by the app language order and missing priority is quality`() {
        val m59Config = """{"audioLanguages":["es","en","fr"],"cachedOnly":false}"""
        val config = decodeStreamAutoPlayPriorityConfig(m59Config)!!
        assertEquals(listOf("en", "fr", "es"), config.audioLanguages)
        assertEquals(StreamAutoPlayPriority.CALIDAD, config.priority)
    }

    @Test
    fun `an explicit priority survives and keeps the new list order`() {
        val json =
            """{"audioLanguages":["es","en","fr"],"priority":"IDIOMA"}"""
        val config = decodeStreamAutoPlayPriorityConfig(json)!!
        assertEquals(listOf("es", "en", "fr"), config.audioLanguages)
        assertEquals(StreamAutoPlayPriority.IDIOMA, config.priority)
    }

    @Test
    fun `list only M59 storage keeps every language and each rule position`() {
        val json = """[{"addonName":"A","quality":"FHD_1080","cachedOnly":true,"audioLanguages":["es","it","en","es"]},
            {"addonName":"B","quality":"OTHER","audioLanguages":[]}]"""
        val rules = decodeStreamAutoPlayPriorityList(json)
        assertEquals(listOf(
            StreamAutoPlayPriorityRule("A", StreamAutoPlayQuality.FHD_1080, true, "en"),
            StreamAutoPlayPriorityRule("A", StreamAutoPlayQuality.FHD_1080, true, "it"),
            StreamAutoPlayPriorityRule("A", StreamAutoPlayQuality.FHD_1080, true, "es"),
            StreamAutoPlayPriorityRule("B", StreamAutoPlayQuality.OTHER)
        ), rules)
        assertEquals(listOf("en", "it", "es"), StreamAutoPlayPriorityConfig.fromRules(rules).audioLanguages)
    }

    @Test
    fun `explicit singleton null wins over old language fields`() {
        val json = """[{"audioLanguage":null,"audioLanguages":["es"],"spanishOnly":true}]"""
        assertNull(decodeStreamAutoPlayPriorityList(json).single().audioLanguage)
    }

    @Test
    fun `legacy spanish flag migrates even with explicit priority`() {
        val config = decodeStreamAutoPlayPriorityConfig("""{"spanishOnly":true,"priority":"IDIOMA"}""")!!
        assertEquals(listOf("es"), config.audioLanguages)
        assertEquals(StreamAutoPlayPriority.IDIOMA, config.priority)
    }
}
