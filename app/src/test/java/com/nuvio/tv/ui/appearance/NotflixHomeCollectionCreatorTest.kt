package com.nuvio.tv.ui.appearance

import androidx.lifecycle.ViewModelStore
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.*
import com.nuvio.tv.domain.model.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class NotflixHomeCollectionCreatorTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = mockk<CollectionsDataStore>()
    private val manager = mockk<ProfileManager>()
    private val auth = mockk<AuthManager>()
    private val active = MutableStateFlow(2)
    private val signed = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val own = UserProfile(2, "Synthetic", "#000000", usesPrimaryAddons=true)
    private val owner = ViewModelStore()
    private lateinit var vm: NotflixHomeCollectionViewModel
    private val request = NotflixCollectionRequest(AppearanceContext(2, 7), 11, false)
    private val appearance get() = AppearanceState(context=request.context, requested=AppAppearance.NOTFLIX,
        effective=AppAppearance.NOTFLIX, selectionGeneration=request.selectionGeneration)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { manager.activeProfileId } returns active
        every { manager.activeProfile } answers { if(active.value==2) own else own.copy(id=3) }
        every { auth.authState } returns signed
        coEvery { store.insertCollectionIfAbsent(any(), any(), any()) } coAnswers {
            if(thirdArg<()->Boolean>()()) CollectionInsertResult.Inserted else CollectionInsertResult.NotAuthorized
        }
        vm = NotflixHomeCollectionViewModel(store, manager, signed); owner.put("creator",vm)
    }
    @After fun close(){owner.clear();Dispatchers.resetMain()}

    @Test fun effectiveInsertionUsesCapturedProfileAndOfficialPreset()=runTest(dispatcher) {
        val result=NotflixHomeCollectionCreator(store).create(request){true}
        assertEquals(NotflixCollectionResult.CREATED_LOCAL,result)
        coVerify(exactly=1){store.insertCollectionIfAbsent(2,NotflixHomeCollectionPreset.create(),any())}
    }
    @Test fun revokedContextNeverStartsStorage()=runTest(dispatcher) {
        assertEquals(NotflixCollectionResult.CONTEXT_CHANGED,NotflixHomeCollectionCreator(store).create(request){false})
        coVerify(exactly=0){store.insertCollectionIfAbsent(any(),any(),any())}
    }
    @Test fun accountIsBlockedBeforeWriteBecauseOfficialSyncCannotBindRequest()=runTest(dispatcher) {
        assertEquals(NotflixCollectionResult.ACCOUNT_SYNC_PENDING,
            NotflixHomeCollectionCreator(store).create(request.copy(authenticated=true)){true})
        coVerify(exactly=0){store.insertCollectionIfAbsent(any(),any(),any())}
    }
    @Test fun collisionAndIoHaveExplicitResultsWithoutRetryOrOverwrite()=runTest(dispatcher) {
        coEvery{store.insertCollectionIfAbsent(any(),any(),any())} returns CollectionInsertResult.Exists("edited")
        assertEquals(NotflixCollectionResult.EXISTS,NotflixHomeCollectionCreator(store).create(request){true})
        coEvery{store.insertCollectionIfAbsent(any(),any(),any())} throws IOException("synthetic")
        assertEquals(NotflixCollectionResult.FAILED,NotflixHomeCollectionCreator(store).create(request){true})
        coVerify(exactly=2){store.insertCollectionIfAbsent(any(),any(),any())}
    }
    @Test fun cancellationRemainsCancellation()=runTest(dispatcher) {
        coEvery{store.insertCollectionIfAbsent(any(),any(),any())} throws CancellationException("synthetic")
        try {NotflixHomeCollectionCreator(store).create(request){true};fail("Expected cancellation")}
        catch(_:CancellationException){}
    }
    @Test fun rapidDoublePressStartsOneWriteAndKeepsCapturedContext()=runTest(dispatcher) {
        vm.create(appearance){ctx,generation->ctx==request.context && generation==11L}
        vm.create(appearance){_,_->true};assertTrue(vm.state.value.busy);runCurrent()
        coVerify(exactly=1){store.insertCollectionIfAbsent(2,any(),any())}
        assertEquals(NotflixCollectionResult.CREATED_LOCAL,vm.state.value.result);assertFalse(vm.state.value.busy)
    }
    @Test fun queuedProfileChangeCannotRetargetOrPublishOldResult()=runTest(dispatcher) {
        vm.create(appearance){_,_->true};active.value=3;runCurrent()
        coVerify(exactly=0){store.insertCollectionIfAbsent(any(),any(),any())}
        assertNull(vm.state.value.result);assertNull(vm.state.value.request)
    }
    @Test fun queuedGenerationOrAppearanceRevocationCannotWrite()=runTest(dispatcher) {
        var authorized=true;vm.create(appearance){_,_->authorized};authorized=false;runCurrent()
        coVerify(exactly=0){store.insertCollectionIfAbsent(any(),any(),any())}
        assertNull(vm.state.value.result)
    }
    @Test fun queuedAuthenticationChangeCannotWriteGuestDataIntoAccount()=runTest(dispatcher) {
        vm.create(appearance){_,_->true};signed.value=AuthState.FullAccount("synthetic","synthetic@example.invalid");runCurrent()
        coVerify(exactly=0){store.insertCollectionIfAbsent(any(),any(),any())}
        assertNull(vm.state.value.result)
    }
    @Test fun anotherContextDoesNotReceiveAnEarlierMessage() {
        val old=NotflixCollectionCreationState(request,result=NotflixCollectionResult.CREATED_LOCAL)
        assertNull(old.forAppearance(appearance.copy(context=AppearanceContext(3,8))).result)
        assertNull(old.forAppearance(appearance.copy(selectionGeneration=12)).result)
        assertEquals(old,old.forAppearance(appearance))
    }

    @Test fun realAppearanceAuthorizationRequiresConfirmedNotflixAndCurrentGeneration()=runTest(dispatcher) {
        val disk=MutableStateFlow<PersistedAppearanceProfile?>(PersistedAppearanceProfile(2,1))
        val reads=MutableStateFlow(AppearanceRead(AppAppearance.NOTFLIX))
        val appearanceStore=mockk<NotflixAppearanceStore>()
        every{manager.persistedAppearanceProfile} returns disk
        every{manager.profiles} returns MutableStateFlow(listOf(own))
        every{appearanceStore.revision} returns MutableStateFlow(0L)
        every{appearanceStore.contextFor(any(),any(),any())} returns request.context
        every{appearanceStore.isCurrent(any())} returns true
        every{appearanceStore.observe(any())} returns reads
        val controller=AppearanceViewModel(manager,appearanceStore);owner.put("appearance",controller)
        runCurrent();assertFalse(controller.authorizesCollectionCreation(request.context,11))
        assertTrue(controller.confirmSession(requireNotNull(controller.beginSessionConfirmation(2))));runCurrent()
        val gen=controller.state.value.selectionGeneration
        assertTrue(controller.authorizesCollectionCreation(request.context,gen))
        assertFalse(controller.authorizesCollectionCreation(request.context,gen-1))
        reads.value=reads.value.copy(requested=AppAppearance.NUVIO);runCurrent()
        assertFalse(controller.authorizesCollectionCreation(request.context,gen))
        reads.value=reads.value.copy(requested=AppAppearance.NOTFLIX);runCurrent()
        assertTrue(controller.authorizesCollectionCreation(request.context,gen))
        controller.cancelSessionConfirmation()
        assertFalse(controller.authorizesCollectionCreation(request.context,gen))
    }

    private class FakeSync(var pulls: Boolean = true, var pushes: Boolean = true) : NotflixCollectionSync {
        val calls = mutableListOf<String>()
        override suspend fun pullActiveProfile(): Boolean { calls += "pull"; return pulls }
        override suspend fun pushActiveProfile(): Boolean { calls += "push"; return pushes }
    }

    @Test fun accountPullInsertPushCreatesAndSyncs()=runTest(dispatcher) {
        val sync=FakeSync()
        assertEquals(NotflixCollectionResult.CREATED_ACCOUNT,
            NotflixHomeCollectionCreator(store,sync).create(request.copy(authenticated=true)){true})
        assertEquals(listOf("pull","push"),sync.calls)
        coVerify(exactly=1){store.insertCollectionIfAbsent(2,any(),any())}
        coVerify(exactly=0){store.removeCollection(any())}
    }
    @Test fun failedPullWritesNothing()=runTest(dispatcher) {
        val sync=FakeSync(pulls=false)
        assertEquals(NotflixCollectionResult.FAILED,
            NotflixHomeCollectionCreator(store,sync).create(request.copy(authenticated=true)){true})
        assertEquals(listOf("pull"),sync.calls)
        coVerify(exactly=0){store.insertCollectionIfAbsent(any(),any(),any())}
    }
    @Test fun failedPushRollsBackTheLocalInsert()=runTest(dispatcher) {
        coEvery{store.removeCollection(any())} just Runs
        val sync=FakeSync(pushes=false)
        assertEquals(NotflixCollectionResult.FAILED,
            NotflixHomeCollectionCreator(store,sync).create(request.copy(authenticated=true)){true})
        coVerify(exactly=1){store.removeCollection(NotflixHomeCollectionPreset.create().id)}
    }
    @Test fun existingCollectionIsNotPushedAgain()=runTest(dispatcher) {
        coEvery{store.insertCollectionIfAbsent(any(),any(),any())} returns CollectionInsertResult.Exists("edited")
        val sync=FakeSync()
        assertEquals(NotflixCollectionResult.EXISTS,
            NotflixHomeCollectionCreator(store,sync).create(request.copy(authenticated=true)){true})
        assertEquals(listOf("pull"),sync.calls)
    }
}
