package com.nuvio.tv.ui.appearance

import androidx.lifecycle.ViewModelStore
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.AppearanceRead
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.domain.model.PersistedAppearanceProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.yield
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val id = MutableStateFlow(1)
    private val ready = MutableStateFlow(false)
    private val persisted = MutableStateFlow<PersistedAppearanceProfile?>(null)
    private var profileRevision = 0L
    private val profiles = MutableStateFlow(listOf(profile(1), profile(2)))
    private val reads = mapOf(1 to MutableStateFlow(AppearanceRead(AppAppearance.NUVIO)),
        2 to MutableStateFlow(AppearanceRead(AppAppearance.NOTFLIX)))
    private val store = mockk<NotflixAppearanceStore>()
    private val owner = ViewModelStore()
    private lateinit var vm: AppearanceViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val manager = mockk<ProfileManager>()
        every { manager.activeProfileId } returns id
        every { manager.activeProfileReady } returns ready
        every { manager.persistedAppearanceProfile } returns persisted
        every { manager.profiles } returns profiles
        every { store.revision } returns MutableStateFlow(0L)
        every { store.contextFor(any(), any(), any()) } answers {
            val profileId = firstArg<Int>()
            if (secondArg<Boolean>() && profileId in thirdArg<Set<Int>>()) AppearanceContext(profileId, profileId.toLong()) else null
        }
        every { store.isCurrent(any()) } returns true
        every { store.observe(any()) } answers { reads.getValue(firstArg<AppearanceContext>().profileId) }
        vm = AppearanceViewModel(manager, store)
        owner.put("appearance", vm)
    }

    @After fun tearDown() {
        owner.clear()
        Dispatchers.resetMain()
    }

    @Test fun `provisional context cannot save and availability governs effective choice`() = runTest(dispatcher) {
        ready.value = true // another official collector can be ready before its id changes
        runCurrent()
        vm.select(AppAppearance.NOTFLIX)
        runCurrent()
        coVerify(exactly = 0) { store.select(any(), any()) }
        ready.value = true
        id.value = 2
        authorize(2)
        runCurrent()
        assertEquals(AppAppearance.NOTFLIX, vm.state.value.requested)
        assertEquals(if (AppearancePolicy.NOTFLIX_AVAILABLE) AppAppearance.NOTFLIX else AppAppearance.NUVIO, vm.state.value.effective)
    }

    @Test fun `late read and failed save from A do not affect B or a new A selection`() = runTest(dispatcher) {
        ready.value = true
        authorize(1)
        runCurrent()
        val pending = CompletableDeferred<Result<Unit>>()
        coEvery { store.select(AppearanceContext(1, 1), AppAppearance.NOTFLIX) } coAnswers { pending.await() }
        vm.select(AppAppearance.NOTFLIX)
        runCurrent()
        id.value = 2
        authorize(2)
        runCurrent()
        reads.getValue(1).value = AppearanceRead(AppAppearance.NOTFLIX)
        runCurrent()
        assertEquals(2, vm.state.value.context?.profileId)
        assertFalse(vm.state.value.saveFailed)
        id.value = 1
        authorize(1)
        runCurrent()
        pending.complete(Result.failure(IOException("old selection failed")))
        runCurrent()
        assertEquals(1, vm.state.value.context?.profileId)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test fun `failed current save is reported without changing confirmed choice`() = runTest(dispatcher) {
        ready.value = true
        authorize(1)
        runCurrent()
        coEvery { store.select(any(), any()) } returns Result.failure(IOException("disk failure"))
        vm.select(AppAppearance.NOTFLIX)
        runCurrent()
        assertTrue(vm.state.value.saveFailed)
        assertEquals(AppAppearance.NUVIO, vm.state.value.requested)
        assertEquals(AppAppearance.NUVIO, vm.state.value.effective)
    }

    @Test fun `save rejects stale visible state immediately after profile switch`() = runTest(dispatcher) {
        ready.value = true
        authorize(1)
        runCurrent()
        id.value = 2
        authorize(2)
        vm.select(AppAppearance.NOTFLIX) // collectors have not processed the new id yet
        runCurrent()
        coVerify(exactly = 0) { store.select(any(), any()) }
        assertEquals(2, vm.state.value.context?.profileId)
    }

    @Test fun `retry reopens a failed read without changing profile or official data`() = runTest(dispatcher) {
        var attempts = 0
        every { store.observe(AppearanceContext(1, 1)) } answers {
            attempts++
            kotlinx.coroutines.flow.flowOf(if (attempts == 1) AppearanceRead(AppAppearance.NUVIO, failed = true)
                else AppearanceRead(AppAppearance.NOTFLIX))
        }
        authorize(1)
        runCurrent()
        assertTrue(vm.state.value.readFailed)
        vm.retry()
        runCurrent()
        assertEquals(2, attempts)
        assertFalse(vm.state.value.readFailed)
        assertEquals(AppAppearance.NOTFLIX, vm.state.value.requested)
        assertEquals(if (AppearancePolicy.NOTFLIX_AVAILABLE) AppAppearance.NOTFLIX else AppAppearance.NUVIO, vm.state.value.effective)
        coVerify(exactly = 0) { store.select(any(), any()) }
    }

    @Test fun `successful save reopens a completed failed read`() = runTest(dispatcher) {
        val result = MutableStateFlow(AppearanceRead(AppAppearance.NUVIO, failed = true))
        every { store.observe(AppearanceContext(1, 1)) } answers { kotlinx.coroutines.flow.flowOf(result.value) }
        coEvery { store.select(any(), AppAppearance.NOTFLIX) } coAnswers {
            result.value = AppearanceRead(AppAppearance.NOTFLIX)
            Result.success(Unit)
        }
        authorize(1)
        runCurrent()
        vm.select(AppAppearance.NOTFLIX)
        runCurrent()
        assertEquals(AppAppearance.NOTFLIX, vm.state.value.requested)
        assertFalse(vm.state.value.readFailed)
    }

    @Test fun `healthy save never empties context or rolls back the confirmed choice`() = runTest(dispatcher) {
        authorize(1)
        runCurrent()
        val context = requireNotNull(vm.state.value.context)
        val emissions = mutableListOf<AppearanceState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { emissions += it } }
        coEvery { store.select(any(), AppAppearance.NOTFLIX) } coAnswers {
            reads.getValue(1).value = AppearanceRead(AppAppearance.NOTFLIX)
            yield()
            Result.success(Unit)
        }
        vm.select(AppAppearance.NOTFLIX)
        runCurrent()
        assertEquals(AppAppearance.NOTFLIX, vm.state.value.requested)
        assertTrue(emissions.all { it.context == context })
        val firstConfirmed = emissions.indexOfFirst { it.requested == AppAppearance.NOTFLIX }
        assertTrue(firstConfirmed >= 0)
        assertTrue(emissions.subList(firstConfirmed, emissions.size).all { it.requested == AppAppearance.NOTFLIX })
        verify(exactly = 1) { store.observe(context) }
    }

    @Test fun `persisted profile is closed until an explicit current session decision`() = runTest(dispatcher) {
        persisted.value = PersistedAppearanceProfile(2, ++profileRevision)
        ready.value = true
        runCurrent()
        assertNull(vm.state.value.context)
        vm.select(AppAppearance.NOTFLIX)
        runCurrent()
        verify(exactly = 0) { store.observe(any()) }
        coVerify(exactly = 0) { store.select(any(), any()) }
        assertNull(vm.beginSessionConfirmation(1)) // disk2 must not stand in for a decision about1
        val cancelledPin = requireNotNull(vm.beginSessionConfirmation(requireNotNull(persisted.value).profileId))
        vm.cancelSessionConfirmation()
        assertFalse(vm.confirmSession(cancelledPin))
        runCurrent()
        assertNull(vm.state.value.context)
        val valid = requireNotNull(vm.beginSessionConfirmation(requireNotNull(persisted.value).profileId))
        assertTrue(vm.confirmSession(valid))
        runCurrent()
        assertEquals(2, vm.state.value.context?.profileId)
        vm.cancelSessionConfirmation()
        runCurrent()
        assertNull(vm.state.value.context)
    }

    @Test fun `late confirmation cannot authorize another profile or returned disk revision`() = runTest(dispatcher) {
        persisted.value = PersistedAppearanceProfile(2, ++profileRevision)
        val old = requireNotNull(vm.beginSessionConfirmation(requireNotNull(persisted.value).profileId))
        persisted.value = PersistedAppearanceProfile(1, ++profileRevision)
        assertFalse(vm.confirmSession(old))
        persisted.value = PersistedAppearanceProfile(2, ++profileRevision)
        assertFalse(vm.confirmSession(old))
        runCurrent()
        assertNull(vm.state.value.context)
        val valid = requireNotNull(vm.beginSessionConfirmation(requireNotNull(persisted.value).profileId))
        assertTrue(vm.confirmSession(valid))
        runCurrent()
        assertEquals(2, vm.state.value.context?.profileId)
    }

    @Test fun `confirmation token from another controller cannot authorize this session`() {
        val context = AppearanceSessionContext(PersistedAppearanceProfile(2, 1), AppearanceContext(2, 1))
        val previousController = AppearanceSessionConfirmation()
        val currentController = AppearanceSessionConfirmation()
        val obsolete = requireNotNull(previousController.begin(context))
        val current = requireNotNull(currentController.begin(context))
        assertEquals(obsolete.attempt, current.attempt)
        assertFalse(currentController.confirm(obsolete, context))
        assertNull(currentController.authorized(context))
        assertTrue(currentController.confirm(current, context))
    }

    @Test fun `motion is closed before confirmation and while Nuvio is effective`() = runTest(dispatcher) {
        vm.selectHomeMotion(true)
        runCurrent()
        coVerify(exactly = 0) { store.selectHomeMotion(any(), any(), any()) }
        authorize(1)
        runCurrent()
        vm.selectHomeMotion(true)
        runCurrent()
        coVerify(exactly = 0) { store.selectHomeMotion(any(), any(), any()) }
        authorize(2)
        runCurrent()
        coEvery { store.selectHomeMotion(AppearanceContext(2, 2), true, any()) } coAnswers {
            assertTrue(thirdArg<() -> Boolean>().invoke())
            reads.getValue(2).value = AppearanceRead(AppAppearance.NOTFLIX, reduceHomeMotion = true)
            Result.success(Unit)
        }
        vm.selectHomeMotion(true)
        runCurrent()
        assertTrue(vm.state.value.reduceHomeMotion)
        coVerify(exactly = 1) { store.selectHomeMotion(any(), any(), any()) }
        coVerify(exactly = 0) { store.select(any(), any()) }
    }

    @Test fun `queued motion request is rejected when confirmation is revoked before launch`() = runTest(dispatcher) {
        authorize(2)
        runCurrent()
        vm.selectHomeMotion(true)
        vm.cancelSessionConfirmation()
        runCurrent()
        coVerify(exactly = 0) { store.selectHomeMotion(any(), any(), any()) }
        assertNull(vm.state.value.context)
    }

    @Test fun `motion commit capability and late failure expire across another session of same profile`() = runTest(dispatcher) {
        authorize(2)
        runCurrent()
        val generation = vm.state.value.selectionGeneration
        val finish = CompletableDeferred<Result<Unit>>()
        lateinit var guard: () -> Boolean
        coEvery { store.selectHomeMotion(any(), true, any()) } coAnswers {
            guard = thirdArg()
            finish.await()
        }
        vm.selectHomeMotion(true)
        runCurrent()
        assertTrue(guard())
        authorize(1)
        runCurrent()
        assertFalse(guard())
        assertFalse(vm.state.value.reduceHomeMotion)
        authorize(2)
        runCurrent()
        assertNotEquals(generation, vm.state.value.selectionGeneration)
        assertFalse(guard())
        finish.complete(Result.failure(IOException("old motion save")))
        runCurrent()
        assertFalse(vm.state.value.saveFailed)
        assertFalse(vm.state.value.reduceHomeMotion)
    }

    @Test fun `failed motion save stays Normal and successful save keeps current context without reobserve`() = runTest(dispatcher) {
        authorize(2)
        runCurrent()
        val context = requireNotNull(vm.state.value.context)
        val emissions = mutableListOf<AppearanceState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { emissions += it } }
        coEvery { store.selectHomeMotion(any(), true, any()) } returns Result.failure(IOException("motion disk"))
        vm.selectHomeMotion(true)
        runCurrent()
        assertTrue(vm.state.value.saveFailed)
        assertFalse(vm.state.value.reduceHomeMotion)
        coEvery { store.selectHomeMotion(any(), true, any()) } coAnswers {
            assertTrue(thirdArg<() -> Boolean>().invoke())
            reads.getValue(2).value = AppearanceRead(AppAppearance.NOTFLIX, reduceHomeMotion = true)
            yield()
            Result.success(Unit)
        }
        vm.selectHomeMotion(true)
        runCurrent()
        assertTrue(vm.state.value.reduceHomeMotion)
        assertFalse(vm.state.value.saveFailed)
        assertTrue(emissions.all { it.context == context && it.requested == AppAppearance.NOTFLIX })
        verify(exactly = 1) { store.observe(context) }
        coVerify(exactly = 0) { store.select(any(), any()) }
    }

    @Test fun `motion selection after profile switch rejects still visible old context`() = runTest(dispatcher) {
        authorize(2)
        runCurrent()
        authorize(1)
        vm.selectHomeMotion(true) // old UI state has not processed profile1 yet
        runCurrent()
        coVerify(exactly = 0) { store.selectHomeMotion(any(), any(), any()) }
        assertEquals(1, vm.state.value.context?.profileId)
        assertFalse(vm.state.value.reduceHomeMotion)
    }

    private fun authorize(id: Int) {
        persisted.value = PersistedAppearanceProfile(id, ++profileRevision)
        check(vm.confirmSession(requireNotNull(vm.beginSessionConfirmation(requireNotNull(persisted.value).profileId))))
    }

    private fun profile(id: Int) = UserProfile(id, "P$id", "#1E88E5")
}
