package com.nuvio.tv.core.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.HiddenTitleChanges
import com.nuvio.tv.data.local.HiddenTitlesStore
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.local.NotflixIntegrationStore
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.data.local.ProfileAgeLimitStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real DataStores on two isolated "devices"; no network (signed out, so nothing is pushed). */
class NotflixSettingsSyncServiceTest {
    @Test
    fun selectorReadsMobileKidsPresetAndFailsClosedForInvalidLimits() {
        fun age(json: String) = selectorAgeLimit(Json.parseToJsonElement(json).jsonObject)
        assertEquals(AgeLevel.TP, age("""{"level":{"type":"string","value":"TP"}}"""))
        assertEquals(AgeLevel.A12, age("""{"level":{"type":"string","value":"A12"}}"""))
        assertEquals(AgeLevel.TP, age("""{"level":{"type":"int","value":12}}"""))
        assertEquals(AgeLevel.TP, age("""{"level":null}"""))
        assertEquals(null, age("{}"))
    }

    private val dirs = mutableListOf<File>()
    private val factories = mutableListOf<ProfileDataStoreFactory>()

    private val keysByFactory = mutableMapOf<ProfileDataStoreFactory, MutableStateFlow<NotflixIntegrationCredentials>>()

    /** In-memory stand-in for the Keystore-backed keys store. */
    private fun keysStore(keys: MutableStateFlow<NotflixIntegrationCredentials>): NotflixIntegrationStore {
        val store = mockk<NotflixIntegrationStore>()
        every { store.read() } answers { keys.value }
        every { store.values } returns keys
        coEvery { store.save(any(), any()) } answers { keys.value = firstArg<NotflixIntegrationCredentials>().copy(updatedAt = secondArg()) }
        return store
    }

    private fun device(): Pair<ProfileDataStoreFactory, NotflixSettingsSyncService> {
        val dir = Files.createTempDirectory("nf-sync-").toFile().also { dirs += it }
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns dir
        every { context.applicationContext } returns context
        val factory = ProfileDataStoreFactory(context).also { factories += it }
        val auth = mockk<AuthManager>(relaxed = true)
        every { auth.isAuthenticated } returns false
        val profiles = mockk<ProfileManager>(relaxed = true)
        every { profiles.activeProfileId } returns MutableStateFlow(1)
        val keys = MutableStateFlow(NotflixIntegrationCredentials()).also { keysByFactory[factory] = it }
        return factory to NotflixSettingsSyncService(auth, mockk(relaxed = true), profiles, factory, mockk(relaxed = true), keysStore(keys))
            .also { services += it }
    }

    private val services = mutableListOf<NotflixSettingsSyncService>()

    @After fun tearDown() = runBlocking {
        // Stop each service's own watcher first: left running, it reopens a DataStore on a deleted
        // test directory and the "multiple DataStores" error surfaces in a later, unrelated test.
        services.forEach { service ->
            (NotflixSettingsSyncService::class.java.getDeclaredField("scope").apply { isAccessible = true }
                .get(service) as kotlinx.coroutines.CoroutineScope).coroutineContext[Job]?.cancelAndJoin()
        }
        factories.forEach { factory ->
            val cache = ProfileDataStoreFactory::class.java.getDeclaredField("cache")
                .apply { isAccessible = true }.get(factory) as MutableMap<*, *>
            cache.values.toList().forEach { scoped ->
                (scoped!!::class.java.getDeclaredField("job").apply { isAccessible = true }.get(scoped) as Job).cancel()
            }
            cache.clear()
        }
        dirs.forEach { it.deleteRecursively() }
    }

    @Test fun settingsTravelToAnotherDeviceButTvOnlyMotionStaysLocal() = runBlocking {
        val (tvA, syncA) = device()
        tvA.get(1, HiddenTitlesStore.FEATURE).edit { it[stringSetPreferencesKey("hidden_keys")] = setOf("movie:tt1", "series:tt2") }
        tvA.get(1, ProfileAgeLimitStore.FEATURE).edit { it[stringPreferencesKey("level")] = "TEEN" }
        tvA.get(1, NotflixAppearanceStore.FEATURE).edit {
            it[stringPreferencesKey("appearance")] = "notflix"
            it[booleanPreferencesKey(NotflixAppearanceStore.UI_SOUNDS_ENABLED)] = false
            it[booleanPreferencesKey(NotflixAppearanceStore.WIKIPEDIA_ENABLED)] = false
            it[booleanPreferencesKey(NotflixAppearanceStore.REDUCE_HOME_MOTION)] = true
        }

        val blob = syncA.exportFeatures(1)
        val appearance = blob[NotflixAppearanceStore.FEATURE]!!.jsonObject
        assertFalse(NotflixAppearanceStore.REDUCE_HOME_MOTION in appearance)
        assertFalse(NotflixAppearanceStore.WIKIPEDIA_ENABLED in appearance)

        val (tvB, syncB) = device()
        tvB.get(1, NotflixAppearanceStore.FEATURE).edit { it[booleanPreferencesKey(NotflixAppearanceStore.REDUCE_HOME_MOTION)] = false }
        syncB.importFeatures(1, blob)

        assertEquals(setOf("movie:tt1", "series:tt2"), tvB.get(1, HiddenTitlesStore.FEATURE).data.first()[stringSetPreferencesKey("hidden_keys")])
        assertEquals("TEEN", tvB.get(1, ProfileAgeLimitStore.FEATURE).data.first()[stringPreferencesKey("level")])
        val b = tvB.get(1, NotflixAppearanceStore.FEATURE).data.first()
        assertEquals("notflix", b[stringPreferencesKey("appearance")])
        assertEquals(false, b[booleanPreferencesKey(NotflixAppearanceStore.UI_SOUNDS_ENABLED)])
        assertEquals(null, b[booleanPreferencesKey(NotflixAppearanceStore.WIKIPEDIA_ENABLED)]) // "only this device"
        assertEquals(false, b[booleanPreferencesKey(NotflixAppearanceStore.REDUCE_HOME_MOTION)]) // B's own value kept
        val back = syncB.exportFeatures(1)
        for (f in listOf(ProfileAgeLimitStore.FEATURE, NotflixAppearanceStore.FEATURE)) assertEquals(blob[f], back[f])
    }

    @Test fun unhidingOnOneDeviceRemovesTheTitleOnTheOther() = runBlocking {
        val (tvA, syncA) = device()
        val (tvB, syncB) = device()
        val storeA = HiddenTitlesStore(tvA)
        HiddenTitlesStore(tvB).hide(1, "movie:tt1")
        syncA.importFeatures(1, syncB.exportFeatures(1)) // A learns about the hide
        Thread.sleep(5)
        storeA.show(1, "movie:tt1")
        syncB.importFeatures(1, syncA.exportFeatures(1))
        assertTrue(tvB.get(1, HiddenTitlesStore.FEATURE).data.first()[stringSetPreferencesKey("hidden_keys")].orEmpty().isEmpty())
    }

    @Test fun crossedChangesOnTwoTvsAreBothKept() = runBlocking {
        val (tvA, syncA) = device()
        val (tvB, syncB) = device()
        val storeA = HiddenTitlesStore(tvA)
        val storeB = HiddenTitlesStore(tvB)
        storeA.hide(1, "movie:old")
        syncB.importFeatures(1, syncA.exportFeatures(1)) // both TVs start with "old" hidden
        Thread.sleep(5)
        storeA.hide(1, "movie:new", "New", null)          // A hides a title
        storeB.show(1, "movie:old")                       // meanwhile B unhides another
        val fromA = syncA.exportFeatures(1)
        syncA.importFeatures(1, syncB.exportFeatures(1))
        syncB.importFeatures(1, fromA)
        val expected = setOf("movie:new")
        assertEquals(expected, storeA.observe(1).first())
        assertEquals(expected, storeB.observe(1).first())
        assertEquals("New", storeB.observeMetadata(1).first()["movie:new"]?.title)
    }

    @Test fun titlesHiddenBeforeDatesExistAreKeptFromBothSides() {
        val merged = HiddenTitleChanges.merge(setOf("movie:a"), null, null, setOf("movie:b"), null, null)
        assertEquals(setOf("movie:a", "movie:b"), merged.keys)
    }

    @Test fun oldShowsAreForgottenButHidesAreNot() {
        val now = 10L * HiddenTitleChanges.SHOW_RETENTION_MS
        val old = now - HiddenTitleChanges.SHOW_RETENTION_MS - 1
        val changes = """{"movie:shown":{"h":false,"t":$old},"movie:hidden":{"h":true,"t":$old}}"""
        val merged = HiddenTitleChanges.merge(setOf("movie:hidden"), null, changes, emptySet(), null, null, now)
        assertEquals(setOf("movie:hidden"), merged.keys)
        assertFalse("movie:shown" in HiddenTitleChanges.decode(merged.changesJson))
    }
    @Test fun newestKeysWinAndAnOlderCopyNeverOverwrites() = runBlocking {
        val (tvA, syncA) = device()
        val (tvB, syncB) = device()
        keysByFactory.getValue(tvA).value = NotflixIntegrationCredentials(tmdbApiKey = "key-a", traktClientId = "id-a", updatedAt = 100)
        syncB.importFeatures(1, syncA.exportFeatures(1))
        assertEquals("key-a", keysByFactory.getValue(tvB).value.tmdbApiKey)
        assertEquals(100L, keysByFactory.getValue(tvB).value.updatedAt)

        keysByFactory.getValue(tvB).value = NotflixIntegrationCredentials(tmdbApiKey = "key-b", updatedAt = 200)
        syncB.importFeatures(1, syncA.exportFeatures(1)) // A's copy is older
        assertEquals("key-b", keysByFactory.getValue(tvB).value.tmdbApiKey)
    }

    @Test fun neverSavedKeysDoNotWipeAnotherDevicesKeys() = runBlocking {
        val (_, syncA) = device()
        val (tvB, syncB) = device()
        keysByFactory.getValue(tvB).value = NotflixIntegrationCredentials(tmdbApiKey = "key-b", updatedAt = 200)
        syncB.importFeatures(1, syncA.exportFeatures(1))
        assertEquals("key-b", keysByFactory.getValue(tvB).value.tmdbApiKey)
    }

    @Test fun newerTraktTokenWinsAndAnUnfinishedSignInStaysLocal() = runBlocking {
        val (tvA, syncA) = device()
        val (tvB, syncB) = device()
        tvA.get(1, NotflixSettingsSyncService.TRAKT_FEATURE).edit {
            it[stringPreferencesKey("access_token")] = "new-access"
            it[stringPreferencesKey("refresh_token")] = "new-refresh"
            it[longPreferencesKey("created_at")] = 2_000
            it[stringPreferencesKey("device_code")] = "a-code"
        }
        tvB.get(1, NotflixSettingsSyncService.TRAKT_FEATURE).edit {
            it[stringPreferencesKey("access_token")] = "old-access"
            it[longPreferencesKey("created_at")] = 1_000
            it[stringPreferencesKey("device_code")] = "b-code"
        }
        syncB.importFeatures(1, syncA.exportFeatures(1))
        val b = tvB.get(1, NotflixSettingsSyncService.TRAKT_FEATURE).data.first()
        assertEquals("new-access", b[stringPreferencesKey("access_token")])
        assertEquals("new-refresh", b[stringPreferencesKey("refresh_token")])
        assertEquals("b-code", b[stringPreferencesKey("device_code")])

        syncA.importFeatures(1, syncB.exportFeatures(1)) // same token back: nothing changes
        assertEquals("new-access", tvA.get(1, NotflixSettingsSyncService.TRAKT_FEATURE).data.first()[stringPreferencesKey("access_token")])
    }

    @Test fun aCopyWithoutTraktTokenDoesNotSignThisTvOut() = runBlocking {
        val (_, syncA) = device()
        val (tvB, syncB) = device()
        tvB.get(1, NotflixSettingsSyncService.TRAKT_FEATURE).edit {
            it[stringPreferencesKey("access_token")] = "b-access"
            it[longPreferencesKey("created_at")] = 1_000
        }
        syncB.importFeatures(1, syncA.exportFeatures(1))
        assertEquals("b-access", tvB.get(1, NotflixSettingsSyncService.TRAKT_FEATURE).data.first()[stringPreferencesKey("access_token")])
    }
    @Test fun playOrderAndButtonsTravelButAudioAndEngineStayOnTheDevice() = runBlocking {
        val (tvA, syncA) = device()
        val (tvB, syncB) = device()
        val player = NotflixSettingsSyncService.PLAYER_FEATURE
        tvA.get(1, player).edit {
            it[stringPreferencesKey("stream_auto_play_priority_list")] = "4k,1080p"
            it[booleanPreferencesKey("stream_reuse_last_link_enabled")] = true
            it[stringPreferencesKey("notflix_player_buttons")] = "next,subtitles"
            it[floatPreferencesKey("next_episode_threshold_percent_v2")] = 95f
            it[stringPreferencesKey("audio_output_channels")] = "a-only"
        }
        tvB.get(1, player).edit {
            it[stringPreferencesKey("audio_output_channels")] = "b-only"
            it[stringPreferencesKey("stream_auto_play_regex")] = "old"
        }
        val blob = syncA.exportFeatures(1)
        assertFalse("audio_output_channels" in blob[player]!!.jsonObject)
        syncB.importFeatures(1, blob)
        val b = tvB.get(1, player).data.first()
        assertEquals("4k,1080p", b[stringPreferencesKey("stream_auto_play_priority_list")])
        assertEquals(true, b[booleanPreferencesKey("stream_reuse_last_link_enabled")])
        assertEquals("next,subtitles", b[stringPreferencesKey("notflix_player_buttons")])
        assertEquals(95f, b[floatPreferencesKey("next_episode_threshold_percent_v2")])
        assertEquals("b-only", b[stringPreferencesKey("audio_output_channels")])
        assertEquals(null, b[stringPreferencesKey("stream_auto_play_regex")]) // A cleared it, so B follows
    }
}
