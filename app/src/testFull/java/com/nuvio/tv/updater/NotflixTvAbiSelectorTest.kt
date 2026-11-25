package com.nuvio.tv.updater

import com.nuvio.tv.data.remote.dto.GitHubAssetDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotflixTvAbiSelectorTest {
    @Test
    fun mixedReleaseSelectsTheExistingTvAsset() {
        val assets = listOf(
            asset("Notflix-Mobile-arm64.apk"),
            asset("Notflix-Mobile-arm64-v8a.apk"),
            asset("Notflix-arm64-v8a.apk"),
            asset("Notflix-universal.apk")
        )
        assertEquals("Notflix-arm64-v8a.apk", AbiSelector.chooseNotflixTvApk(assets, listOf("arm64-v8a"))?.name)
        assertEquals("Notflix-universal.apk", AbiSelector.chooseNotflixTvApk(assets, listOf("armeabi-v7a"))?.name)
    }

    @Test
    fun mobileAndIncompatibleTvAssetsAreNeverOffered() {
        val assets = listOf(
            asset("Notflix-Mobile.apk"),
            asset("Notflix.ipa"),
            asset("Notflix-arm64-v8a.apk")
        )
        assertNull(AbiSelector.chooseNotflixTvApk(assets, listOf("armeabi-v7a")))
    }

    private fun asset(name: String) = GitHubAssetDto(name, "https://example.com/$name")
}
