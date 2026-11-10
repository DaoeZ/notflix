package com.nuvio.tv.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAutoPlayPriorityStorageTest {
    @Test
    fun `rules survive a json round trip in order`() {
        val rules = listOf(
            StreamAutoPlayPriorityRule("AddonA", StreamAutoPlayQuality.UHD_2160),
            StreamAutoPlayPriorityRule(null, StreamAutoPlayQuality.FHD_1080),
            StreamAutoPlayPriorityRule("AddonB", StreamAutoPlayQuality.OTHER)
        )

        assertEquals(rules, decodeStreamAutoPlayPriorityList(encodeStreamAutoPlayPriorityList(rules)))
    }

    @Test
    fun `empty and invalid storage decode to an empty list`() {
        assertTrue(decodeStreamAutoPlayPriorityList(null).isEmpty())
        assertTrue(decodeStreamAutoPlayPriorityList("").isEmpty())
        assertTrue(decodeStreamAutoPlayPriorityList("not-json").isEmpty())
    }
}
