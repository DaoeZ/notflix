package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.NotflixCardOpening
import com.nuvio.tv.domain.model.NotflixCardSize
import com.nuvio.tv.ui.appearance.AppearancePolicy
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import com.nuvio.tv.core.auth.AccountLocalDataResetService
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.core.sync.androidtv.AndroidTvChannelManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real Preferences DataStore/files/serializer; only the Android Context is replaced. */
class NotflixAppearanceStoreTest {
    private lateinit var dir: File
    private lateinit var context: Context
    private val factories = mutableListOf<ProfileDataStoreFactory>()

    @Before fun setUp() {
        dir = Files.createTempDirectory("notflix-appearance-").toFile()
        context = mockk(relaxed = true)
        every { context.filesDir } returns dir
        every { context.applicationContext } returns context
    }

    @After fun tearDown() = runBlocking {
        factories.forEach { close(it) }
        dir.deleteRecursively()
        Unit
    }

    private fun factory() = ProfileDataStoreFactory(context).also { factories += it }

    @Test fun `home motion is optional strict boolean and unknown schema never enables Notflix`() = runBlocking {
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        val context = capability(store)
        val disk = factory.get(1, NotflixAppearanceStore.FEATURE)
        assertFalse(store.observe(context).first().reduceHomeMotion)
        disk.edit {
            it[stringPreferencesKey("appearance")] = "notflix"
            it[intPreferencesKey("schema_version")] = 1
            it[stringPreferencesKey(NotflixAppearanceStore.REDUCE_HOME_MOTION)] = "true"
        }
        assertEquals(AppAppearance.NOTFLIX, store.observe(context).first().requested)
        assertFalse(store.observe(context).first().reduceHomeMotion)
        disk.edit { it[booleanPreferencesKey(NotflixAppearanceStore.REDUCE_HOME_MOTION)] = true }
        assertTrue(store.observe(context).first().reduceHomeMotion)
        disk.edit { it[intPreferencesKey("schema_version")] = 99 }
        assertEquals(AppAppearance.NUVIO, store.observe(context).first().requested)
        assertFalse(store.observe(context).first().reduceHomeMotion)
        store.selectHomeMotion(context, false).getOrThrow()
        val after = disk.data.first()
        assertEquals(99, after[intPreferencesKey("schema_version")])
        assertEquals("notflix", after[stringPreferencesKey("appearance")])
        assertEquals(AppAppearance.NUVIO, store.observe(context).first().requested)
    }

    @Test fun `motion and appearance writes preserve each other and official feature bytes`() = runBlocking {
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        val capability = capability(store)
        val official = factory.get(1, "theme_settings")
        official.edit { it[stringPreferencesKey("sentinel")] = "official-current" }
        val officialFile = File(dir, "datastore/theme_settings.preferences_pb")
        val before = officialFile.readBytes()
        store.selectHomeMotion(capability, true).getOrThrow()
        val own = factory.get(1, NotflixAppearanceStore.FEATURE)
        assertNull(own.data.first()[stringPreferencesKey("appearance")])
        assertNull(own.data.first()[intPreferencesKey("schema_version")])
        store.select(capability, AppAppearance.NOTFLIX).getOrThrow()
        assertTrue(store.observe(capability).first().reduceHomeMotion)
        store.selectHomeMotion(capability, false).getOrThrow()
        assertEquals(AppAppearance.NOTFLIX, store.observe(capability).first().requested)
        store.selectHomeMotion(capability, true).getOrThrow()
        store.select(capability, AppAppearance.NUVIO).getOrThrow()
        assertTrue(store.observe(capability).first().reduceHomeMotion)
        assertArrayEquals(before, officialFile.readBytes())
    }

    @Test fun `home motion persists per profile and installation through real reopen`() = runBlocking {
        val first = factory()
        val store = NotflixAppearanceStore(first)
        store.selectHomeMotion(capability(store, 2), true).getOrThrow()
        assertFalse(store.observe(capability(store, 1)).first().reduceHomeMotion)
        close(first)
        val reopened = NotflixAppearanceStore(factory())
        assertTrue(reopened.observe(capability(reopened, 2)).first().reduceHomeMotion)
        assertFalse(reopened.observe(capability(reopened, 1)).first().reduceHomeMotion)
        val otherContext = mockk<Context>(relaxed = true)
        val otherDir = File(dir, "other-motion-installation").apply { mkdirs() }
        every { otherContext.filesDir } returns otherDir
        every { otherContext.applicationContext } returns otherContext
        val other = NotflixAppearanceStore(ProfileDataStoreFactory(otherContext).also { factories += it })
        assertFalse(other.observe(capability(other, 2)).first().reduceHomeMotion)
    }

    @Test fun `card opening defaults to Normal and only the three approved waits are trusted`() = runBlocking {
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        val context = capability(store)
        val disk = factory.get(1, NotflixAppearanceStore.FEATURE)
        assertEquals(NotflixCardOpening.NORMAL, store.observe(context).first().cardOpening)
        store.selectCardOpening(context, NotflixCardOpening.SLOW).getOrThrow()
        assertEquals(NotflixCardOpening.SLOW, store.observe(context).first().cardOpening)
        assertEquals(2000, disk.data.first()[intPreferencesKey(NotflixAppearanceStore.CARD_OPENING_MS)])
        // Corrupt values fail safe without being silently rewritten and without poisoning the rest.
        disk.edit { it[intPreferencesKey(NotflixAppearanceStore.CARD_OPENING_MS)] = 777 }
        assertEquals(NotflixCardOpening.NORMAL, store.observe(context).first().cardOpening)
        assertEquals(777, disk.data.first()[intPreferencesKey(NotflixAppearanceStore.CARD_OPENING_MS)])
        disk.edit { it[stringPreferencesKey(NotflixAppearanceStore.CARD_OPENING_MS)] = "2000" }
        assertEquals(NotflixCardOpening.NORMAL, store.observe(context).first().cardOpening)
        disk.edit {
            it[intPreferencesKey(NotflixAppearanceStore.CARD_OPENING_MS)] = 150
            it[intPreferencesKey("schema_version")] = 99
        }
        assertEquals(NotflixCardOpening.NORMAL, store.observe(context).first().cardOpening)
    }

    @Test fun `card opening persists per profile and installation through real reopen`() = runBlocking {
        val first = factory()
        val store = NotflixAppearanceStore(first)
        store.selectCardOpening(capability(store, 2), NotflixCardOpening.INSTANT).getOrThrow()
        assertEquals(NotflixCardOpening.NORMAL, store.observe(capability(store, 1)).first().cardOpening)
        close(first)
        val reopened = NotflixAppearanceStore(factory())
        assertEquals(NotflixCardOpening.INSTANT, reopened.observe(capability(reopened, 2)).first().cardOpening)
        assertEquals(NotflixCardOpening.NORMAL, reopened.observe(capability(reopened, 1)).first().cardOpening)
        val otherContext = mockk<Context>(relaxed = true)
        val otherDir = File(dir, "other-opening-installation").apply { mkdirs() }
        every { otherContext.filesDir } returns otherDir
        every { otherContext.applicationContext } returns otherContext
        val other = NotflixAppearanceStore(ProfileDataStoreFactory(otherContext).also { factories += it })
        assertEquals(NotflixCardOpening.NORMAL, other.observe(capability(other, 2)).first().cardOpening)
    }

    @Test fun `card size defaults to Large and only the three approved sizes are trusted`() = runBlocking {
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        val context = capability(store)
        val disk = factory.get(1, NotflixAppearanceStore.FEATURE)
        assertEquals(NotflixCardSize.LARGE, store.observe(context).first().cardSize)
        store.selectCardSize(context, NotflixCardSize.SMALL).getOrThrow()
        assertEquals(NotflixCardSize.SMALL, store.observe(context).first().cardSize)
        assertEquals(75, disk.data.first()[intPreferencesKey(NotflixAppearanceStore.CARD_SIZE_PERCENT)])
        // Corrupt values fail safe without being silently rewritten and without poisoning the rest.
        disk.edit { it[intPreferencesKey(NotflixAppearanceStore.CARD_SIZE_PERCENT)] = 42 }
        assertEquals(NotflixCardSize.LARGE, store.observe(context).first().cardSize)
        assertEquals(42, disk.data.first()[intPreferencesKey(NotflixAppearanceStore.CARD_SIZE_PERCENT)])
        disk.edit { it[stringPreferencesKey(NotflixAppearanceStore.CARD_SIZE_PERCENT)] = "75" }
        assertEquals(NotflixCardSize.LARGE, store.observe(context).first().cardSize)
        disk.edit {
            it[intPreferencesKey(NotflixAppearanceStore.CARD_SIZE_PERCENT)] = 87
            it[intPreferencesKey("schema_version")] = 99
        }
        assertEquals(NotflixCardSize.LARGE, store.observe(context).first().cardSize)
    }

    @Test fun `card size persists per profile and installation through real reopen`() = runBlocking {
        val first = factory()
        val store = NotflixAppearanceStore(first)
        store.selectCardSize(capability(store, 2), NotflixCardSize.SMALL).getOrThrow()
        assertEquals(NotflixCardSize.LARGE, store.observe(capability(store, 1)).first().cardSize)
        close(first)
        val reopened = NotflixAppearanceStore(factory())
        assertEquals(NotflixCardSize.SMALL, reopened.observe(capability(reopened, 2)).first().cardSize)
        assertEquals(NotflixCardSize.LARGE, reopened.observe(capability(reopened, 1)).first().cardSize)
        val otherContext = mockk<Context>(relaxed = true)
        val otherDir = File(dir, "other-size-installation").apply { mkdirs() }
        every { otherContext.filesDir } returns otherDir
        every { otherContext.applicationContext } returns otherContext
        val other = NotflixAppearanceStore(ProfileDataStoreFactory(otherContext).also { factories += it })
        assertEquals(NotflixCardSize.LARGE, other.observe(capability(other, 2)).first().cardSize)
    }

    @Test fun `card size commit rechecks authorization and keeps the previous size on failure`() = runBlocking {
        withTimeout(10_000) {
            val factory = factory()
            val store = NotflixAppearanceStore(factory)
            val context = capability(store, 2)
            store.selectCardSize(context, NotflixCardSize.LARGE).getOrThrow()
            val disk = factory.get(2, NotflixAppearanceStore.FEATURE)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val allowed = kotlinx.coroutines.flow.MutableStateFlow(true)
            val blocker = async { disk.edit { entered.complete(Unit); release.await() } }
            entered.await()
            val save = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                store.selectCardSize(context, NotflixCardSize.SMALL) { allowed.value }
            }
            assertFalse(save.isCompleted)
            allowed.value = false
            release.complete(Unit)
            blocker.await()
            assertTrue(save.await().isFailure)
            assertEquals(100, disk.data.first()[intPreferencesKey(NotflixAppearanceStore.CARD_SIZE_PERCENT)])
            assertEquals(NotflixCardSize.LARGE, store.observe(context).first().cardSize)
        }
    }

    @Test fun `card opening commit rechecks authorization and keeps the previous wait on failure`() = runBlocking {
        withTimeout(10_000) {
            val factory = factory()
            val store = NotflixAppearanceStore(factory)
            val context = capability(store, 2)
            store.selectCardOpening(context, NotflixCardOpening.NORMAL).getOrThrow()
            val disk = factory.get(2, NotflixAppearanceStore.FEATURE)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val allowed = kotlinx.coroutines.flow.MutableStateFlow(true)
            val blocker = async { disk.edit { entered.complete(Unit); release.await() } }
            entered.await()
            val save = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                store.selectCardOpening(context, NotflixCardOpening.SLOW) { allowed.value }
            }
            assertFalse(save.isCompleted)
            allowed.value = false
            release.complete(Unit)
            blocker.await()
            assertTrue(save.await().isFailure)
            assertEquals(1200, disk.data.first()[intPreferencesKey(NotflixAppearanceStore.CARD_OPENING_MS)])
            assertEquals(NotflixCardOpening.NORMAL, store.observe(context).first().cardOpening)
        }
    }

    @Test fun `motion commit rechecks authorization after real DataStore queue wait`() = runBlocking {
        withTimeout(10_000) {
            val factory = factory()
            val store = NotflixAppearanceStore(factory)
            val context = capability(store, 2)
            val disk = factory.get(2, NotflixAppearanceStore.FEATURE)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val allowed = kotlinx.coroutines.flow.MutableStateFlow(true)
            val blocker = async {
                disk.edit { entered.complete(Unit); release.await() }
            }
            entered.await()
            val save = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                store.selectHomeMotion(context, true) { allowed.value }
            }
            assertFalse(save.isCompleted)
            allowed.value = false
            release.complete(Unit)
            blocker.await()
            assertTrue(save.await().isFailure)
            assertNull(disk.data.first()[booleanPreferencesKey(NotflixAppearanceStore.REDUCE_HOME_MOTION)])
        }
    }

    @Test fun `retired motion capability cannot write and reused profile starts Normal`() = runBlocking {
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        val old = capability(store, 2)
        store.selectHomeMotion(old, true).getOrThrow()
        factory.clearProfile(2)
        assertTrue(store.selectHomeMotion(old, true).isFailure)
        factory.markProfileCreated(2)
        factory.prepareAppearanceProfile(2)
        val fresh = capability(store, 2)
        assertNotEquals(old, fresh)
        assertFalse(store.observe(fresh).first().reduceHomeMotion)
    }

    @Test fun `motion storage failure is reported without writing appearance`() = runBlocking {
        requireNotNull(file(2).parentFile).mkdirs()
        file(2).mkdirs()
        File(file(2), "occupied").writeText("fixture")
        val store = NotflixAppearanceStore(factory())
        assertTrue(store.selectHomeMotion(capability(store, 2), true).isFailure)
        val read = store.observe(capability(store, 2)).first()
        assertTrue(read.failed)
        assertFalse(read.reduceHomeMotion)
        assertEquals(AppAppearance.NUVIO, read.requested)
    }
    private fun file(id: Int, suffix: String = ".preferences_pb") = File(dir,
        "datastore/${NotflixAppearanceStore.FEATURE}${if (id == 1) "" else "_p$id"}$suffix")
    private fun capability(store: NotflixAppearanceStore, id: Int = 1) =
        requireNotNull(store.contextFor(id, true, setOf(1, 2)))

    private suspend fun close(factory: ProfileDataStoreFactory) {
        val cache = ProfileDataStoreFactory::class.java.getDeclaredField("cache")
            .apply { isAccessible = true }.get(factory) as MutableMap<*, *>
        cache.values.toList().forEach { scoped ->
            val job = scoped!!::class.java.getDeclaredField("job").apply { isAccessible = true }.get(scoped) as Job
            job.cancelAndJoin()
        }
        cache.clear()
    }

    @Test fun `invalid values and closed gate safely resolve to Nuvio`() {
        listOf("", "modern", 1, true).forEach { assertEquals(AppAppearance.NUVIO, AppAppearance.decode(it, 1)) }
        assertEquals(AppAppearance.NOTFLIX, AppAppearance.decode(null, 1))
        listOf(2, "1", true, 1L).forEach { assertEquals(AppAppearance.NUVIO, AppAppearance.decode("notflix", it)) }
        assertEquals(AppAppearance.NOTFLIX, AppAppearance.decode("notflix", 1))
        assertEquals(AppAppearance.NUVIO, AppearancePolicy.effective(AppAppearance.NOTFLIX, available = false))
        assertEquals(AppAppearance.NOTFLIX, AppearancePolicy.effective(AppAppearance.NOTFLIX, available = true))
    }

    @Test fun `unconfirmed missing and invalid profiles cannot open the feature`() {
        val store = NotflixAppearanceStore(factory())
        assertNull(store.contextFor(1, false, setOf(1)))
        assertNull(store.contextFor(2, true, setOf(1)))
        assertNull(store.contextFor(7, true, setOf(7)))
        assertFalse(file(1).exists())
    }

    @Test fun `choices persist independently after closing and reopening actual disk stores`() = runBlocking {
        val first = factory()
        val store = NotflixAppearanceStore(first)
        assertEquals(AppAppearance.NOTFLIX, store.observe(capability(store)).first().requested)
        store.select(capability(store), AppAppearance.NOTFLIX).getOrThrow()
        assertEquals(AppAppearance.NOTFLIX, store.observe(capability(store, 2)).first().requested)
        close(first)
        val reopened = NotflixAppearanceStore(factory())
        assertEquals(AppAppearance.NOTFLIX, reopened.observe(capability(reopened)).first().requested)
        assertEquals(AppAppearance.NOTFLIX, reopened.observe(capability(reopened, 2)).first().requested)
        assertTrue(file(1).length() > 0)
    }

    @Test fun `logical invalid value is not silently rewritten`() = runBlocking {
        val factory = factory()
        factory.get(1, NotflixAppearanceStore.FEATURE).edit { it[stringPreferencesKey("appearance")] = "future" }
        val store = NotflixAppearanceStore(factory)
        assertEquals(AppAppearance.NUVIO, store.observe(capability(store)).first().requested)
        assertEquals("future", factory.get(1, NotflixAppearanceStore.FEATURE).data.first()[stringPreferencesKey("appearance")])
    }

    @Test fun `corruption recovers only its own valid backup`() = runBlocking {
        val first = factory()
        val store = NotflixAppearanceStore(first)
        store.select(capability(store), AppAppearance.NOTFLIX).getOrThrow()
        close(first)
        file(1).copyTo(file(1, ".preferences_pb.bak"), overwrite = true)
        file(1).writeBytes(byteArrayOf(-1, -1, -1))
        val reopened = NotflixAppearanceStore(factory())
        assertEquals(AppAppearance.NOTFLIX, reopened.observe(capability(reopened)).first().requested)
        assertEquals(AppAppearance.NOTFLIX, reopened.observe(capability(reopened, 2)).first().requested)
    }

    @Test fun `corruption without backup falls back and failed IO reports failure`() = runBlocking {
        requireNotNull(file(1).parentFile).mkdirs()
        file(1).writeBytes(byteArrayOf(-1, -1, -1))
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        assertEquals(AppAppearance.NOTFLIX, store.observe(capability(store)).first().requested)
        // A non-file target forces a real storage error, without OS permission assumptions.
        file(2).mkdirs()
        File(file(2), "child").writeText("occupied")
        assertTrue(store.observe(capability(store, 2)).first().failed)
        assertTrue(store.select(capability(store, 2), AppAppearance.NOTFLIX).isFailure)
    }

    @Test fun `retirement drains writes and rejects stale callers and reopen until prepared`() = runBlocking {
        withTimeout(10_000) {
            val factory = factory()
            val store = NotflixAppearanceStore(factory)
            val old = capability(store, 2)
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val write = async {
                factory.withAppearanceContext(old) {
                    entered.complete(Unit)
                    finish.await()
                    factory.get(2, NotflixAppearanceStore.FEATURE).edit { it[stringPreferencesKey("appearance")] = "notflix" }
                }
            }
            entered.await()
            val retire = async { factory.clearProfile(2) }
            kotlinx.coroutines.yield()
            assertFalse(retire.isCompleted)
            finish.complete(Unit)
            write.await()
            retire.await()
            assertFalse(file(2).exists())
            assertFalse(file(2, ".preferences_pb.bak").exists())
            assertNull(store.contextFor(2, true, setOf(2)))
            assertTrue(store.select(old, AppAppearance.NOTFLIX).isFailure)
            assertTrue(runCatching { factory.get(2, NotflixAppearanceStore.FEATURE) }.isFailure)
            factory.markProfileCreated(2)
            factory.prepareAppearanceProfile(2)
            val fresh = capability(store, 2)
            assertNotEquals(old, fresh)
            assertEquals(AppAppearance.NOTFLIX, store.observe(fresh).first().requested)
            assertTrue(store.select(old, AppAppearance.NOTFLIX).isFailure)
        }
    }

    @Test fun `account reset clears uncached backup and profile1 but leaves unrelated standalone data`() = runBlocking {
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        store.select(capability(store), AppAppearance.NOTFLIX).getOrThrow()
        file(2, ".preferences_pb.bak").writeText("uncached old account")
        val other = File(dir, "datastore/device_local_player_prefs.preferences_pb").apply { writeText("keep") }
        factory.beginAppearanceAccountReset()
        assertNull(store.contextFor(1, true, setOf(1)))
        factory.finishAppearanceAccountReset()
        assertFalse(file(2, ".preferences_pb.bak").exists())
        assertEquals("keep", other.readText())
        close(factory)
        val reopened = NotflixAppearanceStore(factory())
        assertEquals(AppAppearance.NOTFLIX, reopened.observe(capability(reopened)).first().requested)
    }

    @Test fun `failed deletion stays quarantined across reopen and can recover after repair`() = runBlocking {
        file(1).mkdirs()
        val child = File(file(1), "occupied").apply { writeText("x") }
        val first = factory()
        first.beginAppearanceAccountReset()
        first.finishAppearanceAccountReset()
        assertNull(NotflixAppearanceStore(first).contextFor(1, true, setOf(1)))
        val second = factory()
        val store = NotflixAppearanceStore(second)
        assertNull(store.contextFor(1, true, setOf(1)))
        assertTrue(child.delete())
        second.prepareAppearanceProfile(1)
        assertEquals(AppAppearance.NOTFLIX, store.observe(capability(store)).first().requested)
    }

    @Test fun `alternation preserves official settings library progress and legitimate updates`() = runBlocking {
        val factory = factory()
        val features = listOf("experience_settings", "layout_settings", "theme_settings", "trailer_settings", "library_preferences", "watch_progress")
        features.forEach { name -> factory.get(1, name).edit { it[stringPreferencesKey("sentinel")] = name } }
        val store = NotflixAppearanceStore(factory)
        store.select(capability(store), AppAppearance.NOTFLIX).getOrThrow()
        factory.get(1, "theme_settings").edit { it[stringPreferencesKey("sentinel")] = "legitimate official change" }
        store.select(capability(store), AppAppearance.NUVIO).getOrThrow()
        features.forEach { name ->
            assertEquals(if (name == "theme_settings") "legitimate official change" else name,
                factory.get(1, name).data.first()[stringPreferencesKey("sentinel")])
        }
    }

    @Test fun `real account reset hook preserves official continuation when own deletion fails`() = runBlocking {
        file(1).mkdirs()
        File(file(1), "occupied").writeText("x")
        val factory = factory()
        val metadata = mockk<ProfileDataStore>(relaxed = true)
        val locks = mockk<ProfileLockStateDataStore>(relaxed = true)
        val credentials = mockk<ProfileScopedCredentialStore>(relaxed = true)
        val channels = mockk<AndroidTvChannelManager>(relaxed = true)
        coEvery { channels.clearAll() } throws IOException("channel failure")
        AccountLocalDataResetService(context, factory, metadata, locks, setOf(credentials), channels).clearAfterSignOut()
        coVerify(exactly = 1) { metadata.clearAll() }
        coVerify(exactly = 1) { locks.clearAll() }
        io.mockk.verify(exactly = 1) { credentials.clearAllProfiles() }
        assertNull(NotflixAppearanceStore(factory).contextFor(1, true, setOf(1)))
    }

    @Test fun `another installation of the same local profile does not inherit appearance`() = runBlocking {
        val first = NotflixAppearanceStore(factory())
        first.select(capability(first), AppAppearance.NOTFLIX).getOrThrow()
        val otherDir = File(dir, "other-installation").apply { mkdirs() }
        val otherContext = mockk<Context>(relaxed = true)
        every { otherContext.filesDir } returns otherDir
        every { otherContext.applicationContext } returns otherContext
        val other = ProfileDataStoreFactory(otherContext).also { factories += it }
        val second = NotflixAppearanceStore(other)
        assertEquals(AppAppearance.NOTFLIX, second.observe(capability(second)).first().requested)
        assertEquals(AppAppearance.NOTFLIX, first.observe(capability(first)).first().requested)
    }

    @Test fun `new profile defaults to Notflix while saved Nuvio and read failure stay Nuvio`() = runBlocking {
        val factory = factory()
        val store = NotflixAppearanceStore(factory)
        assertEquals(AppAppearance.NOTFLIX, store.observe(capability(store)).first().requested)
        store.select(capability(store), AppAppearance.NUVIO).getOrThrow()
        assertEquals(AppAppearance.NUVIO, store.observe(capability(store)).first().requested)

        requireNotNull(file(2).parentFile).mkdirs()
        file(2).mkdirs()
        File(file(2), "occupied").writeText("fixture")
        val failed = store.observe(capability(store, 2)).first()
        assertTrue(failed.failed)
        assertEquals(AppAppearance.NUVIO, failed.requested)
    }

}
