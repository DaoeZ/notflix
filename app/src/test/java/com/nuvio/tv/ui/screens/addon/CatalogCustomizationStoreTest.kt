package com.nuvio.tv.ui.screens.addon

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.HomeCatalogSettingsSyncService
import com.nuvio.tv.data.local.*
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Actual official Layout/Collections DataStores and CatalogOrder VM; isolated files, no live sync/repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogCustomizationStoreTest {
    private val dir=Files.createTempDirectory("nf17-layout-").toFile()
    private val manager=mockk<ProfileManager>()
    private val active=MutableStateFlow(2)
    private val profiles=MutableStateFlow(listOf(UserProfile(1,"Synthetic primary","#000000"),UserProfile(2,"Synthetic own","#000000")))
    private val context=mockk<Context>(relaxed=true)
    private val installed=MutableStateFlow(listOf(addon("a"),addon("b")))
    private val sync=mockk<HomeCatalogSettingsSyncService>(relaxed=true)
    private var owner=ViewModelStore()
    private lateinit var factory:ProfileDataStoreFactory
    private lateinit var layout:LayoutPreferenceDataStore
    private lateinit var collections:CollectionsDataStore
    private lateinit var vm:CatalogOrderViewModel
    private fun addon(id:String)=Addon(id,id,version="fixture",description=null,logo=null,baseUrl="https://$id.invalid",
        catalogs=listOf(CatalogDescriptor(ContentType.MOVIE,id="top",name="$id top",showInHome=true),CatalogDescriptor(ContentType.SERIES,id="new",name="$id new",showInHome=true)),types=listOf(ContentType.MOVIE,ContentType.SERIES),resources=emptyList())
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every{context.filesDir} returns dir;every{context.applicationContext} returns context
        every{manager.activeProfileId} returns active;every{manager.profiles} returns profiles
        every{manager.activeProfile} answers{profiles.value.firstOrNull{it.id==active.value}}
        open()
    }
    private fun open() {
        factory=ProfileDataStoreFactory(context);layout=LayoutPreferenceDataStore(factory,manager);collections=CollectionsDataStore(context,factory,manager)
        val repo=mockk<AddonRepository>();every{repo.getInstalledAddons()} returns installed
        val servers=mockk<ServerCatalog>();every{servers.addons} returns MutableStateFlow(emptyList())
        vm=CatalogOrderViewModel(repo,collections,layout,sync,servers);owner.put("catalogs",vm)
    }
    private suspend fun awaitItems(count:Int=4,predicate:(CatalogOrderUiState)->Boolean={true}):CatalogOrderUiState =
        withTimeout(10_000){vm.uiState.first{!it.isLoading && it.items.size==count && predicate(it)}}
    private suspend fun close() {
        owner.clear()
        val scope=LayoutPreferenceDataStore::class.java.getDeclaredField("scope").apply{isAccessible=true}.get(layout) as CoroutineScope
        scope.coroutineContext[Job]?.cancelAndJoin()
        val cache=ProfileDataStoreFactory::class.java.getDeclaredField("cache").apply{isAccessible=true}.get(factory) as MutableMap<*,*>
        cache.values.toList().forEach{store->(store!!::class.java.getDeclaredField("job").apply{isAccessible=true}.get(store) as Job).cancelAndJoin()};cache.clear()
    }
    @After fun cleanup()=runBlocking {
        close();Dispatchers.resetMain()
        check(dir.canonicalFile.parentFile==File(System.getProperty("java.io.tmpdir")).canonicalFile && dir.name.startsWith("nf17-layout-"))
        dir.deleteRecursively();Unit
    }
    @Test fun officialMoveToggleAndReopenPersistOnlyInTemporaryOwnProfile()=runBlocking {
        val start=awaitItems();val first=start.items.first();val keys=start.items.map{it.key}
        vm.moveDown(first.key);awaitItems{it.items[1].key==first.key}
        vm.toggleCatalogEnabled(first.disableKey);awaitItems{it.items.first{r->r.key==first.key}.isDisabled}
        assertEquals(listOf(keys[1],keys[0])+keys.drop(2),layout.homeCatalogOrderKeys.first())
        close();owner=ViewModelStore();open()
        val reopened=awaitItems{it.items[1].key==first.key && it.items[1].isDisabled}
        assertEquals(first.key,reopened.items[1].key)
        vm.toggleCatalogEnabled(first.disableKey);awaitItems{!it.items.first{r->r.key==first.key}.isDisabled}
        vm.moveUp(first.key);assertEquals(keys,awaitItems{it.items.first().key==first.key}.items.map{it.key})
        assertTrue(layout.disabledHomeCatalogKeys.first().isEmpty());verify(atLeast=4){sync.triggerPush()}
        assertFalse(File(dir,"datastore/layout_settings.preferences_pb").exists())
    }
    @Test fun movementLimitsAndUnknownKeysDoNotWriteOrderOrPush()=runBlocking {
        val state=awaitItems();vm.moveUp(state.items.first().key);vm.moveDown(state.items.last().key);vm.moveDown("missing")
        assertTrue(layout.homeCatalogOrderKeys.first().isEmpty());verify(exactly=0){sync.triggerPush()}
        assertFalse(state.items.first().canMoveUp);assertFalse(state.items.last().canMoveDown)
    }
    @Test fun followAddonOrderDisablesIndividualMovesAndPreservesManifestOrder()=runBlocking {
        val start=awaitItems();layout.setHomeCatalogOrderKeys(start.items.map{it.key}.reversed())
        awaitItems{it.items.first().key==start.items.last().key}
        vm.toggleFollowAddonsOrder(true)
        val followed=awaitItems{it.followAddonsOrder};assertEquals(start.items.map{it.key},followed.items.map{it.key})
        assertTrue(followed.items.all{!it.canMoveUp && !it.canMoveDown})
        clearMocks(sync,answers=false);vm.moveDown(followed.items.first().key)
        assertEquals(start.items.map{it.key}.reversed(),layout.homeCatalogOrderKeys.first());verify(exactly=0){sync.triggerPush()}
    }
    @Test fun collectionMovesBetweenAddonBlocksInFollowModeAndPersistsFolders()=runBlocking {
        val start=awaitItems();val col=Collection("c","Synthetic collection",pinToTop=true,folders=listOf(CollectionFolder("f","Synthetic folder")))
        collections.setCollections(listOf(col));vm.toggleFollowAddonsOrder(true)
        val initial=awaitItems(5){it.followAddonsOrder};assertEquals("collection_c",initial.items.last().key)
        vm.moveUp("collection_c")
        val middle=awaitItems(5){it.items.indexOfFirst{r->r.key=="collection_c"}==2}
        assertEquals(listOf(start.items[0].key,start.items[1].key,"collection_c",start.items[2].key,start.items[3].key),middle.items.map{it.key})
        assertEquals(col,collections.collections.first().single());assertTrue(layout.followAddonsOrder.first())
        vm.moveUp("collection_c");awaitItems(5){it.items.first().key=="collection_c"}
        vm.moveUp("collection_c");assertEquals("collection_c",layout.homeCatalogOrderKeys.first().first())
    }
    @Test fun customTitleStoreAndOfficialOrderViewModelUseSameNamesWithoutAnEditor()=runBlocking<Unit> {
        val initial=awaitItems();val key=initial.items[0].key
        layout.setCustomCatalogTitles(mapOf(key to "Imported local title",initial.items[1].key to "  "))
        val updated=awaitItems{it.items.first().catalogName=="Imported local title"}
        assertEquals(mapOf(key to "Imported local title"),layout.customCatalogTitles.first())
        assertEquals(initial.items.drop(1).map{it.catalogName},updated.items.drop(1).map{it.catalogName})
        layout.setCustomCatalogTitles(emptyMap());awaitItems{it.items.first().catalogName==initial.items.first().catalogName}
    }
    @Test fun inheritedOrderVisibilityAndTitlesReadPrimaryWithoutMutatingIt()=runBlocking {
        awaitItems();active.value=1
        layout.setHomeCatalogOrderKeys(listOf("primary_movie_top"));layout.setDisabledHomeCatalogKeys(listOf("primary_movie_top"));layout.setCustomCatalogTitles(mapOf("primary_movie_top" to "Primary name"));layout.setFollowAddonsOrder(true)
        val primaryFile=File(dir,"datastore/layout_settings.preferences_pb");val before=primaryFile.readBytes()
        active.value=2;layout.setHomeCatalogOrderKeys(listOf("own_movie_top"));layout.setCustomCatalogTitles(mapOf("own_movie_top" to "Own name"));layout.setFollowAddonsOrder(false)
        profiles.value=profiles.value.map{if(it.id==2)it.copy(usesPrimaryAddons=true)else it};active.value=1;active.value=2
        assertEquals(listOf("primary_movie_top"),layout.homeCatalogOrderKeys.first())
        assertEquals(setOf("primary_movie_top"),layout.disabledHomeCatalogKeys.first().toSet())
        assertEquals(mapOf("primary_movie_top" to "Primary name"),layout.customCatalogTitles.first())
        assertFalse(layout.followAddonsOrder.first());assertArrayEquals(before,primaryFile.readBytes())
        assertTrue(AddonManagementAccess.isReadOnly(profiles.value.last()))
    }
    @Test fun ownProfileSwitchKeepsNamesOrderAndFollowFlagsSeparate()=runBlocking {
        awaitItems();layout.setCustomCatalogTitles(mapOf("a_movie_top" to "Own"));layout.setHomeCatalogOrderKeys(listOf("a_movie_top"));layout.setFollowAddonsOrder(true)
        active.value=1;assertTrue(layout.customCatalogTitles.first().isEmpty());assertTrue(layout.homeCatalogOrderKeys.first().isEmpty());assertFalse(layout.followAddonsOrder.first())
        active.value=2;assertEquals("Own",layout.customCatalogTitles.first()["a_movie_top"]);assertTrue(layout.followAddonsOrder.first())
    }
    @Test fun allDisabledAndDuplicateCatalogIdsRemainDistinctOfficialEntries()=runBlocking {
        val state=awaitItems();assertEquals(4,state.items.map{it.key}.distinct().size)
        for(item in state.items){vm.toggleCatalogEnabled(item.disableKey);awaitItems{it.items.first{r->r.key==item.key}.isDisabled}}
        val hidden=awaitItems{it.items.all{r->r.isDisabled}};assertEquals(4,hidden.items.size)
        assertTrue(hidden.items.any{it.addonName=="a"});assertTrue(hidden.items.any{it.addonName=="b"})
    }
}
