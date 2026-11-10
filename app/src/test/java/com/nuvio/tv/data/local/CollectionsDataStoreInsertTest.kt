package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.gson.JsonParser
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.CollectionSyncService
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.ui.appearance.NotflixHomeCollectionPreset
import com.nuvio.tv.ui.screens.collection.CollectionEditorViewModel
import com.nuvio.tv.ui.screens.collection.CollectionManagementViewModel
import io.mockk.*
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Real official store, isolated files and jobs. No live profile, account or remote transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsDataStoreInsertTest {
    private val directory=Files.createTempDirectory("nf20-collections-").toFile()
    private val context=mockk<Context>(relaxed=true)
    private val manager=mockk<ProfileManager>()
    private val active=MutableStateFlow(2)
    private lateinit var factory:ProfileDataStoreFactory
    private lateinit var store:CollectionsDataStore
    private val owner=ViewModelStore()
    private val key=stringPreferencesKey("collections_json")
    private val preset get()=NotflixHomeCollectionPreset.create()
    @Before fun setup(){
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every{context.filesDir} returns directory;every{context.applicationContext} returns context
        every{manager.activeProfileId} returns active
        every{manager.activeProfile} answers{UserProfile(active.value,"Synthetic","#000000",usesPrimaryAddons=true)}
        factory=ProfileDataStoreFactory(context);store=CollectionsDataStore(context,factory,manager)
    }
    private suspend fun closeFactory(){
        val cache=ProfileDataStoreFactory::class.java.getDeclaredField("cache").apply{isAccessible=true}.get(factory) as MutableMap<*,*>
        cache.values.toList().forEach{value->(value!!::class.java.getDeclaredField("job").apply{isAccessible=true}.get(value) as Job).cancelAndJoin()};cache.clear()
    }
    @After fun close()=runBlocking{owner.clear();closeFactory();Dispatchers.resetMain()}
    private suspend fun raw()=factory.get(2,"collections").data.first()[key]
    @Test fun emptyStoreAtomicallyCreatesElevenOfficialFoldersAndReopens()=runBlocking{
        assertEquals(CollectionInsertResult.Inserted,store.insertCollectionIfAbsent(2,preset){true})
        assertEquals(preset,store.getCurrentCollections().single())
        closeFactory();factory=ProfileDataStoreFactory(context);store=CollectionsDataStore(context,factory,manager)
        assertEquals(preset,store.getCurrentCollections().single())
    }
    @Test fun concurrentInstancesNeverDuplicate()=runBlocking{
        val results=coroutineScope{(1..32).map{async(Dispatchers.Default){CollectionsDataStore(context,factory,manager).insertCollectionIfAbsent(2,preset){true}}}.awaitAll()}
        assertEquals(1,results.count{it==CollectionInsertResult.Inserted})
        assertEquals(31,results.count{it is CollectionInsertResult.Exists})
        assertEquals(listOf(preset),store.getCurrentCollections())
    }
    @Test fun idCollisionPreservesUserEditsAndEntireRawValue()=runBlocking{
        val edited=preset.copy(title="Mi inicio editado",pinToTop=false,folders=preset.folders.reversed())
        store.addCollection(edited);val before=raw()
        assertTrue(store.insertCollectionIfAbsent(2,preset){true} is CollectionInsertResult.Exists)
        assertEquals(before,raw());assertEquals(edited,store.getCurrentCollections().single())
    }
    @Test fun titleCollisionNormalizesCaseWhitespaceAndUnicodeWithoutAdoptingId()=runBlocking{
        val other=Collection("user-owned","  INICIO\u00a0  NOTFLIX  ")
        store.addCollection(other);val before=raw()
        assertEquals(CollectionInsertResult.Exists("user-owned"),store.insertCollectionIfAbsent(2,preset){true})
        assertEquals(before,raw());assertEquals(other,store.getCurrentCollections().single())
    }
    @Test fun appendPreservesUnknownFieldsAndUnrelatedCollectionExactlyAsJsonObjects()=runBlocking{
        val before="""[{"id":"other","title":"Unrelated","folders":[],"futureField":{"keep":[1,2,3]}}]"""
        factory.get(2,"collections").edit{it[key]=before}
        assertEquals(CollectionInsertResult.Inserted,store.insertCollectionIfAbsent(2,preset){true})
        val parsed=JsonParser.parseString(raw()).asJsonArray
        assertEquals(JsonParser.parseString(before).asJsonArray[0],parsed[0]);assertEquals(2,parsed.size())
    }
    @Test fun malformedExistingJsonFailsClosedWithoutReplacingData()=runBlocking{
        for(value in listOf("not json","{}", "[null]", "[{\"id\":\"other\"}]")){
            factory.get(2,"collections").edit{it[key]=value}
            try{store.insertCollectionIfAbsent(2,preset){true};fail("Expected refusal")}catch(_:IOException){}
            assertEquals(value,raw())
        }
    }
    @Test fun invalidOrRevokedContextDoesNotObtainStoreOrWrite()=runBlocking{
        assertEquals(CollectionInsertResult.NotAuthorized,store.insertCollectionIfAbsent(2,preset){false})
        assertEquals(CollectionInsertResult.NotAuthorized,store.insertCollectionIfAbsent(7,preset){true})
        assertNull(raw())
    }
    private fun delayed(afterTransform:Boolean, entered:CompletableDeferred<Unit>,resume:CompletableDeferred<Unit>):CollectionsDataStore {
        val actual=factory.get(2,"collections")
        val wrapper=object:DataStore<Preferences>{
            override val data=actual.data
            override suspend fun updateData(transform:suspend (Preferences)->Preferences):Preferences =
                actual.updateData { before ->
                    if(afterTransform){val next=transform(before);entered.complete(Unit);resume.await();next}
                    else{entered.complete(Unit);resume.await();transform(before)}
                }
        }
        val bound=mockk<ProfileDataStoreFactory>();every{bound.get(2,"collections")} returns wrapper
        return CollectionsDataStore(context,bound,manager)
    }
    @Test fun revocationWhileWaitingForTransactionPreventsInsertion()=runBlocking{
        val entered=CompletableDeferred<Unit>();val resume=CompletableDeferred<Unit>();var valid=true
        val scoped=delayed(false,entered,resume)
        val result=async{scoped.insertCollectionIfAbsent(2,preset){valid}}
        entered.await();valid=false;active.value=3;resume.complete(Unit)
        assertEquals(CollectionInsertResult.NotAuthorized,result.await())
        assertNull(factory.get(2,"collections").data.first()[key]);assertTrue(store.getCurrentCollections().isEmpty())
    }
    @Test fun startedTransactionKeepsCapturedDestinationAfterProfileSwitch()=runBlocking{
        val entered=CompletableDeferred<Unit>();val resume=CompletableDeferred<Unit>()
        val scoped=delayed(true,entered,resume)
        val result=async{scoped.insertCollectionIfAbsent(2,preset){active.value==2}}
        entered.await();active.value=3;resume.complete(Unit)
        assertEquals(CollectionInsertResult.Inserted,result.await())
        assertTrue(store.getCurrentCollections().isEmpty())
        active.value=2;assertEquals(listOf(preset),store.getCurrentCollections())
        active.value=1;assertTrue(store.getCurrentCollections().isEmpty())
    }
    @Test fun ioFailureDoesNotPublishPartialValue()=runBlocking{
        val before=preferencesOf(key to "[{\"id\":\"other\",\"title\":\"Keep\",\"folders\":[]}]")
        val failing=object:DataStore<Preferences>{
            override val data=flowOf(before)
            override suspend fun updateData(transform:suspend (Preferences)->Preferences):Preferences{
                transform(before);throw IOException("Synthetic IO failure before commit")
            }
        }
        val bound=mockk<ProfileDataStoreFactory>();every{bound.get(2,"collections")} returns failing
        try{CollectionsDataStore(context,bound,manager).insertCollectionIfAbsent(2,preset){true};fail("Expected IO")}catch(_:IOException){}
        assertEquals(before,failing.data.first())
    }
    @Test fun officialEditorAndDeletePreserveAnotherCollectionAndRepeatedCreateKeepsEdits()=runBlocking{
        val other=Collection("other","Keep this",folders=listOf(CollectionFolder("f","Keep folder")))
        store.addCollection(other);store.insertCollectionIfAbsent(2,preset){true}
        val repo=mockk<AddonRepository>();every{repo.getInstalledAddons()} returns flowOf(emptyList())
        val sync=mockk<CollectionSyncService>(relaxed=true)
        val editor=CollectionEditorViewModel(context,SavedStateHandle(mapOf("collectionId" to preset.id)),store,repo,mockk(),mockk(),sync)
        owner.put("editor",editor)
        withTimeout(5000){editor.uiState.first{!it.isLoading}}
        assertFalse(editor.uiState.value.isNew);assertEquals(11,editor.uiState.value.folders.size)
        editor.setPinToTop(false);val saved=CompletableDeferred<Unit>();editor.save{saved.complete(Unit)};withTimeout(5000){saved.await()}
        val edited=store.getCurrentCollections().first{it.id==preset.id};assertFalse(edited.pinToTop)
        assertTrue(store.insertCollectionIfAbsent(2,preset){true} is CollectionInsertResult.Exists)
        assertEquals(edited,store.getCurrentCollections().first{it.id==preset.id})
        val management=CollectionManagementViewModel(store,sync,context);owner.put("management",management)
        management.deleteCollection(preset.id)
        withTimeout(5000){store.collections.first{it.size==1}}
        assertEquals(listOf(other),store.getCurrentCollections())
        verify(exactly=2){sync.triggerPush()} // Official editor/delete callbacks, simulated transport only.
    }
}
