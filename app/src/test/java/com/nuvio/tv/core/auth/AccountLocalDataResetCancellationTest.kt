package com.nuvio.tv.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.core.sync.androidtv.AndroidTvChannelManager
import com.nuvio.tv.data.local.*
import com.nuvio.tv.domain.model.AppAppearance
import io.mockk.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AccountLocalDataResetCancellationTest {
    private lateinit var dir: File
    private lateinit var context: Context
    private lateinit var factory: ProfileDataStoreFactory
    private lateinit var store: NotflixAppearanceStore
    private val metadata = mockk<ProfileDataStore>(relaxed = true)
    private val locks = mockk<ProfileLockStateDataStore>(relaxed = true)
    private val credentials = mockk<ProfileScopedCredentialStore>(relaxed = true)
    private val channels = mockk<AndroidTvChannelManager>(relaxed = true)

    @Before fun setUp() {
        dir = Files.createTempDirectory("notflix-reset-cancel-").toFile()
        context = mockk(relaxed = true)
        every { context.filesDir } returns dir
        every { context.applicationContext } returns context
        factory = ProfileDataStoreFactory(context)
        store = NotflixAppearanceStore(factory)
    }

    private fun cache() = ProfileDataStoreFactory::class.java.getDeclaredField("cache")
        .apply { isAccessible = true }.get(factory) as MutableMap<*, *>
    private fun scopedJob(scoped: Any) = scoped::class.java.getDeclaredField("job")
        .apply { isAccessible = true }.get(scoped) as Job

    @After fun tearDown() = runBlocking {
        cache().values.toList().forEach { scopedJob(requireNotNull(it)).cancelAndJoin() }
        cache().clear()
        dir.deleteRecursively()
        Unit
    }

    private fun service(port: ProfileDataStoreFactory = factory) =
        AccountLocalDataResetService(context, port, metadata, locks, setOf(credentials), channels)
    private fun capability(id: Int) = requireNotNull(store.contextFor(id, true, (1..6).toSet()))
    private fun marker(id: Int) = File(dir, "datastore/${NotflixAppearanceStore.FEATURE}${if (id == 1) "" else "_p$id"}.retired")

    private suspend fun assertFresh() {
        (1..6).forEach { id ->
            assertFalse(marker(id).exists())
            val read = store.observe(capability(id)).first()
            assertFalse("A fallback must not stand in for a successful reopen", read.failed)
            assertEquals(AppAppearance.NOTFLIX, read.requested)
        }
    }

    @Test fun `cancellation during partial begin drains the removed owner and finalizes every profile`() = runBlocking {
        withTimeout(10_000) {
            val old = capability(1)
            store.select(old, AppAppearance.NOTFLIX).getOrThrow()
            store.select(capability(2), AppAppearance.NOTFLIX).getOrThrow()
            factory.get(1, "theme_settings").edit { it[stringPreferencesKey("sentinel")] = "official" }
            val scoped = requireNotNull(cache()[NotflixAppearanceStore.FEATURE])
            val started = CompletableDeferred<Unit>()
            val retiring = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            // Real scoped DataStore owner with a controlled finalizer: retirement
            // has removed it from the cache and must still wait for its last IO.
            CoroutineScope(scopedJob(scoped) + Dispatchers.IO).launch {
                started.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { retiring.complete(Unit); release.await() }
                }
            }
            started.await()
            val returnedNormally = AtomicBoolean(false)
            val reset = async(Dispatchers.IO) { service().clearAfterSignOut(); returnedNormally.set(true) }
            try {
                retiring.await()
                assertNull(store.contextFor(1, true, setOf(1)))
                assertTrue(marker(1).exists())
                reset.cancel()
                assertFalse(reset.isCompleted)
            } finally {
                release.complete(Unit)
                withContext(NonCancellable) { reset.join() }
            }
            assertTrue(reset.isCancelled)
            assertFalse(returnedNormally.get())
            assertTrue(scopedJob(scoped).isCompleted)
            assertTrue(store.select(old, AppAppearance.NOTFLIX).isFailure)
            assertFresh()
            coVerify(exactly = 0) { channels.clearAll() }
            assertEquals("official", factory.get(1, "theme_settings").data.first()[stringPreferencesKey("sentinel")])
        }
    }

    @Test fun `cancellation in official suspended cleanup propagates after own finalization`() = runBlocking {
        withTimeout(10_000) {
            val old = capability(1)
            store.select(old, AppAppearance.NOTFLIX).getOrThrow()
            val entered = CompletableDeferred<Unit>()
            coEvery { channels.clearAll() } coAnswers { entered.complete(Unit); awaitCancellation() }
            val returnedNormally = AtomicBoolean(false)
            val reset = async(Dispatchers.IO) { service().clearAfterSignOut(); returnedNormally.set(true) }
            entered.await()
            reset.cancelAndJoin()
            assertTrue(reset.isCancelled)
            assertFalse(returnedNormally.get())
            assertFalse(store.isCurrent(old))
            assertFresh()
            // The existing best-effort official sequence still attempts metadata.
            coVerify(exactly = 1) { metadata.clearAll() }
            coVerify(exactly = 1) { locks.clearAll() }
        }
    }

    @Test fun `cancellation arriving during finalization cannot interrupt actual cleanup`() = runBlocking {
        withTimeout(10_000) {
            store.select(capability(1), AppAppearance.NOTFLIX).getOrThrow()
            val finishing = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            // Only the phase boundary is controlled. Actual begin/finish, files
            // and DataStore are the real factory; no simulated cleanup result.
            val port = mockk<ProfileDataStoreFactory>()
            coEvery { port.beginAppearanceAccountReset() } coAnswers { factory.beginAppearanceAccountReset() }
            coEvery { port.clearProfileScopedData() } coAnswers { factory.clearProfileScopedData() }
            coEvery { port.finishAppearanceAccountReset() } coAnswers {
                finishing.complete(Unit)
                release.await()
                factory.finishAppearanceAccountReset()
            }
            val returnedNormally = AtomicBoolean(false)
            val reset = async(Dispatchers.IO) { service(port).clearAfterSignOut(); returnedNormally.set(true) }
            try {
                finishing.await()
                reset.cancel()
                assertFalse(reset.isCompleted)
                assertNull(store.contextFor(1, true, setOf(1)))
            } finally {
                release.complete(Unit)
                withContext(NonCancellable) { reset.join() }
            }
            assertTrue(reset.isCancelled)
            assertFalse(returnedNormally.get())
            assertFresh()
        }
    }

    @Test fun `cancelled reset keeps failed disk removal quarantined and permits a safe retry`() = runBlocking {
        withTimeout(10_000) {
            val old = capability(1)
            store.select(capability(2), AppAppearance.NOTFLIX).getOrThrow()
            val entered = CompletableDeferred<Unit>()
            coEvery { channels.clearAll() } coAnswers { entered.complete(Unit); awaitCancellation() }
            val returnedNormally = AtomicBoolean(false)
            val reset = async(Dispatchers.IO) { service().clearAfterSignOut(); returnedNormally.set(true) }
            entered.await()
            val target = File(dir, "datastore/${NotflixAppearanceStore.FEATURE}.preferences_pb")
            target.mkdirs()
            val child = File(target, "occupied").apply { writeText("x") }
            reset.cancelAndJoin()
            assertTrue(reset.isCancelled)
            assertFalse(returnedNormally.get())
            assertTrue(marker(1).exists())
            assertNull(store.contextFor(1, true, setOf(1)))
            assertTrue(store.select(old, AppAppearance.NOTFLIX).isFailure)
            assertEquals(AppAppearance.NOTFLIX, store.observe(capability(2)).first().requested)
            assertTrue(child.delete())
            assertTrue(target.delete())
            factory.prepareAppearanceProfile(1)
            assertFalse(marker(1).exists())
            assertEquals(AppAppearance.NOTFLIX, store.observe(capability(1)).first().requested)
        }
    }
}
