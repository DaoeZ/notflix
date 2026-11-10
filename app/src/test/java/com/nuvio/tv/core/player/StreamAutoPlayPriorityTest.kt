package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlayPriorityRule
import com.nuvio.tv.data.local.StreamAutoPlayPriorityConfig
import com.nuvio.tv.data.local.StreamAutoPlayQuality
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            rules = listOf(StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.UHD_2160, spanishOnly = true))
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
