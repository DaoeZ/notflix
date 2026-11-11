package com.nuvio.tv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.appearance.*
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.junit.*
import org.junit.Assert.*

/** Actual Home renderer/shell/projection/lazy owner. Destinations are explicitly inert:
 * official FolderDetail/MetaDetails integration is a separate native trial, not faked here.
 */
@OptIn(ExperimentalTestApi::class)
class NotflixCollectionRowsComposeTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val owner=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val memory=NotflixHomeMemory()
    private val navigation=NotflixNavigationState()
    private val requests=CopyOnWriteArrayList<String>()
    private val gates=mutableMapOf<String,CompletableDeferred<Unit>>()
    private var itemsPerRow=3
    private var collection=notflixCollectionTestPreset()
    private lateinit var loader:NotflixCollectionRows
    private lateinit var nav:NavHostController
    private var ui by mutableStateOf(HomeUiState())
    private var destination="home"
    private var active=true
    private var visit=1L
    private var folderOpened:Pair<String,String>?=null
    private var detailOpened:String?=null
    private val addon=Addon("com.linvo.cinemeta","Synthetic",version="1",description=null,logo=null,baseUrl="https://example.invalid",
        catalogs=emptyList(),types=listOf(ContentType.MOVIE,ContentType.SERIES),resources=emptyList())
    private val context=NotflixCollectionScope(2,1,1,false)
    @After fun close(){instrumentation.runOnMainSync{owner.cancel()}}
    private fun signature(r:CollectionCatalogRequest)="${r.type}/${r.catalogId}/${r.extraArgs["genre"].orEmpty()}"
    private fun item(id:String,type:String)=MetaPreview(id,ContentType.fromString(type),name="Synthetic $id",poster=null,posterShape=PosterShape.POSTER,
        background=null,logo=null,description="Synthetic fixture",releaseInfo="2026",imdbRating=null,genres=emptyList())
    private fun publish(){
        val rows=loader.snapshot()
        ui=HomeUiState(layoutPreferencesReady=true,isLoading=false,heroSectionEnabled=false,notflixPresentationActive=active,
            notflixCatalog=projectNotflixCatalogs(NotflixCatalogSnapshot(2,1,true),listOf("collection_notflix-inicio"),emptyList(),listOf(collection),
                emptySet(),emptyMap(),emptyList(),false,"Movie","Series",rows),notflixCwPhase=NotflixRowPhase.READY)
    }
    private fun configure(){loader.configure(if(active)context.copy(visit=visit)else null,
        if(active)notflixCollectionSpecs(listOf(collection),listOf(addon),emptySet())else emptyList());publish()}
    private fun show(){
        instrumentation.runOnMainSync{
            loader=NotflixCollectionRows(owner,{request->flow{
                val key=signature(request);requests+=key;gates[key]?.await()
                emit(NetworkResult.Success(CatalogRow(addon.id,addon.name,addon.baseUrl,request.catalogId,request.catalogId,ContentType.fromString(request.type),
                    rawType=request.type,items=(0 until itemsPerRow).map{item("$key:$it",request.type)},extraArgs=request.extraArgs)))
            }},{true},{publish()})
            configure()
        }
        compose.setContent{
            NuvioTheme{
                nav=rememberNavController()
                NavHost(nav,startDestination="home",enterTransition={EnterTransition.None},exitTransition={ExitTransition.None},
                    popEnterTransition={EnterTransition.None},popExitTransition={ExitTransition.None}){
                    composable("home"){
                        DisposableEffect(Unit) {
                            // Same inactive gap and delayed projection publication as the real Home host.
                            if(!active)owner.launch { delay(75);active=true;visit++;configure() }
                            onDispose { active=false;configure() }
                        }
                        val focus=remember{FocusRequester()};val held=remember{mutableStateOf(false)}
                        val shell=rememberNotflixNavigationShell(navigation,"home","home",setOf("home","search","settings"),"home",
                            listOf(NotflixDestination("home","Home"),NotflixDestination("search","Search"),NotflixDestination("settings","Settings")),
                            focus,null,{}, {}, {},held)
                        CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true,navigation,reduceHomeMotion=true)){
                            PresentationShellHost(shell,focus){
                                NotflixHomeContent(notflixHomeEntryState(ui),memory,navigation=navigation,
                                    onDetail={id,_,_->detailOpened=id;destination="detail";nav.navigate("detail")},
                                    onContinue={},onFolder={a,b->folderOpened=a to b;destination="folder";nav.navigate("folder")},
                                    onVisibleRow={loader.request(it)},onHiddenRow=loader::release,onRetry={})
                            }
                        }
                    }
                    composable("folder"){BackHandler{destination="home";nav.popBackStack()};Text("Inert folder destination")}
                    composable("detail"){BackHandler{destination="home";nav.popBackStack()};Text("Inert detail destination")}
                }
            }
        }
    }
    private fun row(index:Int)=ui.notflixCatalog.rows[index]
    private fun tag(row:NotflixCatalogRow,index:Int)="notflix-card-${row.key}|${row.catalog!!.stableItemKeys()[index]}"
    private fun seeAll(row:NotflixCatalogRow)="notflix-card-${row.key}|see-all"
    private fun focus(tag:String){
        compose.waitUntil(10000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any{it.config.getOrElse(SemanticsProperties.Focused){false}}}
        compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun ready(index:Int){
        compose.waitUntil(10000){row(index).phase==NotflixRowPhase.READY && row(index).catalog!=null}
    }
    private fun key(tag:String,key:Key){compose.onNodeWithTag(tag).performKeyInput{keyDown(key);keyUp(key)}}
    private fun back(){instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);compose.waitForIdle()}
    @Test fun elevenRowsDemandOnlyVisibleSourcesAndTraverseWithDpadInReduced(){
        show();ready(0);focus(tag(row(0),0))
        assertTrue(requests.size<11)
        for(index in 1..10){
            key(tag(row(index-1),0),Key.DirectionDown)
            ready(index);focus(tag(row(index),0))
        }
        assertEquals(11,ui.notflixCatalog.rows.size)
        assertEquals(collection.folders.map{it.title},ui.notflixCatalog.rows.map{it.title})
        assertEquals(11,requests.size);assertEquals(11,requests.toSet().size)
        assertTrue("movie/year/2026" in requests && "series/year/2026" in requests && "movie/top/Sci-Fi" in requests)
        compose.runOnIdle{assertNull(detailOpened);assertNull(folderOpened)}
    }
    @Test fun seeAllOpensCorrectFolderAndBackRestoresSameAction(){
        show();ready(0);focus(tag(row(0),0))
        repeat(3){i->key(if(i==0)tag(row(0),0)else tag(row(0),i),Key.DirectionRight)}
        val returnTag=seeAll(row(0))
        focus(returnTag);key(returnTag,Key.DirectionCenter)
        compose.onNodeWithText("Inert folder destination").assertExists()
        assertEquals("notflix-inicio" to collection.folders.first().id,folderOpened)
        back();focus(returnTag)
        assertEquals("home",destination)
    }
    @Test fun officialDetailPortAndBackKeepTitleRowAndPosition(){
        show();ready(0);focus(tag(row(0),0))
        key(tag(row(0),0),Key.DirectionRight);focus(tag(row(0),1))
        val returnTag=tag(row(0),1);val expectedId=row(0).catalog!!.items[1].id
        key(returnTag,Key.DirectionCenter);compose.waitForIdle()
        if(destination=="home")key(returnTag,Key.DirectionCenter)
        compose.onNodeWithText("Inert detail destination").assertExists()
        assertEquals(expectedId,detailOpened)
        back();focus(returnTag);assertEquals(1,memory.focus!!.index)
    }
    @Test fun firstBrowsingTitleReturnKeepsMeasuredBounds(){
        show();ready(0);focus(tag(row(0),0))
        for(index in 1..2){key(tag(row(index-1),0),Key.DirectionDown);ready(index);focus(tag(row(index),0))}
        compose.waitUntil(10000){memory.focus?.expanded==true}
        val returnTag=tag(row(2),0)
        val before=compose.onNodeWithTag(returnTag).fetchSemanticsNode().boundsInRoot
        key(returnTag,Key.DirectionCenter)
        compose.onNodeWithText("Inert detail destination").assertExists()
        back();focus(returnTag);compose.waitForIdle()
        assertEquals("First detail return must preserve measured bounds",before,
            compose.onNodeWithTag(returnTag).fetchSemanticsNode().boundsInRoot)
    }
    @Test fun firstBrowsingSeeAllReturnKeepsMeasuredBounds(){
        itemsPerRow=40
        show();ready(0);focus(tag(row(0),0))
        for(index in 1..2){key(tag(row(index-1),0),Key.DirectionDown);ready(index);focus(tag(row(index),0))}
        compose.waitUntil(10000){memory.focus?.expanded==true}
        for(index in 1..itemsPerRow){
            key(tag(row(2),index-1),Key.DirectionRight)
            focus(if(index==itemsPerRow)seeAll(row(2))else tag(row(2),index))
        }
        val returnTag=seeAll(row(2))
        val before=compose.onNodeWithTag(returnTag).fetchSemanticsNode().boundsInRoot
        key(returnTag,Key.DirectionCenter)
        compose.onNodeWithText("Inert folder destination").assertExists()
        back();focus(returnTag);compose.waitForIdle()
        assertEquals("First folder return must preserve measured bounds",before,
            compose.onNodeWithTag(returnTag).fetchSemanticsNode().boundsInRoot)
    }
    @Test fun removalWhileSourceIsPendingDiscardsResponseAndRepairsFocus(){
        val gate=CompletableDeferred<Unit>();gates["series/top/"]=gate
        show();ready(0);focus(tag(row(0),0))
        key(tag(row(0),0),Key.DirectionDown)
        compose.waitUntil(10000){memory.focus?.rowKey=="collection_notflix-inicio:series-populares"}
        compose.runOnIdle{
            collection=collection.copy(folders=collection.folders.filterNot{it.id=="series-populares"})
            configure();gate.complete(Unit)
        }
        compose.waitUntil(10000){memory.focus?.rowKey!=null && memory.focus?.rowKey!="collection_notflix-inicio:series-populares"}
        assertEquals(10,ui.notflixCatalog.rows.size)
        assertTrue(ui.notflixCatalog.rows.none{it.title=="Series populares"})
        assertTrue(ui.notflixCatalog.rows.flatMap{it.catalog?.items.orEmpty()}.none{it.id.startsWith("series/top/")})
    }
    @Test fun changedSourceDoesNotShowRetainedTitlesAndKeepsOfficialFolderExit(){
        show();ready(0);focus(tag(row(0),0))
        val gate=CompletableDeferred<Unit>();gates["movie/top/Family"]=gate
        compose.runOnIdle{
            val first=collection.folders.first()
            collection=collection.copy(folders=listOf(first.copy(title="Edited folder",sources=listOf(AddonCatalogCollectionSource(addon.id,"movie","top","Family"))))+collection.folders.drop(1))
            configure()
        }
        compose.waitUntil(10000){row(0).sourceIdentity?.extraArgs?.get("genre")=="Family"}
        assertNull(row(0).catalog)
        compose.runOnIdle{gate.complete(Unit)}
        ready(0)
        assertEquals("Edited folder",row(0).title)
        assertTrue(row(0).catalog!!.items.all{it.id.startsWith("movie/top/Family")})
    }
}
