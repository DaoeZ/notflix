package com.nuvio.tv.ui.screens.home

import androidx.compose.runtime.mutableStateMapOf
import com.nuvio.tv.domain.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Only the official VM boundary is replaced. No network/provider is invoked by this test. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotflixPreviewResolverTest {
    private val dispatcher=StandardTestDispatcher()
    private val model=mockk<HomeViewModel>()
    private val ui=MutableStateFlow(HomeUiState(notflixPresentationActive=true))
    private val loading=mutableSetOf<String>();private val negative=mutableSetOf<String>()
    private val urls=mutableStateMapOf<String,String>();private val audio=mutableStateMapOf<String,String>()
    private var version=0L;private var active:String?=null;private var job:Job?=null;private var calls=0
    private var request:(MetaPreview)->Unit={}
    private val binding=PreviewBinding(AppearanceContext(2,7),11)
    private fun lease(serial:Long=1,row:String="A"):PreviewLease {
        val meta=MetaPreview("id",ContentType.MOVIE,name="Synthetic",poster=null,posterShape=PosterShape.POSTER,
            background=null,logo=null,description=null,releaseInfo=null,imdbRating=null,genres=emptyList())
        return PreviewLease(PreviewVisit(binding,"$row|id","movie:id",serial),serial,meta,false)
    }
    @Before fun setup(){
        Dispatchers.setMain(dispatcher)
        every{model._uiState} returns ui
        every{model.trailerPreviewRequestVersion} answers{version}
        every{model.trailerPreviewRequestVersion=any()} answers{version=firstArg();Unit}
        every{model.activeTrailerPreviewItemId} answers{active}
        every{model.activeTrailerPreviewItemId=any()} answers{active=firstArg();Unit}
        every{model.trailerPreviewJob} answers{job}
        every{model.trailerPreviewLoadingIds} returns loading
        every{model.trailerPreviewNegativeCache} returns negative
        every{model.trailerPreviewUrlsState} returns urls
        every{model.trailerPreviewAudioUrlsState} returns audio
        every{model.requestTrailerPreview(any<MetaPreview>())} answers{calls++;request(firstArg())}
    }
    @After fun cleanup(){job?.cancel();Dispatchers.resetMain()}

    @Test fun `closed presentation or revoked authorization never delegates`()=runTest(dispatcher) {
        val r=OfficialNotflixPreviewResolver(model)
        assertNull(r.resolve(lease()){false});ui.value=ui.value.copy(notflixPresentationActive=false)
        assertNull(r.resolve(lease()){true});assertEquals(0,calls)
    }
    @Test fun `official startup feature or server guard leaves foreign job unclaimed`()=runTest(dispatcher) {
        val foreign=backgroundScope.launch{awaitCancellation()};job=foreign
        request={} // Official early return: no version claimed.
        val r=OfficialNotflixPreviewResolver(model);assertNull(r.resolve(lease()){true});r.cancel(lease());runCurrent()
        assertTrue(foreign.isActive);assertEquals(0L,version);assertEquals(1,calls)
    }
    @Test fun `cached official video and audio are passed unchanged without another resolver`()=runTest(dispatcher) {
        urls["id"]="https://unit.invalid/video";audio["id"]="https://unit.invalid/audio"
        request={version++;active=it.id}
        val r=OfficialNotflixPreviewResolver(model);val source=r.resolve(lease()){true}
        assertEquals(PreviewSource(urls.getValue("id"),audio.getValue("id")),source);assertEquals(1,calls);assertNull(active)
        r.cancel(lease());assertEquals("https://unit.invalid/video",urls["id"]);assertEquals(2L,version)
    }
    @Test fun `joins only its official loading job and clears the enrichment retry hint`()=runTest(dispatcher) {
        val done=CompletableDeferred<Unit>()
        request={meta->version++;active=meta.id;loading+=meta.id;job=backgroundScope.launch{done.await();urls[meta.id]="https://unit.invalid/resolved";loading-=meta.id}}
        val r=OfficialNotflixPreviewResolver(model);val result=async{r.resolve(lease()){true}};runCurrent()
        assertFalse(result.isCompleted);assertNull(active);assertEquals(1,calls)
        done.complete(Unit);runCurrent();assertEquals("https://unit.invalid/resolved",result.await()?.video)
    }
    @Test fun `cancel owns its pending job and late authorization cannot return a source`()=runTest(dispatcher) {
        val done=CompletableDeferred<Unit>();var authorized=true
        request={meta->version++;active=meta.id;loading+=meta.id;job=backgroundScope.launch{done.await();urls[meta.id]="https://unit.invalid/late"}}
        val r=OfficialNotflixPreviewResolver(model);val first=lease();val result=async{r.resolve(first){authorized}};runCurrent()
        authorized=false;r.cancel(first);runCurrent();assertTrue(requireNotNull(job).isCancelled);assertNull(result.await());assertTrue(urls.isEmpty())
    }
    @Test fun `newer official version cannot be cancelled even if the item id is reused`()=runTest(dispatcher) {
        request={meta->version++;active=meta.id;loading+=meta.id;job=backgroundScope.launch{awaitCancellation()}}
        val r=OfficialNotflixPreviewResolver(model);val old=lease();val result=async{r.resolve(old){true}};runCurrent();val oldJob=job
        oldJob?.cancel();version++;active="id";val next=backgroundScope.launch{awaitCancellation()};job=next
        r.cancel(old);runCurrent();assertTrue(next.isActive);assertEquals("id",active);assertNull(result.await())
    }
    @Test fun `old cancellation cannot steal a new row owner with the same canonical id`()=runTest(dispatcher) {
        val done=CompletableDeferred<Unit>()
        request={meta->version++;active=meta.id;loading+=meta.id;job=backgroundScope.launch{done.await();urls[meta.id]="https://unit.invalid/current";loading-=meta.id}}
        val r=OfficialNotflixPreviewResolver(model);val first=lease();val a=async{r.resolve(first){true}};runCurrent();r.cancel(first);runCurrent();assertNull(a.await())
        val next=lease(2,"B");val b=async{r.resolve(next){true}};runCurrent();val secondJob=requireNotNull(job)
        r.cancel(first);assertTrue(secondJob.isActive);done.complete(Unit);runCurrent();assertEquals("https://unit.invalid/current",b.await()?.video)
    }
    @Test fun `revoked guard discards an official completion without changing its cached data`()=runTest(dispatcher) {
        val done=CompletableDeferred<Unit>();var authorized=true
        request={meta->version++;active=meta.id;loading+=meta.id;job=backgroundScope.launch{done.await();urls[meta.id]="https://unit.invalid/cache";loading-=meta.id}}
        val r=OfficialNotflixPreviewResolver(model);val result=async{r.resolve(lease()){authorized}};runCurrent();authorized=false;done.complete(Unit);runCurrent()
        assertNull(result.await());assertEquals("https://unit.invalid/cache",urls["id"])
    }
}
