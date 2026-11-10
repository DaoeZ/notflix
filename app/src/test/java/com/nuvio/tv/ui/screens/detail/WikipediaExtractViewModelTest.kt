package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.data.wikipedia.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WikipediaExtractViewModelTest {
    private val store=mockk<NotflixAppearanceStore>().apply{every{isCurrent(any())} returns true}
    @Test fun closedOrUnavailableGateDoesNotReadCacheOrFetch()=runTest {
        var calls=0
        val repo=object:WikipediaExtractRepository {override val available=true;override suspend fun find(binding:WikipediaBinding,authorized:()->Boolean):WikipediaExtract?{calls++;return wikipediaFixture()}}
        val vm=WikipediaExtractViewModel(repo,store)
        val job=launch{vm.show(wikipediaBinding()){false}};runCurrent();job.join()
        assertEquals(0,calls);assertNull(vm.state.value.extract)
        val unavailable=object:WikipediaExtractRepository{override val available=false;override suspend fun find(binding:WikipediaBinding,authorized:()->Boolean):WikipediaExtract?{calls++;return null}}
        WikipediaExtractViewModel(unavailable,store).show(wikipediaBinding()){true};assertEquals(0,calls)
    }
    @Test fun completedResultRemainsOnlyDuringTheResumedVisitAndRevokeCancelsOwner()=runTest {
        val repo=object:WikipediaExtractRepository{override val available=true;override suspend fun find(binding:WikipediaBinding,authorized:()->Boolean)=wikipediaFixture()}
        val vm=WikipediaExtractViewModel(repo,store);val job=launch{vm.show(wikipediaBinding()){true}};runCurrent()
        assertEquals(wikipediaFixture(),vm.state.value.extract);vm.revoke();runCurrent()
        assertTrue(job.isCancelled);assertNull(vm.state.value.extract)
    }
    @Test fun lateNonCooperativeOldResultCannotPublishOrClearNewProfile()=runTest {
        val gate=CompletableDeferred<Unit>()
        val repo=object:WikipediaExtractRepository{override val available=true;override suspend fun find(binding:WikipediaBinding,authorized:()->Boolean):WikipediaExtract? {
            if(binding.context.profileId==2) withContext(NonCancellable){gate.await()}
            return wikipediaFixture(binding.identity.imdbId)
        }}
        val vm=WikipediaExtractViewModel(repo,store)
        val old=launch{vm.show(wikipediaBinding()){true}};runCurrent()
        val next=wikipediaBinding("tt0000002",1,2);val newer=launch{vm.show(next){true}};runCurrent()
        assertEquals(next,vm.state.value.binding);gate.complete(Unit);runCurrent();old.join()
        assertEquals(next,vm.state.value.binding);assertEquals("tt0000002",vm.state.value.extract!!.imdbId)
        newer.cancelAndJoin();assertNull(vm.state.value.extract)
    }
    @Test fun revokedContextOrToggleBeforeCompletionDiscardsResult()=runTest {
        val gate=CompletableDeferred<Unit>();var enabled=true
        val repo=object:WikipediaExtractRepository{override val available=true;override suspend fun find(binding:WikipediaBinding,authorized:()->Boolean):WikipediaExtract?{gate.await();return wikipediaFixture()}}
        val vm=WikipediaExtractViewModel(repo,store);val job=launch{vm.show(wikipediaBinding()){enabled}};runCurrent()
        enabled=false;gate.complete(Unit);runCurrent();assertNull(vm.state.value.extract);job.cancelAndJoin()
        every{store.isCurrent(any())} returns false
        vm.show(wikipediaBinding()){true};assertNull(vm.state.value.extract)
    }
    @Test fun navigatingAwayCancelsInFlightWorkWithoutPublishingAPlaceholder()=runTest {
        var cancelled=0
        val repo=object:WikipediaExtractRepository{override val available=true;override suspend fun find(binding:WikipediaBinding,authorized:()->Boolean):WikipediaExtract? {
            try{awaitCancellation()}finally{cancelled++}
        }}
        val vm=WikipediaExtractViewModel(repo,store);val job=launch{vm.show(wikipediaBinding()){true}};runCurrent()
        assertNull(vm.state.value.extract);job.cancelAndJoin();assertEquals(1,cancelled);assertNull(vm.state.value.extract)
    }
}
