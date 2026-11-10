package com.nuvio.tv.ui.screens.home

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.appearance.*
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Declared synthetic rows/collections, actual projection/Home/shell/focus; no store, URLs or player. */
private class CatalogFixture {
    val rows=listOf("Alpha","Beta","Gamma").mapIndexed { i,title ->
        val item=MetaPreview(id="shared",type=ContentType.MOVIE,name="Synthetic $title",poster=null,posterShape=PosterShape.POSTER,background=null,logo=null,description=null,releaseInfo=null,imdbRating=null,genres=emptyList())
        val data=CatalogRow("fixture$i","Synthetic addon$i","https://fixture$i.invalid","top",title,ContentType.MOVIE,items=listOf(item,item.copy(id="second")))
        NotflixCatalogRow(data.stableKey(),data.legacyKey(),"$title - Movie",NotflixRowPhase.READY,data)
    }
    val descriptors=rows.map{r->val c=r.catalog!!;HomeViewModel.PlaceholderDescriptor(r.loadKey,c.addonId,c.addonName,c.addonBaseUrl,c.catalogId,c.catalogName,c.apiType,r.title)}
    var order by mutableStateOf(rows.map{it.loadKey})
    var disabled by mutableStateOf(emptySet<String>())
    var titles by mutableStateOf(emptyMap<String,String>())
    var phases by mutableStateOf(emptyMap<String,NotflixRowPhase>())
    var absent by mutableStateOf(emptySet<String>())
    var collections by mutableStateOf(emptyList<Collection>())
    var detail by mutableStateOf<String?>(null)
    val memory=NotflixHomeMemory();val navigation=NotflixNavigationState().apply{selectedRoute="home"}
    val details=mutableListOf<String>();val folders=mutableListOf<Pair<String,String>>()
    fun tag(index:Int)= "notflix-card-${rows[index].key}|${rows[index].catalog!!.stableItemKeys().first()}"
}

@Composable private fun CatalogContent(s:CatalogFixture) {
    val movie=stringResource(R.string.type_movie);val series=stringResource(R.string.type_series)
    val captured=NotflixCatalogSnapshot(2,7,true,s.rows.map{it.copy(phase=s.phases[it.loadKey]?:it.phase,catalog=it.catalog.takeUnless{_->it.loadKey in s.absent})})
    val projected=projectNotflixCatalogs(captured,s.order,s.rows.filter{it.loadKey !in s.absent}.mapNotNull{it.catalog},s.collections,s.disabled,s.titles,s.descriptors,true,movie,series)
    val focus=remember{FocusRequester()};val held=remember{mutableStateOf(false)}
    val shell=rememberNotflixNavigationShell(s.navigation,if(s.detail==null)"home" else "detail","home",setOf("home","search","library","settings"),"home",
        listOf("home","search","library","settings").map{NotflixDestination(it,it)},focus,"Fixture",{},{},{},held)
    CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(true,s.navigation,true)) {
        NotflixPresentationTheme(true) {
            PresentationShellHost(shell,focus) {
                if(s.detail==null) NotflixHomeContent(HomeUiState(notflixPresentationActive=true,notflixCatalog=projected,layoutPreferencesReady=true,isLoading=false,
                    heroSectionEnabled=false,continueWatchingEnabled=false),s.memory,navigation=s.navigation,
                    onDetail={id,_,_->s.details+=id;s.detail=id},onContinue={},onFolder={c,f->s.folders+=c to f},onVisibleRow={},onLongPress={_,_->},onRetry={})
                else {
                    val back=remember{FocusRequester()}
                    Button(onClick={s.detail=null},modifier=Modifier.focusRequester(back).testTag("nf17-detail-back")){Text("Synthetic return")}
                    BackHandler{s.detail=null};LaunchedEffect(Unit){back.requestFocus()}
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class NotflixCatalogPersonalizationTest {
    @get:Rule val compose=createComposeRule()
    private fun show(s:CatalogFixture,spanish:Boolean=false) {
        compose.setContent {
            val base=LocalContext.current
            val ctx=remember(spanish){base.createConfigurationContext(Configuration(base.resources.configuration).apply{setLocale(if(spanish)Locale("es")else Locale.US)})}
            CompositionLocalProvider(LocalContext provides ctx,LocalConfiguration provides ctx.resources.configuration){CatalogContent(s)}
        }
        compose.waitForIdle();focused(s,0)
    }
    private fun focused(s:CatalogFixture,index:Int) {
        try {
            compose.waitUntil(10_000){runCatching{compose.onNodeWithTag(s.tag(index)).assertIsFocused();true}.getOrDefault(false)}
        } catch (failure:Throwable) {
            Log.d("NotflixCatalogTest","expected_row=$index memory_focus=${s.memory.focus} top=${s.navigation.topFocused}")
            compose.onRoot(useUnmergedTree=true).printToLog("NotflixCatalogTree")
            capture("focus-failure")
            throw failure
        }
    }
    private fun key(tag:String,key:Key){compose.onNodeWithTag(tag).performKeyInput{pressKey(key)};compose.waitForIdle()}
    private fun capture(label:String) {
        val ins=InstrumentationRegistry.getInstrumentation();val run=InstrumentationRegistry.getArguments().getString("nf_m17_run")?:"catalogs-r1"
        require(Regex("catalogs-r[0-9]+").matches(run))
        val dir=File(requireNotNull(ins.targetContext.getExternalFilesDir(null)),"nf003-m17/$run/$label-${UUID.randomUUID()}");check(dir.mkdirs())
        val b=requireNotNull(ins.uiAutomation.takeScreenshot());File(dir,"screen.png").outputStream().use{check(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
        Log.d("NotflixCatalogTest","artifact_dir=${dir.absolutePath}")
    }
    @Test fun currentOverrideUpdatesVisibleHeadingAndKeepsExactCardFocus() {
        val s=CatalogFixture();show(s);capture("before-title")
        compose.runOnIdle{s.titles=mapOf(s.rows[0].loadKey to "my films")};compose.waitForIdle()
        compose.onNodeWithText("My films - Movie").assertExists();compose.onNodeWithText("Alpha - Movie").assertDoesNotExist();focused(s,0)
        capture("after-title");compose.runOnIdle{assertTrue(s.details.isEmpty());assertTrue(s.folders.isEmpty())}
    }
    @Test fun reorderKeepsIdentityAndHiddenFocusedRowUsesNextThenRestoresWithoutStealingFocus() {
        val s=CatalogFixture();show(s)
        compose.runOnIdle{s.order=listOf(s.rows[1].loadKey,s.rows[0].loadKey,s.rows[2].loadKey)};compose.waitForIdle();focused(s,0)
        // Alpha moved to index1; after removal the existing positional fallback is Gamma at index1.
        compose.runOnIdle{s.disabled=setOf(s.rows[0].loadKey)};compose.waitForIdle();focused(s,2)
        compose.runOnIdle{assertEquals(1,s.memory.focus!!.rowIndex)}
        compose.runOnIdle{s.disabled=emptySet();s.order=s.rows.map{it.loadKey}};compose.waitForIdle();focused(s,2)
        compose.runOnIdle{assertTrue(s.details.isEmpty());assertTrue(s.folders.isEmpty())}
    }
    @Test fun pendingRetainsItemsButUsesNewHeadingAndReadyRemovalRepairsFocus() {
        val s=CatalogFixture();show(s)
        compose.runOnIdle{s.phases=mapOf(s.rows[0].loadKey to NotflixRowPhase.PENDING);s.absent=setOf(s.rows[0].loadKey);s.titles=mapOf(s.rows[0].loadKey to "pending name")}
        compose.waitForIdle();capture("pending-heading")
        compose.onNodeWithText("Pending name - Movie · updating…").assertExists()
        compose.onNodeWithText("Alpha - Movie").assertDoesNotExist();focused(s,0)
        compose.runOnIdle{s.disabled=setOf(s.rows[0].loadKey)};compose.waitForIdle();focused(s,1)
    }
    @Test fun allHiddenReturnsToTopAndDownAfterRestoreUsesOriginalIdentity() {
        val s=CatalogFixture();show(s)
        compose.runOnIdle{s.disabled=s.rows.map{it.loadKey}.toSet()};compose.waitForIdle()
        compose.waitUntil(10_000){s.navigation.topFocused};compose.onNodeWithTag("notflix-destination-home").assertIsFocused()
        compose.onNodeWithText("Alpha - Movie").assertDoesNotExist()
        compose.runOnIdle{s.disabled=emptySet()};compose.waitForIdle();key("notflix-destination-home",Key.DirectionDown);focused(s,0)
    }
    @Test fun duplicateCatalogIdsAcrossAddonsKeepIndependentLabelsAndDetailReturn() {
        val s=CatalogFixture();show(s);key(s.tag(0),Key.DirectionDown);focused(s,1)
        compose.runOnIdle{s.titles=mapOf(s.rows[0].loadKey to "only alpha")};compose.waitForIdle()
        compose.onNodeWithText("Beta - Movie").assertExists();focused(s,1)
        key(s.tag(1),Key.DirectionCenter);compose.onNodeWithTag("nf17-detail-back").assertIsFocused()
        key("nf17-detail-back",Key.DirectionCenter);focused(s,1)
        compose.runOnIdle{assertEquals(listOf("shared"),s.details);assertEquals(s.rows[1].key,s.memory.focus!!.rowKey)}
    }
    @Test fun pinnedCollectionUsesCurrentTitleAndOfficialFolderIdentity() {
        val s=CatalogFixture();show(s)
        compose.runOnIdle{s.collections=listOf(Collection("c","My shelf",pinToTop=true,folders=listOf(CollectionFolder("f","Owned folder"))))}
        compose.waitForIdle();focused(s,0)
        key(s.tag(0),Key.DirectionUp)
        val folder="notflix-card-collection_c|f"
        compose.waitUntil(10_000){runCatching{compose.onNodeWithTag(folder).assertIsFocused();true}.getOrDefault(false)}
        compose.runOnIdle{s.collections=s.collections.map{it.copy(title="Renamed shelf")}};compose.waitForIdle()
        compose.onNodeWithText("Renamed shelf").assertExists();key(folder,Key.DirectionCenter)
        compose.runOnIdle{assertEquals(listOf("c" to "f"),s.folders);assertTrue(s.details.isEmpty())}
    }
    @Test fun spanishResourceSuffixAndUserTitleRemainVisibleWithoutChangingGlobalLanguage() {
        val s=CatalogFixture().apply{titles=mapOf(rows[0].loadKey to "mis películas")};show(s,true)
        compose.onNodeWithText("Mis películas - Película").assertExists();focused(s,0);capture("spanish-title")
    }
    @Test fun removingOverrideRestoresDescriptorNameWhileFocusAndDataStayUseful() {
        val s=CatalogFixture().apply{titles=mapOf(rows[0].loadKey to "temporary")};show(s)
        compose.runOnIdle{s.titles=emptyMap()};compose.waitForIdle();compose.onNodeWithText("Alpha - Movie").assertExists();focused(s,0)
        compose.runOnIdle{assertTrue(s.details.isEmpty())}
    }
}
