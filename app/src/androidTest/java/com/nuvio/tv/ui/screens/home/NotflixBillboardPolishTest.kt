package com.nuvio.tv.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import android.content.res.Configuration
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.appearance.*
import com.nuvio.tv.ui.theme.NuvioTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Offline product renderer; identical controlled clock/window for M24 baseline and comparison. */
class NotflixBillboardPolishTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val stage = InstrumentationRegistry.getArguments().getString("m24Stage") ?: "baseline"
    private val output by lazy { File(instrumentation.targetContext.getExternalFilesDir(null), "nf003-m24-fixtures/$stage").apply { mkdirs() } }
    private val compositionExports = AtomicInteger(0)
    private lateinit var view: android.view.View
    private var playActivations = 0
    private var frameProgress: () -> Float = { 0f }
    private var frameScale: () -> Float = { 1f }
    private var current by mutableStateOf<MetaPreview?>(null)
    private var language by mutableStateOf("en")
    private var advances = 0
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun record(name: String, value: JSONObject) {
        File(output,"$name.json").writeText(value.toString(2))
        Log.i("M24Billboard", value.toString())
        instrumentation.sendStatus(0, Bundle().apply { putString("m24_record", value.toString()) })
    }
    private fun capture(name: String) {
        File(output,"$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun image(name: String, second: Boolean = false): String {
        val file=File(output,"$name.png")
        val bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
        for(y in 0 until 90) for(x in 0 until 160) bitmap.setPixel(x,y,
            if ((x/20+y/15)%2==0) (if(second) AndroidColor.rgb(80,140,40) else AndroidColor.rgb(40,100,180)) else AndroidColor.rgb(200,150,60))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        return file.toURI().toString()
    }
    private fun fixture(): MetaPreview = MetaPreview("m24:alpha",ContentType.MOVIE,name="Original title",poster=null,
        posterShape=PosterShape.POSTER,background=image("fixture-art"),logo=null,
        description="An offline fixture exercises the real banner without contacting a provider.",releaseInfo="2026",imdbRating=7.5f,genres=listOf("Drama","Adventure"),runtime="126")
    private fun standalone(reduced: Boolean = false) {
        compose.mainClock.autoAdvance=false
        val initial=fixture();current=initial
        compose.setContent {
            view = LocalView.current
            val localizedConfig=remember(language) { Configuration(instrumentation.targetContext.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) } }
            val localizedContext=remember(language) { instrumentation.targetContext.createConfigurationContext(localizedConfig) }
            CompositionLocalProvider(LocalContext provides localizedContext,LocalConfiguration provides localizedConfig) { NuvioTheme {
            val header=remember { FocusRequester() };val info=remember { FocusRequester() };val play=remember { FocusRequester() }
            CompositionLocalProvider(LocalBillboardFrameObserver provides { progress,scale -> frameProgress=progress;frameScale=scale;compositionExports.incrementAndGet() }) {
                Column {
                    Text("Home",Modifier.focusRequester(header).clickable {}.testTag("m24-header"))
                    current?.let { NotflixBillboard(it,0,6,PreviewBinding(AppearanceContext(2,1),1),reduced,true,0,
                        info,play,onAdvance={advances++},onSelect={},onInteraction={},onFocus={},onKey={_,_->},onInfo={},onPlay={playActivations++}) }
                }
            }
            LaunchedEffect(Unit) { header.requestFocus() }
        } } }
        step(80)
    }
    @Test fun stableWindowCountsRealCompositionSemanticsAndDrawing() {
        standalone();step(1920)
        compose.onNodeWithTag("m24-header").assertIsFocused()
        val start=compositionExports.get();val startProgress=frameProgress();val startScale=frameScale()
        var semanticValueChanges=0;var semanticSnapshots=0
        var previous=compose.onNodeWithTag("notflix-editorial").fetchSemanticsNode().config.toString()
        repeat(375) {
            step(16)
            val next=compose.onNodeWithTag("notflix-editorial").fetchSemanticsNode().config.toString()
            if(next!=previous) semanticValueChanges++
            previous=next;semanticSnapshots++
        }
        val delta=mapOf("banner" to compositionExports.get()-start)
        assertEquals(0,delta["banner"]);assertEquals(0,semanticValueChanges)
        assertFalse(previous.contains("BillboardProgress"));assertFalse(previous.contains("BillboardImageScale"))
        assertTrue(frameProgress()>startProgress+.58f);assertTrue(frameScale()>startScale+.019f)
        assertEquals(0,advances);assertEquals("m24:alpha",current?.id)
        val result=JSONObject().put("stage",stage).put("window_ms",6000).put("start_clock_ms",2000).put("frame_steps",375)
            .put("counts",JSONObject(delta)).put("semantic_snapshots",semanticSnapshots).put("semantic_value_changes",semanticValueChanges)
            .put("start_progress",startProgress).put("end_progress",frameProgress()).put("start_scale",startScale).put("end_scale",frameScale())
        record("stable-window",result);capture("stable-window-end")
    }
    @Test fun sameIdentityEnrichmentCapturesBackgroundTransition() {
        standalone();step(2000);capture("enrichment-before")
        val original=current!!;val logo=image("fixture-logo")
        compose.runOnUiThread { current=original.copy(name="Late enriched title",logo=logo,description="Late metadata of the same identity") }
        val observations=org.json.JSONArray()
        var elapsed=0L
        for(at in listOf(16L,64L,128L,256L,480L)) {
            step(at-elapsed);elapsed=at
            val arts=compose.onAllNodesWithTag("notflix-editorial-art",useUnmergedTree=true).fetchSemanticsNodes().size
            assertEquals(1,arts)
            observations.put(JSONObject().put("ms",at).put("art_nodes",arts));capture("enrichment-$at")
        }
        record("same-identity-enrichment",JSONObject().put("stage",stage).put("same_identity",notflixMetaKey(original)==notflixMetaKey(current!!)).put("sequence",observations))
        compose.runOnUiThread { current=current!!.copy(background=image("replacement-art",true)) };step(500)
        capture("actual-background-update");assertEquals(original.id,current?.id)
    }

    @Test fun typographyBoundsAndEllipsisEnglishSpanish() {
        standalone();step(1000)
        val base=current!!;val offlineLogo=image("layout-logo")
        val results=org.json.JSONArray()
        for(locale in listOf("en","es")) for(case in 0..2) {
            val rich=if(locale=="en") "A young traveller discovers an unexpected friendship while crossing a distant country. Their journey reveals a family secret and challenges everything they thought they knew." else "Una joven viajera descubre una amistad inesperada al cruzar un país lejano. El viaje revela un secreto familiar y pone a prueba todo lo que creían saber."
            val model=when(case) {
                0 -> base.copy(id="m24:layout:logo",name="Lupin",logo=offlineLogo,description=rich,genres=if(locale=="en") listOf("Crime","Drama","Mystery") else listOf("Crimen","Drama","Misterio"))
                1 -> base.copy(id="m24:layout:long",name=if(locale=="en") "The Lord of the Rings: The Return of the King" else "El señor de los anillos: El retorno del rey",description=rich,releaseInfo="2003",runtime="201",genres=if(locale=="en") listOf("Adventure","Fantasy","Drama") else listOf("Aventura","Fantasía","Drama"))
                else -> base.copy(id="m24:layout:missing",name="The Weight",description=null,releaseInfo=null,imdbRating=null,genres=emptyList(),runtime=null)
            }
            compose.runOnUiThread { language=locale;current=model };step(1000)
            val nodes=compose.onAllNodes(hasText("",substring=true),useUnmergedTree=true).fetchSemanticsNodes()
            val fields=org.json.JSONArray()
            nodes.forEach { node ->
                if(SemanticsProperties.Text !in node.config) return@forEach
                val label=node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
                if(label=="Home") return@forEach
                val layouts=mutableListOf<TextLayoutResult>()
                if(SemanticsActions.GetTextLayoutResult in node.config) node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val field=JSONObject().put("text",label).put("bounds",node.boundsInRoot.toString())
                layouts.firstOrNull()?.let { layout -> field.put("font_sp",layout.layoutInput.style.fontSize.value).put("width_px",layout.size.width).put("height_px",layout.size.height).put("line_count",layout.lineCount).put("overflow",layout.hasVisualOverflow).put("visible_end",layout.getLineEnd(layout.lineCount-1,true)).put("length",label.length) }
                fields.put(field)
            }
            val copy=compose.onNodeWithTag("notflix-editorial-copy").fetchSemanticsNode().boundsInRoot
            val banner=compose.onNodeWithTag("notflix-editorial").fetchSemanticsNode().boundsInRoot
            val play=compose.onNodeWithTag("notflix-editorial-play").fetchSemanticsNode().boundsInRoot
            val info=compose.onNodeWithTag("notflix-editorial-details").fetchSemanticsNode().boundsInRoot
            assertTrue(play.right<=info.left);assertTrue(info.right<=copy.right+1f);assertTrue(play.bottom<=banner.bottom)
            results.put(JSONObject().put("locale",locale).put("case",case).put("copy_bounds",copy.toString()).put("banner_bounds",banner.toString()).put("play_bounds",play.toString()).put("info_bounds",info.toString()).put("fields",fields))
            capture("layout-$locale-$case")
        }
        record("typography",JSONObject().put("stage",stage).put("cases",results).put("logos","offline fixture shape; real logo inspected separately in native demo"))
    }

    @Test fun sequentialCopyCancelsHeldPressAndKeepsActionsAndFrameStill() {
        standalone();step(1000)
        val beforePlay=compose.onNodeWithTag("notflix-editorial-play").fetchSemanticsNode().boundsInRoot
        val beforeInfo=compose.onNodeWithTag("notflix-editorial-details").fetchSemanticsNode().boundsInRoot
        val beforeFrame=compose.onNodeWithTag("notflix-editorial").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("notflix-editorial-play").performSemanticsAction(SemanticsActions.RequestFocus) { it() };step(32)
        val down=android.os.SystemClock.uptimeMillis()
        compose.runOnUiThread { view.dispatchKeyEvent(android.view.KeyEvent(down,down,android.view.KeyEvent.ACTION_DOWN,23,0)) }
        compose.runOnUiThread { current=current!!.copy(id="m24:successor",name="Visible successor",description=null,releaseInfo=null,imdbRating=null,genres=emptyList(),runtime=null) }
        val frames=org.json.JSONArray();var elapsed=0L
        for(at in listOf(32L,64L,96L,128L,160L,224L,320L,400L)) {
            step(at-elapsed);elapsed=at
            val oldCount=compose.onAllNodesWithText("Original title",useUnmergedTree=true).fetchSemanticsNodes().size
            val newCount=compose.onAllNodesWithText("Visible successor",useUnmergedTree=true).fetchSemanticsNodes().size
            assertTrue(oldCount+newCount<=1)
            assertEquals(beforePlay,compose.onNodeWithTag("notflix-editorial-play").fetchSemanticsNode().boundsInRoot)
            assertEquals(beforeInfo,compose.onNodeWithTag("notflix-editorial-details").fetchSemanticsNode().boundsInRoot)
            assertEquals(beforeFrame,compose.onNodeWithTag("notflix-editorial").fetchSemanticsNode().boundsInRoot)
            if(at==32L) {
                assertEquals(1,oldCount);assertEquals(0,newCount)
                compose.onNodeWithTag("notflix-editorial-play").assertIsNotEnabled().assertIsFocused()
            }
            if(at==64L) compose.runOnUiThread {
                view.dispatchKeyEvent(android.view.KeyEvent(down,down+64,android.view.KeyEvent.ACTION_DOWN,23,1))
                view.dispatchKeyEvent(android.view.KeyEvent(down,down+65,android.view.KeyEvent.ACTION_UP,23,0))
            }
            frames.put(JSONObject().put("ms",at).put("old_text_nodes",oldCount).put("new_text_nodes",newCount));capture("copy-transition-$at")
        }
        assertEquals(0,playActivations)
        compose.onNodeWithText("Visible successor").assertExists();compose.onNodeWithText("Original title").assertDoesNotExist()
        compose.onNodeWithTag("notflix-editorial-play").assertIsEnabled().assertIsFocused()
        val next=android.os.SystemClock.uptimeMillis()
        compose.runOnUiThread {
            view.dispatchKeyEvent(android.view.KeyEvent(next,next,android.view.KeyEvent.ACTION_DOWN,23,0))
            view.dispatchKeyEvent(android.view.KeyEvent(next,next+20,android.view.KeyEvent.ACTION_DOWN,23,1))
            view.dispatchKeyEvent(android.view.KeyEvent(next,next+40,android.view.KeyEvent.ACTION_UP,23,0))
        }
        step(32);assertEquals(1,playActivations)
        record("sequential-copy",JSONObject().put("stage",stage).put("sequence",frames).put("held_cancelled",true).put("fresh_repeat_dispatches",1).put("actions_bounds_unchanged",true).put("frame_bounds_unchanged",true))
    }
    @Test fun reducedManualChangeHasOneStaticCopyAndNoAutomaticAdvance() {
        standalone(reduced=true);step(1000)
        compose.runOnUiThread { current=current!!.copy(id="m24:reduced",name="Static reduced title",description=null) };step(32)
        compose.onNodeWithText("Static reduced title").assertExists();compose.onNodeWithText("Original title").assertDoesNotExist()
        compose.onNodeWithTag("notflix-editorial-play").assertIsEnabled()
        step(12_000);assertEquals(0,advances);assertEquals(0f,frameProgress(),0f);assertEquals(1f,frameScale(),0f)
        capture("reduced-static")
    }
}
