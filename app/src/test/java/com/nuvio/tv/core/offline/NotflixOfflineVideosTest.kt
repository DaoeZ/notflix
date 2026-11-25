package com.nuvio.tv.core.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotflixOfflineVideosTest {
    @Test
    fun acceptsKnownVideoContainersFromPathOrFilename() {
        assertEquals("mkv", offlineVideoExtension("/temporary/file", "Movie.MKV"))
        assertEquals("mp4", offlineVideoExtension("/films/final.mp4", null))
        assertNull(offlineVideoExtension("/index.html", null))
    }

    @Test
    fun rejectsAdaptiveManifestsEvenWithAPlayableFileHint() {
        assertNull(offlineVideoExtension("/playlist.m3u8", "Movie.mp4"))
        assertNull(offlineVideoExtension("/file.mp4", "Manifest.MPD"))
    }

    @Test
    fun recordsAreScopedToBothAccountAndProfile() {
        val main = offlineOwnerKey("account", 1)
        assertEquals(main, offlineOwnerKey("account", 1))
        assertNotEquals(main, offlineOwnerKey("account", 2))
        assertNotEquals(main, offlineOwnerKey("other-account", 1))
    }
}
