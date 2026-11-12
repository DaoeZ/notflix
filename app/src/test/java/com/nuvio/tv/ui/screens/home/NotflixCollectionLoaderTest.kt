package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class NotflixCollectionLoaderTest {
    private val scopeId=NotflixCollectionScope(2,1,1,false)
    private val addon=Addon("com.linvo.cinemeta","Fixture",version="1",description=null,logo=null,baseUrl="https://fixture.invalid",catalogs=emptyList(),types=listOf(ContentType.MOVIE),resources=emptyList())
    private fun item(id:String="tt1",release:String?=null)=MetaPreview(id,ContentType.MOVIE,name=id,poster=null,posterShape=PosterShape.POSTER,background=null,logo=null,description=null,releaseInfo=release,imdbRating=null,genres=emptyList())
    private fun row(items:List<MetaPreview> = listOf(item()))=CatalogRow(addon.id,addon.name,addon.baseUrl,"top","Top",ContentType.MOVIE,items=items)
    private fun spec(id:String="one",genre:String?=null):NotflixCollectionSpec {
        val source=AddonCatalogCollectionSource(addon.id,"movie","top",genre)
        return NotflixCollectionSpec("collection_notflix-inicio:$id",id,"notflix-inicio" to id,source,source.collectionCatalogRequest(listOf(addon)))
    }
    @Test fun configureElevenDoesNotLoadUntilRequestedAndRepeatedDemandDeduplicates()=runTest {
        var calls=0
        val loader=NotflixCollectionRows(this,{calls++;flowOf(NetworkResult.Success(row()))},{true},{})
        val specs=(1..11).map{spec("$it")}
        loader.configure(scopeId,specs);runCurrent();assertEquals(0,calls)
        repeat(20){loader.request(specs[0].key)};advanceUntilIdle()
        assertEquals(1,calls);assertEquals(1,loader.snapshot().count{it.catalog!=null})
        repeat(20){loader.request(specs[0].key)};advanceUntilIdle();assertEquals(1,calls)
    }
    @Test fun twoParallelRequestsAndQueuedCancellationAreBounded()=runTest {
        var active=0;var max=0;var calls=0;val gate=CompletableDeferred<Unit>()
        val loader=NotflixCollectionRows(this,{flow{calls++;active++;max=maxOf(max,active);try{gate.await();emit(NetworkResult.Success(row()))}finally{active--}}},{true},{})
        val specs=(1..11).map{spec("$it")};loader.configure(scopeId,specs)
        specs.forEach{loader.request(it.key)};runCurrent()
        assertEquals(2,calls);assertEquals(2,max)
        loader.release(specs[3].key);gate.complete(Unit);advanceUntilIdle()
        assertEquals(3,calls);assertTrue(max<=2)
        assertNull(loader.snapshot()[3].catalog)
    }
    @Test fun retiredAppearanceNonCooperativeResponseCannotPublishIntoANewRequest()=runTest {
        val gates=listOf(CompletableDeferred<Unit>(),CompletableDeferred<Unit>());var calls=0
        val loader=NotflixCollectionRows(this,{
            object:Flow<NetworkResult<CatalogRow>>{
                override suspend fun collect(collector:FlowCollector<NetworkResult<CatalogRow>>) {
                    val n=calls++
                    withContext(NonCancellable){gates[n].await();collector.emit(NetworkResult.Success(row(listOf(item("tt$n")))))}
                }
            }
        },{true},{})
        val s=spec();loader.configure(scopeId,listOf(s));loader.request(s.key);runCurrent()
        loader.configure(null,emptyList());loader.configure(scopeId.copy(visit=2),listOf(s))
        loader.request(s.key);runCurrent();assertEquals(2,calls)
        gates[0].complete(Unit);runCurrent();assertNull(loader.snapshot().single().catalog)
        gates[1].complete(Unit);advanceUntilIdle();assertEquals("tt1",loader.snapshot().single().catalog!!.items.single().id)
    }
    @Test fun hidingAnActiveRequestFinishesOneBoundedPageWithoutRecompositionDuplicates()=runTest {
        var calls=0;val gate=CompletableDeferred<Unit>()
        val loader=NotflixCollectionRows(this,{flow{calls++;gate.await();emit(NetworkResult.Success(row()))}},{true},{})
        val s=spec();loader.configure(scopeId,listOf(s));loader.request(s.key);runCurrent()
        repeat(20){loader.release(s.key);loader.request(s.key)}
        runCurrent();assertEquals(1,calls)
        gate.complete(Unit);advanceUntilIdle()
        assertEquals(1,calls);assertNotNull(loader.snapshot().single().catalog)
    }
    @Test fun profileOrAppearanceRevocationDiscardsLateData()=runTest {
        var current=true;val gate=CompletableDeferred<Unit>()
        val loader=NotflixCollectionRows(this,{flow{withContext(NonCancellable){gate.await()};emit(NetworkResult.Success(row()))}},{current},{})
        val s=spec();loader.configure(scopeId,listOf(s));loader.request(s.key);runCurrent()
        current=false;gate.complete(Unit);advanceUntilIdle();assertNull(loader.snapshot().single().catalog)
        loader.configure(null,emptyList());assertTrue(loader.snapshot().isEmpty())
    }
    @Test fun generationAndSourceChangesInvalidateCacheAndCancelOldWork()=runTest {
        var calls=0;val loader=NotflixCollectionRows(this,{calls++;flowOf(NetworkResult.Success(row()))},{true},{})
        val s=spec();loader.configure(scopeId,listOf(s));loader.request(s.key);advanceUntilIdle()
        loader.configure(scopeId.copy(generation=2),listOf(s));assertNull(loader.snapshot().single().catalog)
        loader.request(s.key);advanceUntilIdle()
        loader.configure(scopeId.copy(generation=2),listOf(spec(genre="Family")));assertNull(loader.snapshot().single().catalog)
        loader.request(s.key);advanceUntilIdle();assertEquals(3,calls)
    }
    @Test fun renameAndReorderPreserveCachedDataWithoutRequests()=runTest {
        var calls=0;val loader=NotflixCollectionRows(this,{calls++;flowOf(NetworkResult.Success(row()))},{true},{})
        val a=spec("a");val b=spec("b");loader.configure(scopeId,listOf(a,b));loader.request(a.key);advanceUntilIdle()
        loader.configure(scopeId,listOf(b,a.copy(title="Edited")))
        assertEquals(listOf("b","Edited"),loader.snapshot().map{it.title});assertNotNull(loader.snapshot()[1].catalog)
        loader.request(a.key);advanceUntilIdle();assertEquals(1,calls)
    }
    @Test fun failedRefreshRetainsDataButSuccessfulEmptyRetiresIt()=runTest {
        var result:NetworkResult<CatalogRow> = NetworkResult.Success(row())
        val loader=NotflixCollectionRows(this,{flowOf(result)},{true},{})
        val s=spec();loader.configure(scopeId,listOf(s));loader.request(s.key);advanceUntilIdle()
        result=NetworkResult.Error("fixture");loader.request(s.key,true);advanceUntilIdle()
        assertEquals(NotflixRowPhase.ERROR,loader.snapshot().single().phase);assertEquals(1,loader.snapshot().single().catalog!!.items.size)
        result=NetworkResult.Success(row(emptyList()));loader.request(s.key,true);advanceUntilIdle()
        assertEquals(NotflixRowPhase.READY,loader.snapshot().single().phase);assertTrue(loader.snapshot().single().catalog!!.items.isEmpty())
    }
    @Test fun throwingTransportIsAnErrorWithoutInventedDataOrAutomaticRetryLoop()=runTest {
        var calls=0;val loader=NotflixCollectionRows(this,{calls++;flow{throw java.io.IOException("fixture")}},{true},{})
        val s=spec();loader.configure(scopeId,listOf(s));loader.request(s.key);advanceUntilIdle()
        assertEquals(NotflixRowPhase.ERROR,loader.snapshot().single().phase);assertNull(loader.snapshot().single().catalog)
        repeat(20){loader.request(s.key)};advanceUntilIdle();assertEquals(1,calls)
    }
    @Test fun cacheItemsAndRowCountAreBoundedAndIdentitiesDeduplicate()=runTest {
        val items=(0..99).map{item("tt$it")}+item("tt0")
        val loader=NotflixCollectionRows(this,{flowOf(NetworkResult.Success(row(items)))},{true},{})
        val specs=(1..22).map{spec("$it")};loader.configure(scopeId,specs)
        specs.forEach{loader.request(it.key);advanceUntilIdle()}
        assertTrue(loader.snapshot().count{it.catalog!=null}<=NotflixCollectionRows.MAX_ROWS)
        assertTrue(loader.snapshot().mapNotNull{it.catalog}.all{it.items.size==NotflixCollectionRows.MAX_ITEMS && it.items.map{m->m.id}.toSet().size==it.items.size})
    }
    @Test fun acceptedCacheSurvivesHiddenHomeButNeverLeaksIntoAnotherProfile()=runTest {
        var calls=0;val loader=NotflixCollectionRows(this,{calls++;flowOf(NetworkResult.Success(row()))},{true},{})
        val s=spec();loader.configure(scopeId,listOf(s));loader.request(s.key);advanceUntilIdle()
        loader.configure(null,emptyList());assertTrue(loader.snapshot().isEmpty())
        loader.configure(scopeId.copy(visit=2),listOf(s));loader.request(s.key);advanceUntilIdle()
        assertEquals(1,calls);assertNotNull(loader.snapshot().single().catalog)
        loader.configure(scopeId.copy(profile=3,visit=3),listOf(s));assertNull(loader.snapshot().single().catalog)
    }
    @Test fun unsupportedAndMissingAddonsExposeOfficialDestinationWithoutNetwork()=runTest {
        var calls=0;val loader=NotflixCollectionRows(this,{calls++;flowOf(NetworkResult.Success(row()))},{true},{})
        val a=spec().copy(source=null,request=null);val b=spec("missing").copy(request=null)
        loader.configure(scopeId,listOf(a,b));loader.request(a.key);loader.request(b.key);advanceUntilIdle()
        assertEquals(0,calls);assertEquals(listOf(NotflixRowPhase.READY,NotflixRowPhase.ERROR),loader.snapshot().map{it.phase})
        assertTrue(loader.snapshot().all{it.folderTarget!=null})
    }
    @Test fun profileChangeWhileTheAgeFilterSuspendsCannotPublishIntoTheNewKidsRows() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var filters = 0
        val loader = NotflixCollectionRows(this, {
            object : Flow<NetworkResult<CatalogRow>> {
                override suspend fun collect(collector: FlowCollector<NetworkResult<CatalogRow>>) {
                    val next = row(listOf(item("profile-${++calls}")))
                    withContext(NonCancellable) { collector.emit(NetworkResult.Success(next)) }
                }
            }
        }, { true }, {}, filterRow = { row ->
            if (++filters == 1) gate.await()
            row
        })
        val s = spec("kids-shared-id")
        try {
            loader.configure(scopeId.copy(ageLimit = com.nuvio.tv.core.profile.AgeLevel.TP), listOf(s))
            loader.request(s.key); runCurrent()
            loader.configure(scopeId.copy(profile = 3, ageLimit = com.nuvio.tv.core.profile.AgeLevel.TP), listOf(s))
            loader.request(s.key); runCurrent()
            assertEquals("profile-2", loader.snapshot().single().catalog!!.items.single().id)
            gate.complete(Unit); advanceUntilIdle()
            assertEquals("profile-2", loader.snapshot().single().catalog!!.items.single().id)
        } finally {
            gate.complete(Unit)
        }
    }
}
