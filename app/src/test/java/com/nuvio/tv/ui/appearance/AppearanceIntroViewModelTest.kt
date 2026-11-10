package com.nuvio.tv.ui.appearance

import androidx.lifecycle.ViewModelStore
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
class AppearanceIntroViewModelTest {
    private val dispatcher=StandardTestDispatcher();private val store=mockk<NotflixAppearanceStore>()
    private val persisted=MutableStateFlow<PersistedAppearanceProfile?>(null);private var revision=0L
    private val reads=mapOf(1 to MutableStateFlow(AppearanceRead(AppAppearance.NUVIO)),2 to MutableStateFlow(AppearanceRead(AppAppearance.NOTFLIX)))
    private val owner=ViewModelStore();private lateinit var vm:AppearanceViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher);val manager=mockk<ProfileManager>()
        every{manager.persistedAppearanceProfile} returns persisted
        every{manager.profiles} returns MutableStateFlow(listOf(UserProfile(1,"P1","#000000"),UserProfile(2,"P2","#000000")))
        every{store.revision} returns MutableStateFlow(0L)
        every{store.contextFor(any(),any(),any())} answers{val id=firstArg<Int>();if(secondArg<Boolean>() && id in thirdArg<Set<Int>>()) AppearanceContext(id,id.toLong()) else null}
        every{store.isCurrent(any())} returns true
        every{store.observe(any())} answers{reads.getValue(firstArg<AppearanceContext>().profileId)}
        vm=AppearanceViewModel(manager,store);owner.put("intro",vm)
    }
    @After fun cleanup(){owner.clear();Dispatchers.resetMain()}
    private fun authorize(id:Int){persisted.value=PersistedAppearanceProfile(id,++revision);check(vm.confirmSession(requireNotNull(vm.beginSessionConfirmation(id))))}

    @Test fun `unconfirmed and Nuvio context cannot write either intro option`()=runTest(dispatcher) {
        vm.selectIntroEnabled(false);vm.selectIntroSoundEnabled(false);runCurrent()
        authorize(1);runCurrent();vm.selectIntroEnabled(false);vm.selectIntroSoundEnabled(false);runCurrent()
        coVerify(exactly=0){store.selectIntroEnabled(any(),any(),any())};coVerify(exactly=0){store.selectIntroSoundEnabled(any(),any(),any())}
    }
    @Test fun `confirmed Notflix options keep same context and other preference`()=runTest(dispatcher) {
        authorize(2);runCurrent();val context=vm.state.value.context;val generation=vm.state.value.selectionGeneration
        coEvery{store.selectIntroEnabled(any(),false,any())} coAnswers {
            assertTrue(thirdArg<()->Boolean>()());reads.getValue(2).value=reads.getValue(2).value.copy(introEnabled=false);Result.success(Unit)
        }
        vm.selectIntroEnabled(false);runCurrent();assertFalse(vm.state.value.introEnabled);assertTrue(vm.state.value.introSoundEnabled)
        assertEquals(context,vm.state.value.context);assertEquals(generation,vm.state.value.selectionGeneration)
        verify(exactly=1){store.observe(requireNotNull(context))};coVerify(exactly=0){store.select(any(),any())}
    }
    @Test fun `queued requests are rejected after confirmation revocation`()=runTest(dispatcher) {
        authorize(2);runCurrent();vm.selectIntroEnabled(false);vm.selectIntroSoundEnabled(false);vm.cancelSessionConfirmation();runCurrent()
        coVerify(exactly=0){store.selectIntroEnabled(any(),any(),any())};coVerify(exactly=0){store.selectIntroSoundEnabled(any(),any(),any())};assertNull(vm.state.value.context)
    }
    @Test fun `late failure and mutation guard cannot cross same profile new generation`()=runTest(dispatcher) {
        authorize(2);runCurrent();val generation=vm.state.value.selectionGeneration
        val finish=CompletableDeferred<Result<Unit>>();lateinit var guard:()->Boolean
        coEvery{store.selectIntroSoundEnabled(any(),false,any())} coAnswers{guard=thirdArg();finish.await()}
        vm.selectIntroSoundEnabled(false);runCurrent();assertTrue(guard())
        authorize(1);runCurrent();assertFalse(guard());authorize(2);runCurrent();assertFalse(guard());assertNotEquals(generation,vm.state.value.selectionGeneration)
        finish.complete(Result.failure(IOException("old intro save")));runCurrent();assertFalse(vm.state.value.saveFailed);assertTrue(vm.state.value.introSoundEnabled)
    }
    @Test fun `current failure is visible and successful retry keeps confirmed state`()=runTest(dispatcher) {
        authorize(2);runCurrent();coEvery{store.selectIntroEnabled(any(),false,any())} returns Result.failure(IOException("fixture"))
        vm.selectIntroEnabled(false);runCurrent();assertTrue(vm.state.value.saveFailed);assertTrue(vm.state.value.introEnabled)
        coEvery{store.selectIntroEnabled(any(),false,any())} coAnswers {assertTrue(thirdArg<()->Boolean>()());reads.getValue(2).value=reads.getValue(2).value.copy(introEnabled=false);Result.success(Unit)}
        vm.selectIntroEnabled(false);runCurrent();assertFalse(vm.state.value.saveFailed);assertFalse(vm.state.value.introEnabled)
    }
    @Test fun `new profile rejects still visible old UI and cancellation stays cancellation`()=runTest(dispatcher) {
        authorize(2);runCurrent();authorize(1);vm.selectIntroSoundEnabled(false);runCurrent();coVerify(exactly=0){store.selectIntroSoundEnabled(any(),any(),any())}
        authorize(2);runCurrent();coEvery{store.selectIntroSoundEnabled(any(),false,any())} throws CancellationException("cancelled intro")
        vm.selectIntroSoundEnabled(false);runCurrent();assertFalse(vm.state.value.saveFailed);assertTrue(vm.state.value.introSoundEnabled)
    }
}
