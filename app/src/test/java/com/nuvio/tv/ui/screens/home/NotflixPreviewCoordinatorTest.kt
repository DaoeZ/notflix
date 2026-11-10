package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotflixPreviewCoordinatorTest {
    private val binding=PreviewBinding(AppearanceContext(2,7),11)
    private fun item(id:String="same")=MetaPreview(id,ContentType.MOVIE,name="Title $id",poster=null,
        posterShape=PosterShape.POSTER,background=null,logo=null,description=null,releaseInfo=null,imdbRating=null,genres=emptyList())
    private fun candidate(row:String="rowA",epoch:Long=1,expanded:Boolean=true)=
        PreviewCandidate(PreviewVisit(binding,"$row|same","movie:same",epoch),item(),expanded)
    private fun enabled()=PreviewEnvironment(binding,true,false,true,false,true,true,false)
    private class Resolver:NotflixPreviewResolver {
        val calls=mutableListOf<PreviewLease>();val cancelled=mutableListOf<PreviewLease>()
        var result:suspend(PreviewLease)->PreviewSource?={PreviewSource("https://unit.invalid/${it.serial}.mp4")}
        override suspend fun resolve(lease:PreviewLease,authorized:()->Boolean):PreviewSource? {check(authorized());calls+=lease;return result(lease)}
        override fun cancel(lease:PreviewLease){cancelled+=lease}
    }
    private fun TestScope.controller(r:Resolver)=NotflixPreviewCoordinator(backgroundScope,r).also{it.updateEnvironment(enabled())}
    private fun TestScope.dwell(){advanceTimeBy(600);runCurrent()}
    private fun lease(c:NotflixPreviewCoordinator)=requireNotNull(c.state.value.lease())

    @Test fun `dwell and expansion are both required before the official request`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate(expanded=false));runCurrent()
        advanceTimeBy(599);runCurrent();assertTrue(r.calls.isEmpty());advanceTimeBy(1);runCurrent();assertTrue(r.calls.isEmpty())
        c.updateCandidate(candidate());runCurrent();assertEquals(1,r.calls.size);assertTrue(c.state.value is PreviewPhase.Playing)
    }
    @Test fun `focus sweep cancels dwell without any resolution or decoder owner`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());advanceTimeBy(590);c.updateCandidate(null);dwell()
        assertTrue(r.calls.isEmpty());assertEquals(PreviewPhase.Idle,c.state.value)
    }
    @Test fun `retained expansion still waits for a real new focus visit`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();c.updateCandidate(null)
        c.updateCandidate(candidate(epoch=2));advanceTimeBy(599);runCurrent();assertEquals(1,r.calls.size)
        advanceTimeBy(1);runCurrent();assertEquals(2,r.calls.size)
    }
    @Test fun `same id in another row has its own visit and releases the first owner`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val first=lease(c);var closed=0;c.registerCleanup(first){closed++}
        c.updateCandidate(candidate("rowB"));assertEquals(1,closed);assertEquals(listOf(first),r.cancelled);dwell()
        assertEquals(2,r.calls.size);assertEquals("rowB|same",lease(c).visit.identity)
    }
    @Test fun `recomposition or metadata enrichment cannot retry a spent visit`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val first=lease(c)
        repeat(20){c.updateCandidate(candidate().copy(item=item().copy(name="Enriched $it")));c.updateEnvironment(enabled())};runCurrent()
        assertEquals(1,r.calls.size);c.finish(first,PreviewEnd.COMPLETE)
        repeat(20){c.updateCandidate(candidate());c.updateEnvironment(enabled())};dwell();assertEquals(1,r.calls.size)
    }
    @Test fun `every disabled or uncertain host guard avoids resolution`()=runTest {
        val variants=listOf(enabled().copy(binding=null),enabled().copy(enabled=false),enabled().copy(valid=false),
            enabled().copy(reduced=true),enabled().copy(resumed=false),enabled().copy(windowFocused=false),
            enabled().copy(introActive=true),enabled().copy(blocked=true),enabled().copy(otherVideo=true))
        for(env in variants){val r=Resolver();val c=controller(r);c.updateEnvironment(env);c.updateCandidate(candidate());dwell();assertTrue(r.calls.isEmpty());c.dispose()}
    }
    @Test fun `background dialogue intro or other video closes once and does not resume same visit`()=runTest {
        for(env in listOf(enabled().copy(resumed=false),enabled().copy(windowFocused=false),enabled().copy(introActive=true),enabled().copy(blocked=true),enabled().copy(otherVideo=true))) {
            val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();var closed=0;c.registerCleanup(lease(c)){closed++}
            c.updateEnvironment(env);c.updateEnvironment(enabled());dwell();assertEquals(1,closed);assertEquals(1,r.calls.size);c.dispose()
        }
    }
    @Test fun `profile storage or confirmation revocation never inherits a late source`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val old=lease(c)
        val next=enabled().copy(binding=PreviewBinding(AppearanceContext(2,8),12))
        c.updateEnvironment(next);assertFalse(c.isCurrent(old));c.updateCandidate(candidate());dwell();assertEquals(1,r.calls.size)
        c.updateEnvironment(enabled());dwell();assertEquals(1,r.calls.size)
        c.updateCandidate(candidate(epoch=2));dwell();assertEquals(2,r.calls.size)
    }
    @Test fun `noncooperative old result cannot replace a newer row source`()=runTest {
        val r=Resolver();val late=CompletableDeferred<PreviewSource?>()
        r.result={if(it.serial==1L)withContext(NonCancellable){late.await()}else PreviewSource("https://unit.invalid/new.mp4")}
        val c=controller(r);c.updateCandidate(candidate());dwell();val old=lease(c)
        c.updateCandidate(candidate("new"));dwell();val current=lease(c);late.complete(PreviewSource("https://unit.invalid/late.mp4"));runCurrent()
        assertFalse(c.isCurrent(old));assertTrue(c.isCurrent(current));assertEquals("https://unit.invalid/new.mp4",(c.state.value as PreviewPhase.Playing).source.video)
    }
    @Test fun `absent error invalid and complete are terminal without a retry loop`()=runTest {
        for(source in listOf(null,PreviewSource("file:///private.mp4"),PreviewSource("javascript:bad"),PreviewSource("https://unit.invalid/a","not-a-url"))) {
            val r=Resolver().apply{result={source}};val c=controller(r);c.updateCandidate(candidate());dwell()
            assertEquals(PreviewEnd.ABSENT,(c.state.value as PreviewPhase.Finished).reason);c.updateCandidate(candidate());dwell();assertEquals(1,r.calls.size);c.dispose()
        }
        val r=Resolver().apply{result={throw IllegalStateException("synthetic resolver failure")}};val c=controller(r);c.updateCandidate(candidate());dwell()
        assertEquals(PreviewEnd.ERROR,(c.state.value as PreviewPhase.Finished).reason)
    }
    @Test fun `cleanup completion and late callbacks do not affect the next lease`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val first=lease(c);var closes=0;c.registerCleanup(first){closes++}
        assertTrue(c.finish(first,PreviewEnd.COMPLETE));assertFalse(c.finish(first,PreviewEnd.ERROR));assertEquals(1,closes)
        c.updateCandidate(candidate("next"));dwell();val next=lease(c);c.registerCleanup(first){closes++};c.firstFrame(first);c.finish(first,PreviewEnd.ERROR)
        assertTrue(c.isCurrent(next));assertEquals(2,closes)
    }
    @Test fun `navigation cancels immediately even when focus has not moved yet`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();var closes=0;c.registerCleanup(lease(c)){closes++}
        c.cancelVisit();assertEquals(1,closes);c.updateCandidate(candidate());dwell();assertEquals(1,r.calls.size)
    }
    @Test fun `audio progress cannot hide a missing first video frame indefinitely`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val current=lease(c)
        repeat(16){c.progress(current,it*1000L);advanceTimeBy(1000);runCurrent()}
        assertEquals(PreviewEnd.TIMEOUT,(c.state.value as PreviewPhase.Finished).reason)
    }
    @Test fun `first frame and real progress reset stall guard but frozen position does not`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val current=lease(c);c.firstFrame(current)
        advanceTimeBy(14_000);c.progress(current,1000);advanceTimeBy(14_000);runCurrent();assertTrue(c.isCurrent(current))
        c.progress(current,1000);advanceTimeBy(1001);runCurrent();assertFalse(c.isCurrent(current))
    }
    @Test fun `sound flag changes cancel current visit without a surprise restart`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell()
        // Sound does not require another network lookup during the existing video.
        c.updateEnvironment(enabled().copy(sound=true));c.updateCandidate(candidate());dwell();assertEquals(1,r.calls.size)
        c.updateCandidate(candidate(epoch=2));dwell();assertTrue(lease(c).sound)
    }
    @Test fun `dispose or throwing cleanup cannot retain an active owner`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val first=lease(c)
        c.registerCleanup(first){throw IllegalStateException("synthetic cleanup")};c.dispose();c.dispose();assertFalse(c.isCurrent(first));assertTrue(c.cleanupFailed)
        c.updateCandidate(candidate(epoch=2));dwell();assertEquals(1,r.calls.size)
    }
    @Test fun `visit sound toggle needs a playing lease and never resolves restarts or outlives it`()=runTest {
        val r=Resolver().apply{val gate=CompletableDeferred<PreviewSource?>();result={gate.await()}}
        val c=controller(r);c.updateCandidate(candidate());dwell();val resolving=lease(c)
        assertFalse(c.setAudible(resolving,true));assertFalse(c.audible(resolving))
        r.result={PreviewSource("https://unit.invalid/${it.serial}.mp4")}
        c.updateCandidate(candidate(epoch=2));dwell();val playing=lease(c);assertTrue(c.state.value is PreviewPhase.Playing)
        assertTrue(c.setAudible(playing,true));assertTrue(c.audible(playing));assertEquals(playing.serial to true,c.audibleOverride.value)
        assertTrue(c.setAudible(playing,false));assertFalse(c.audible(playing))
        assertTrue(c.setAudible(playing,true));assertTrue(c.isCurrent(playing));assertEquals(2,r.calls.size)
        // Leaving the card drops the choice; the next visit starts from the official profile value again.
        c.updateCandidate(candidate("rowB"));assertNull(c.audibleOverride.value);assertFalse(c.setAudible(playing,false))
        dwell();val next=lease(c);assertFalse(c.audible(next));assertEquals(3,r.calls.size)
    }
    @Test fun `stale toggle of an earlier lease cannot change the current one`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val first=lease(c)
        c.updateCandidate(candidate(epoch=2));dwell();val second=lease(c)
        assertFalse(c.setAudible(first,true));assertFalse(c.audible(second));assertNull(c.audibleOverride.value)
        c.dispose();assertFalse(c.setAudible(second,true))
    }
    @Test fun `returning from the big view starts the same identity without a new dwell`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();val first=lease(c);var closed=0
        c.registerCleanup(first){closed++};c.cancelVisit();c.updateCandidate(null)
        assertEquals(1,closed);assertEquals(PreviewPhase.Idle,c.state.value)
        c.restartVisit("rowA|same");c.updateCandidate(candidate(epoch=2));runCurrent()
        assertEquals(2,r.calls.size);assertTrue(c.state.value is PreviewPhase.Playing)
    }
    @Test fun `finished big view keeps the returning card static until another visit`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();c.cancelVisit();c.updateCandidate(null)
        c.suppressNextVisit("rowA|same");c.updateCandidate(candidate(epoch=2));runCurrent();dwell()
        assertEquals(1,r.calls.size);assertEquals(PreviewPhase.Idle,c.state.value)
        // Leaving and returning is a new visit and may play again.
        c.updateCandidate(null);c.updateCandidate(candidate(epoch=3));dwell()
        assertEquals(2,r.calls.size)
    }
    @Test fun `restart for another identity keeps the normal dwell`()=runTest {
        val r=Resolver();val c=controller(r);c.updateCandidate(candidate());dwell();c.cancelVisit();c.updateCandidate(null)
        c.restartVisit("rowB|same");c.updateCandidate(candidate(epoch=2))
        advanceTimeBy(599);runCurrent();assertEquals(1,r.calls.size)
        advanceTimeBy(1);runCurrent();assertEquals(2,r.calls.size)
    }
    @Test fun `preview sound never defeats local disabled or system silence`() {
        assertFalse(previewShouldMute(true,false,false,false))
        for(sound in listOf(false,true))for(media in listOf(false,true))for(ringer in listOf(false,true))for(interruption in listOf(false,true))
            if(!sound||media||ringer||interruption)assertTrue(previewShouldMute(sound,media,ringer,interruption))
    }
}
