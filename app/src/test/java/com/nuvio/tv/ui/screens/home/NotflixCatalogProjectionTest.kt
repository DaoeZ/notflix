package com.nuvio.tv.ui.screens.home

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.model.Collection
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class NotflixCatalogProjectionTest {
    private fun row(addon:String="a",title:String="Popular",type:ContentType=ContentType.MOVIE,phase:NotflixRowPhase=NotflixRowPhase.READY):NotflixCatalogRow {
        val item=MetaPreview(id="same",type=type,name="Synthetic $addon",poster=null,posterShape=PosterShape.POSTER,background=null,logo=null,description=null,releaseInfo=null,imdbRating=null,genres=emptyList())
        val data=CatalogRow(addon,"Fixture $addon","https://$addon.invalid","top",title,type,items=listOf(item))
        return NotflixCatalogRow(data.stableKey(),data.legacyKey(),"$title - Movie",phase,data)
    }
    private fun descriptor(row:NotflixCatalogRow,base:String=row.catalog!!.catalogName,type:String=row.catalog!!.apiType):HomeViewModel.PlaceholderDescriptor {
        val data=requireNotNull(row.catalog)
        return HomeViewModel.PlaceholderDescriptor(row.loadKey,data.addonId,data.addonName,data.addonBaseUrl,data.catalogId,base,type,row.title)
    }
    private fun project(rows:List<NotflixCatalogRow>,order:List<String> = rows.map{it.loadKey},data:List<CatalogRow> = rows.mapNotNull{it.catalog},
        disabled:Set<String> = emptySet(),titles:Map<String,String> = emptyMap(),collections:List<Collection> = emptyList(),
        descriptors:List<HomeViewModel.PlaceholderDescriptor> = rows.filter{it.catalog!=null}.map{descriptor(it)},suffix:Boolean=true,movie:String="Movie",series:String="Series") =
        projectNotflixCatalogs(NotflixCatalogSnapshot(2,7,true,rows),order,data,collections,disabled,titles,descriptors,suffix,movie,series)
    @Test fun reproduceLiveOverrideUpdatesProductRowHeadingWithoutChangingIdentity() {
        val catalog=CatalogRow("fixture","Fixture","https://fixture.invalid","movies","Popular",ContentType.MOVIE,items=emptyList())
        val key=catalog.legacyKey();val vm=mockk<HomeViewModel>(relaxed=true);val context=mockk<Context>()
        every{vm.disabledHomeCatalogKeys} returns emptySet()
        every{vm.customCatalogTitles} returns mapOf(key to "películas favoritas")
        every{vm.catalogStateLock} returns Any()
        every{vm.placeholderDescriptors} returns mutableListOf(HomeViewModel.PlaceholderDescriptor(key,"fixture","Fixture","https://fixture.invalid","movies","Popular","movie","Popular - Movie"))
        every{vm._uiState} returns MutableStateFlow(HomeUiState(catalogTypeSuffixEnabled=true))
        every{vm.appContext} returns context
        every{context.getString(R.string.type_movie)} returns "Movie"
        every{context.getString(R.string.type_series)} returns "Series"
        val row=NotflixCatalogRow(catalog.stableKey(),key,"Popular - Movie",NotflixRowPhase.READY,catalog)
        val before=NotflixCatalogSnapshot(2,7,true,listOf(row))
        val renamed=catalog.copy(catalogName="películas favoritas")
        val result=vm.notflixSnapshot(before,listOf(key),listOf(renamed),emptyList())
        assertEquals("Películas favoritas - Movie",result.rows.single().title)
        assertEquals(row.key,result.rows.single().key);assertEquals(row.loadKey,result.rows.single().loadKey)
        assertEquals(2,result.context);assertEquals(7L,result.generation)
    }
    @Test fun defaultTitleKeepsCapitalizationAndOptionalTypeSuffix() {
        val r=row(title="popular")
        assertEquals("Popular - Movie",project(listOf(r)).rows.single().title)
        assertEquals("Popular",project(listOf(r),suffix=false).rows.single().title)
    }
    @Test fun pendingWithoutPayloadCanUseCurrentOverrideAndOriginalDescriptor() {
        val r=row();val pending=r.copy(catalog=null,phase=NotflixRowPhase.PENDING)
        val result=project(listOf(pending),data=emptyList(),titles=mapOf(r.loadKey to "mi cine"),descriptors=listOf(descriptor(r)))
        assertEquals("Mi cine - Movie",result.rows.single().title);assertNull(result.rows.single().catalog)
        assertEquals(NotflixRowPhase.PENDING,result.rows.single().phase)
    }
    @Test fun removingOverrideDuringPendingRestoresOriginalNameRatherThanRetainedOverride() {
        val base=row();val renamed=base.copy(title="Old override - Movie",phase=NotflixRowPhase.PENDING,catalog=base.catalog!!.copy(catalogName="Old override"))
        assertEquals("Popular - Movie",project(listOf(renamed),data=emptyList(),descriptors=listOf(descriptor(base))).rows.single().title)
    }
    @Test fun retainedDataDoesNotOverwriteUpdatedHeadingOnPendingOrError() {
        val r=row();val previous=NotflixCatalogSnapshot(2,6,true,listOf(r))
        for(phase in listOf(NotflixRowPhase.PENDING,NotflixRowPhase.ERROR)) {
            val next=project(listOf(r.copy(phase=phase,catalog=null)),data=emptyList(),titles=mapOf(r.loadKey to "current name"),descriptors=listOf(descriptor(r)))
            val state=retainNotflixPresentation(HomeUiState(notflixCatalog=next),NotflixHomeMemory().apply{retainedCatalog=previous})
            assertEquals("Current name - Movie",state.notflixCatalog.rows.single().title)
            assertEquals(r.catalog,state.notflixCatalog.rows.single().catalog)
        }
    }
    @Test fun blankOverrideIsIgnoredAndUnicodeSpanishSuffixUsesSuppliedOfficialLabels() {
        val r=row(title="películas")
        assertEquals("Películas - Película",project(listOf(r),titles=mapOf(r.loadKey to "  "),movie="Película").rows.single().title)
        val s=row(type=ContentType.SERIES)
        assertEquals("Popular - Serie",project(listOf(s),series="Serie").rows.single().title)
    }
    @Test fun unknownCustomTypeAndMissingMetadataHaveDeterministicFallbacks() {
        val r=row();assertEquals("Popular - Anime",project(listOf(r),descriptors=listOf(descriptor(r,type="anime"))).rows.single().title)
        val pending=r.copy(catalog=null,title="Declared placeholder",phase=NotflixRowPhase.PENDING)
        assertEquals("Declared placeholder",project(listOf(pending),descriptors=emptyList()).rows.single().title)
    }
    @Test fun orderVisibilityAndRestorePreserveStableRowAndItemKeys() {
        val a=row();val b=row("b");val oldKeys=a.catalog!!.stableItemKeys()
        val sorted=project(listOf(a,b),order=listOf(b.loadKey,a.loadKey))
        assertEquals(listOf(b.key,a.key),sorted.rows.map{it.key})
        assertEquals(oldKeys,sorted.rows.last().catalog!!.stableItemKeys())
        val hidden=project(listOf(a,b),disabled=setOf(a.loadKey));assertEquals(listOf(b.key),hidden.rows.map{it.key})
        assertEquals(listOf(a.key,b.key),project(listOf(a,b)).rows.map{it.key})
    }
    @Test fun duplicateCatalogIdsAcrossAddonsNeverShareTitleOrData() {
        val a=row();val b=row("b",title="Other")
        val result=project(listOf(a,b),titles=mapOf(a.loadKey to "Only A"))
        assertEquals(listOf("Only A - Movie","Other - Movie"),result.rows.map{it.title})
        assertEquals(listOf("Synthetic a","Synthetic b"),result.rows.map{it.catalog!!.items.single().name})
        assertNotEquals(result.rows[0].key,result.rows[1].key)
    }
    @Test fun pinnedCollectionsAppearOnceKeepFoldersAndHonorVisibility() {
        val a=row();val col=Collection("c","Own collection",pinToTop=true,folders=listOf(CollectionFolder("f","Folder")))
        val result=project(listOf(a),order=listOf(a.loadKey,"collection_c","collection_c"),collections=listOf(col))
        assertEquals(listOf("collection_c",a.loadKey),result.rows.map{it.loadKey})
        assertEquals(col,result.rows.first().collection)
        assertEquals(listOf(a.key),project(listOf(a),collections=listOf(col),disabled=setOf("collection_c")).rows.map{it.key})
    }
    @Test fun collectionRenameComesFromOfficialCollectionAndNotCatalogOverrideMap() {
        val col=Collection("c","Current collection",folders=listOf(CollectionFolder("f","Current folder")))
        val result=project(emptyList(),order=listOf("collection_c"),collections=listOf(col),titles=mapOf("collection_c" to "Catalog-only override"))
        assertEquals("Current collection",result.rows.single().title);assertEquals("Current folder",result.rows.single().collection!!.folders.single().title)
    }
    @Test fun allHiddenEmptyAndUnknownOrderNeverInventRowsOrDeleteHeroSources() {
        val a=row();val captured=NotflixCatalogSnapshot(2,7,true,listOf(a),a.catalog!!.items)
        val hidden=projectNotflixCatalogs(captured,listOf(a.loadKey,"unknown"),listOf(a.catalog!!),emptyList(),setOf(a.loadKey),emptyMap(),listOf(descriptor(a)),true,"Movie","Series")
        assertTrue(hidden.rows.isEmpty());assertEquals(captured.heroItems,hidden.heroItems);assertTrue(hidden.configured)
        assertTrue(project(emptyList()).rows.isEmpty())
    }
    @Test fun successfulEmptyRetiresPayloadWhilePendingAndUnconfiguredDoNotProveRemoval() {
        val a=row();val empty=project(listOf(a),data=listOf(a.catalog!!.copy(items=emptyList())))
        assertTrue(empty.rows.single().catalog!!.items.isEmpty())
        val old=NotflixCatalogSnapshot(2,6,true,listOf(a));val mem=NotflixHomeMemory().apply{retainedCatalog=old}
        val retained=retainNotflixPresentation(HomeUiState(notflixCatalog=NotflixCatalogSnapshot(2,7,false)),mem)
        assertEquals(a.catalog,retained.notflixCatalog.rows.single().catalog);assertEquals(NotflixRowPhase.PENDING,retained.notflixCatalog.rows.single().phase)
    }
    @Test fun removedFocusedRowUsesExistingDeterministicFallbackAndEmptyReturnsTop() {
        val a=row();val b=row("b");val saved=NotflixFocusRecord(a.key,a.catalog!!.stableItemKeys().single(),"movie:same",0,0)
        fun focus(s:NotflixCatalogSnapshot)=s.rows.map{r->NotflixFocusRow(r.key,r.phase,r.catalog!!.items.mapIndexed{i,m->NotflixFocusItem(r.catalog.stableItemKeys()[i],notflixMetaKey(m))})}
        assertEquals(NotflixFocusResolution.Item(0,0),resolveNotflixFocus(saved,focus(project(listOf(a,b),disabled=setOf(a.loadKey))),true))
        assertEquals(NotflixFocusResolution.Top,resolveNotflixFocus(saved,emptyList(),true))
    }
    @Test fun projectionPreservesContextGenerationAndLedgerStillRejectsStaleGeneration() {
        val a=row();val ledger=NotflixCatalogLedger();ledger.begin(8);ledger.configure(8,listOf(NotflixCatalogLedger.Descriptor(a.loadKey,a.title,a.key)))
        val old=NotflixCatalogSnapshot(2,7,true,listOf(a));val stale=ledger.snapshot(7,2,emptyMap(),old)
        assertFalse(stale.configured);assertEquals(old.rows,stale.rows)
        val next=project(listOf(a));assertEquals(2,next.context);assertEquals(7L,next.generation)
        assertEquals(NotflixFocusResolution.Wait,resolveNotflixFocus(NotflixFocusRecord(a.key,"x","x",0,0),emptyList(),false))
    }
    @Test fun newCatalogOnlyAppearsWhenOfficialCaptureContainsItsDescriptor() {
        val a=row();val b=row("b")
        assertEquals(listOf(a.key),project(listOf(a),order=listOf(a.loadKey,b.loadKey),data=listOf(a.catalog!!,b.catalog!!)).rows.map{it.key})
        assertEquals(listOf(a.key,b.key),project(listOf(a,b)).rows.map{it.key})
    }
}
