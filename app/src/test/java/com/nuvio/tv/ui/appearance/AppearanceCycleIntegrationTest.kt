package com.nuvio.tv.ui.appearance

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.PersistedAppearanceProfile
import com.nuvio.tv.domain.model.UserProfile
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** ViewModel + store + factory + serializer are real. Only Android Context and profile metadata are controlled. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceCycleIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val persisted = MutableStateFlow<PersistedAppearanceProfile?>(PersistedAppearanceProfile(2, 1))
    private lateinit var dir: File
    private lateinit var factory: ProfileDataStoreFactory
    private lateinit var store: NotflixAppearanceStore
    private lateinit var vm: AppearanceViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dir = Files.createTempDirectory("notflix-cycle-").toFile()
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns dir
        every { context.applicationContext } returns context
        factory = ProfileDataStoreFactory(context)
        store = NotflixAppearanceStore(factory)
        val manager = mockk<ProfileManager>()
        every { manager.persistedAppearanceProfile } returns persisted
        every { manager.profiles } returns MutableStateFlow(listOf(
            UserProfile(1, "P1", "#1E88E5"), UserProfile(2, "P2", "#1E88E5")
        ))
        vm = AppearanceViewModel(manager, store)
        owner.put("appearance", vm)
    }

    @After fun tearDown() {
        owner.clear()
        runBlocking {
            val cache = ProfileDataStoreFactory::class.java.getDeclaredField("cache")
                .apply { isAccessible = true }.get(factory) as MutableMap<*, *>
            cache.values.toList().forEach { scoped ->
                val job = scoped!!::class.java.getDeclaredField("job").apply { isAccessible = true }.get(scoped) as Job
                job.cancelAndJoin()
            }
            cache.clear()
        }
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun authorize(): AppearanceConfirmationRequest = requireNotNull(vm.beginSessionConfirmation(requireNotNull(persisted.value).profileId)).also {
        check(vm.confirmSession(it))
    }

    // Timeout runs on the real dispatcher, so disk IO is not raced by virtual time.
    private suspend fun awaitState(predicate: (AppearanceState) -> Boolean): AppearanceState =
        withContext(Dispatchers.Default) { withTimeout(10_000) { vm.state.first(predicate) } }

    @Test fun `real save keeps every observed state continuous and requested value persists`() = runTest(dispatcher) {
        runCurrent()
        assertNull(vm.state.value.context)
        assertFalse(File(dir, "datastore/${NotflixAppearanceStore.FEATURE}_p2.preferences_pb").exists())
        authorize()
        val before = awaitState { it.context != null }
        assertFalse(before.readFailed)
        val emissions = mutableListOf<AppearanceState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { emissions += it } }
        vm.select(AppAppearance.NOTFLIX)
        awaitState { it.requested == AppAppearance.NOTFLIX }
        vm.retry()
        runCurrent()
        assertTrue(emissions.all { it.context == before.context })
        val confirmedIndex = emissions.indexOfFirst { it.requested == AppAppearance.NOTFLIX }
        assertTrue(confirmedIndex >= 0)
        assertTrue(emissions.subList(confirmedIndex, emissions.size).all { it.requested == AppAppearance.NOTFLIX })
        assertEquals(AppAppearance.NOTFLIX, store.observe(requireNotNull(before.context)).first().requested)
        assertTrue(File(dir, "datastore/${NotflixAppearanceStore.FEATURE}_p2.preferences_pb").length() > 0)
    }

    @Test fun `same ViewModel survives real retirement and a fresh authorized generation`() = runTest(dispatcher) {
        val oldAuthorization = authorize()
        val old = requireNotNull(awaitState { it.context != null }.context)
        vm.select(AppAppearance.NOTFLIX)
        awaitState { it.requested == AppAppearance.NOTFLIX }
        factory.clearProfile(2) // cancel actual DataStore while its observation is live
        awaitState { it.context == null }
        assertTrue(store.select(old, AppAppearance.NOTFLIX).isFailure)
        factory.markProfileCreated(2)
        factory.prepareAppearanceProfile(2)
        runCurrent()
        assertNull(vm.state.value.context)
        assertFalse(vm.confirmSession(oldAuthorization))
        authorize()
        val fresh = awaitState { it.context != null }
        assertFalse(fresh.readFailed)
        assertNotEquals(old, fresh.context)
        assertEquals(AppAppearance.NOTFLIX, fresh.requested)
        vm.select(AppAppearance.NOTFLIX)
        awaitState { it.requested == AppAppearance.NOTFLIX && it.context == fresh.context }
    }

    @Test fun `account reset revokes session even if the persisted id is unchanged`() = runTest(dispatcher) {
        authorize()
        awaitState { it.context != null }
        vm.select(AppAppearance.NOTFLIX)
        awaitState { it.requested == AppAppearance.NOTFLIX }
        factory.beginAppearanceAccountReset()
        awaitState { it.context == null }
        factory.finishAppearanceAccountReset()
        runCurrent()
        assertNull(vm.state.value.context)
        authorize()
        val restored = awaitState { it.context != null }
        assertFalse(restored.readFailed)
        assertEquals(AppAppearance.NOTFLIX, restored.requested)
    }

    @Test fun `real read error can recover without emptying the same authorized context`() = runTest(dispatcher) {
        val target = File(dir, "datastore/${NotflixAppearanceStore.FEATURE}_p2.preferences_pb")
        target.mkdirs()
        val child = File(target, "occupied").apply { writeText("x") }
        authorize()
        val failed = awaitState { it.readFailed }
        assertNotNull(failed.context)
        val emissions = mutableListOf<AppearanceState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { emissions += it } }
        assertTrue(child.delete())
        assertTrue(target.delete())
        vm.retry()
        awaitState { !it.readFailed && it.context != null }
        vm.select(AppAppearance.NOTFLIX)
        awaitState { it.requested == AppAppearance.NOTFLIX }
        assertTrue(emissions.all { it.context == failed.context })
    }
}
