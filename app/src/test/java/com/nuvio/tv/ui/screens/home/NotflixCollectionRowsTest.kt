package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.appearance.NotflixHomeCollectionPreset
import org.junit.Test
import org.junit.Assert.*

class NotflixCollectionRowsTest {
    private val addon=Addon("com.linvo.cinemeta","Cinemeta",version="1",description=null,logo=null,baseUrl="https://cinemeta.invalid",
        catalogs=listOf(CatalogDescriptor(ContentType.MOVIE,id="top",name="Renamed"),CatalogDescriptor(ContentType.SERIES,id="top",name="Renamed")),
        types=listOf(ContentType.MOVIE,ContentType.SERIES),resources=emptyList())
    private fun meta()=MetaPreview(id="tt1",type=ContentType.MOVIE,name="Title",poster=null,posterShape=PosterShape.POSTER,background=null,logo=null,description=null,releaseInfo=null,imdbRating=null,genres=emptyList())
    private fun row(id:String=addon.id,type:String="movie",base:String=addon.baseUrl)=CatalogRow(id,"Any title",base,"top","Not Popular",ContentType.fromString(type),rawType=type,items=listOf(meta()))
    private fun original(catalog:CatalogRow=row())=NotflixCatalogRow(catalog.stableKey(),catalog.legacyKey(),"Changed",NotflixRowPhase.READY,catalog)
    private fun projected(phase:NotflixRowPhase=NotflixRowPhase.READY,items:Boolean=true):NotflixCatalogRow {
        val spec=notflixCollectionSpecs(listOf(NotflixHomeCollectionPreset.create()),listOf(addon),emptySet()).first()
        return NotflixCatalogRow(spec.key,spec.key,spec.title,phase,if(items)row()else row().copy(items=emptyList()),folderTarget=spec.folder,sourceIdentity=spec.request)
    }
    @Test fun elevenDefinitionsUseSavedOrderTitlesAnd2026WithoutPresetRewrite() {
        val c=NotflixHomeCollectionPreset.create()
        val specs=notflixCollectionSpecs(listOf(c),listOf(addon),emptySet())
        assertEquals(c.folders.map{it.title},specs.map{it.title})
        assertEquals(11,specs.size);assertEquals(11,specs.map{it.key}.toSet().size)
        assertEquals(listOf("2026","2026"),specs.filter{it.source?.catalogId=="year"}.map{it.request!!.extraArgs["genre"]})
        val edited=c.copy(folders=c.folders.reversed().mapIndexed{i,f->if(i==0)f.copy(title="My edit")else f})
        assertEquals("My edit",notflixCollectionSpecs(listOf(edited),listOf(addon),emptySet()).first().title)
    }
    @Test fun hiddenCollectionAndOtherIdsDoNotProject() {
        val c=NotflixHomeCollectionPreset.create()
        assertTrue(notflixCollectionSpecs(listOf(c),listOf(addon),setOf("collection_notflix-inicio")).isEmpty())
        assertTrue(notflixCollectionSpecs(listOf(c.copy(id="other")),listOf(addon),emptySet()).isEmpty())
    }
    @Test fun multipleSourcesRemainOfficialFolderInsteadOfIgnoringOne() {
        val c=NotflixHomeCollectionPreset.create();val f=c.folders.first()
        val spec=notflixCollectionSpecs(listOf(c.copy(folders=listOf(f.copy(sources=f.sources+f.sources)))),listOf(addon),emptySet()).single()
        assertNull(spec.source);assertNull(spec.request);assertEquals(c.id to f.id,spec.folder)
    }
    @Test fun missingAddonRetainsFolderDestinationWithNoRequest() {
        val spec=notflixCollectionSpecs(listOf(NotflixHomeCollectionPreset.create()),emptyList(),emptySet()).first()
        assertNotNull(spec.source);assertNull(spec.request)
    }
    @Test fun suppressionUsesExactProviderBaseTypeCatalogAndUsableData() {
        val other=original(row(id="other"));val anotherUrl=original(row(base="https://other.invalid"));val series=original(row(type="series"))
        val own=original();val sub=projected()
        val (visible,aliases)=suppressReplacedPopular(listOf(other,anotherUrl,series,own,sub),emptyMap())
        assertEquals(listOf(other,anotherUrl,series,sub),visible)
        assertEquals(sub.key,aliases[own.key]);assertEquals(own.key,aliases[sub.key])
        val restored=suppressReplacedPopular(listOf(own),aliases)
        assertEquals(own.key,restored.second[sub.key])
    }
    @Test fun pendingOrFailedWithoutDataAndSuccessfulEmptyNeverHideOriginal() {
        val own=original()
        for(phase in NotflixRowPhase.entries) assertTrue(suppressReplacedPopular(listOf(own,projected(phase,false)),emptyMap()).first.contains(own))
        for(phase in listOf(NotflixRowPhase.PENDING,NotflixRowPhase.ERROR))
            assertFalse(suppressReplacedPopular(listOf(own,projected(phase,true)),emptyMap()).first.contains(own))
    }
    @Test fun editedGenreDoesNotSuppressUnfilteredPopular() {
        val own=original();val sub=projected().let{it.copy(sourceIdentity=it.sourceIdentity!!.copy(extraArgs=mapOf("genre" to "Action")))}
        assertTrue(suppressReplacedPopular(listOf(own,sub),emptyMap()).first.contains(own))
    }
    @Test fun changedSourceCannotRetainOldTitles() {
        val sub=projected();val memory=NotflixHomeMemory().apply{retainedCatalog=NotflixCatalogSnapshot(2,1,true,listOf(sub))}
        val changed=sub.copy(phase=NotflixRowPhase.PENDING,catalog=null,sourceIdentity=sub.sourceIdentity!!.copy(extraArgs=mapOf("genre" to "Family")))
        val state=retainNotflixPresentation(HomeUiState(notflixCatalog=NotflixCatalogSnapshot(2,1,true,listOf(changed))),memory)
        assertNull(state.notflixCatalog.rows.single().catalog)
        val same=changed.copy(sourceIdentity=sub.sourceIdentity)
        assertNotNull(retainNotflixPresentation(HomeUiState(notflixCatalog=NotflixCatalogSnapshot(2,1,true,listOf(same))),memory).notflixCatalog.rows.single().catalog)
    }
    @Test fun retainedTitlesSuppressOriginalDuringRefreshAndLateGeneration() {
        val sub=projected();val original=original()
        val memory=NotflixHomeMemory().apply { retainedCatalog=NotflixCatalogSnapshot(2,1,true,listOf(sub)) }
        val pending=sub.copy(phase=NotflixRowPhase.PENDING,catalog=null)
        val incoming=HomeUiState(notflixCatalog=NotflixCatalogSnapshot(2,2,true,listOf(pending,original)))
        val presented=retainNotflixPresentation(incoming,memory)
        assertEquals(listOf(sub.key),presented.notflixCatalog.rows.map{it.key})
        assertNotNull(presented.notflixCatalog.rows.single().catalog)
        assertEquals(sub.key,presented.notflixCatalog.focusAliases[original.key])
    }
    @Test fun authoritativeDeletionOrSourceEditRetiresOldTitlesWhileCatalogsStillConfigure() {
        val sub=projected()
        val memory=NotflixHomeMemory().apply { retainedCatalog=NotflixCatalogSnapshot(2,1,true,listOf(sub)) }
        val removed=HomeUiState(notflixCatalog=NotflixCatalogSnapshot(2,2,false,collectionRowsKnown=true))
        assertTrue(retainNotflixPresentation(removed,memory).notflixCatalog.rows.isEmpty())
        val edited=sub.copy(phase=NotflixRowPhase.PENDING,catalog=null,
            sourceIdentity=sub.sourceIdentity!!.copy(extraArgs=mapOf("genre" to "Action")))
        val incoming=HomeUiState(notflixCatalog=NotflixCatalogSnapshot(2,2,false,listOf(edited),collectionRowsKnown=true))
        val result=retainNotflixPresentation(incoming,memory)
        assertNull(result.notflixCatalog.rows.single().catalog)
        assertEquals(edited.sourceIdentity,result.notflixCatalog.rows.single().sourceIdentity)
    }
    @Test fun projectionRespectsPinOfficialOrderHiddenAndOtherCollectionFolders() {
        val c=NotflixHomeCollectionPreset.create();val other=c.copy(id="other",pinToTop=false)
        val specs=notflixCollectionSpecs(listOf(c),listOf(addon),emptySet())
        val projected=specs.map{NotflixCatalogRow(it.key,it.key,it.title,NotflixRowPhase.PENDING,folderTarget=it.folder,sourceIdentity=it.request)}
        val captured=NotflixCatalogSnapshot(2,1,true,listOf(original()))
        fun project(collection:Collection,disabled:Set<String> = emptySet())=projectNotflixCatalogs(captured,listOf(original().loadKey,"collection_other","collection_notflix-inicio"),listOf(row()),listOf(other,collection),disabled,emptyMap(),emptyList(),false,"Movie","Series",projected)
        assertEquals(projected.map{it.key},project(c).rows.take(11).map{it.key})
        assertNotNull(project(c).rows.last().collection)
        assertEquals(original().key,project(c.copy(pinToTop=false)).rows.first().key)
        val hidden=project(c,setOf("collection_notflix-inicio"))
        assertTrue(hidden.rows.none{it.folderTarget!=null})
        assertTrue(hidden.rows.any{it.key==original().key})
    }
}
