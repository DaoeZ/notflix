package com.nuvio.tv.ui.screens.detail

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.data.wikipedia.WikipediaExtract
import com.nuvio.tv.domain.model.MDBListRatings
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual MetaDetailsContent + block, declared synthetic CC0 prose. No Hilt/stores/network/player. */
@OptIn(ExperimentalTestApi::class)
class NotflixWikipediaPresentationTest {
    @get:Rule val compose=createComposeRule()
    private var extract by mutableStateOf<WikipediaExtract?>(null)
    private fun fixture(language:String="es",series:Boolean=false):WikipediaExtract {
        val text=if(language=="es")"Texto sintético NF-M18 creado para esta prueba de presentación; no describe ninguna película real ni atribuye opiniones a críticos reales."
            else "Synthetic NF-M18 text written for this presentation test; it does not describe a real film or attribute opinions to actual critics."
        return WikipediaExtract("tt0000001",if(series)"series" else "movie","Q123",language,"NF-M18 synthetic fixture",
            "https://$language.wikipedia.org/wiki/NF-M18_synthetic_fixture",if(language=="es")"Crítica" else "Critical response",
            "Critical_response",10,20,1_700_000_000_000,listOf(text))
    }
    private fun show(s:DetailFixtureState,spanish:Boolean=false) {
        compose.setContent {
            val base=LocalContext.current
            val context=remember(spanish){base.createConfigurationContext(Configuration(base.resources.configuration).apply{setLocale(if(spanish)Locale("es")else Locale.US)})}
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides context.resources.configuration){DetailFixture(s,extract)}
        };compose.waitForIdle()
    }
    private fun play()=compose.onNode(isFocusable() and hasClickAction() and (hasText("Play") or hasAnyDescendant(hasText("Play"))))
    private fun scrollToBlock() {
        compose.onNodeWithTag("meta-details-page").performScrollToNode(hasTestTag("notflix-wikipedia"));compose.waitForIdle()
    }
    private fun source()=compose.onNodeWithTag("notflix-wikipedia-source")
    private fun key(key:Key) {
        // Dispatch to the Android-focused window: dialog and underlying Compose roots
        // can both retain Focused semantics. Never select an arbitrary focused node.
        val code=when(key) {
            Key.DirectionDown->android.view.KeyEvent.KEYCODE_DPAD_DOWN
            Key.DirectionUp->android.view.KeyEvent.KEYCODE_DPAD_UP
            Key.DirectionLeft->android.view.KeyEvent.KEYCODE_DPAD_LEFT
            Key.DirectionRight->android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            Key.DirectionCenter->android.view.KeyEvent.KEYCODE_DPAD_CENTER
            else->error("Unsupported fixture key")
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    private fun readingAreaFocused() {
        try {
            compose.waitUntil(10_000){runCatching{compose.onNodeWithTag("notflix-wikipedia-attribution").assertIsFocused();true}.getOrDefault(false)}
        } catch(failure:Throwable) {
            compose.onAllNodes(isRoot(),useUnmergedTree=true).onLast().printToLog("NotflixWikipediaTree")
            capture("source-focus-failure");throw failure
        }
    }
    private fun readToClose() {
        readingAreaFocused()
        for(step in 0..20) {
            if(runCatching{compose.onNodeWithTag("notflix-wikipedia-close").assertIsFocused();true}.getOrDefault(false))break
            key(Key.DirectionDown)
        }
        compose.onNodeWithTag("notflix-wikipedia-close").assertIsFocused()
    }
    private fun capture(label:String) {
        val ins=InstrumentationRegistry.getInstrumentation();val run=InstrumentationRegistry.getArguments().getString("nf_m18_run")?:"wikipedia-r1"
        require(Regex("wikipedia-r[0-9]+").matches(run))
        val dir=File(requireNotNull(ins.targetContext.getExternalFilesDir(null)),"nf003-m18/$run/$label-${UUID.randomUUID()}");check(dir.mkdirs())
        val bitmap=requireNotNull(ins.uiAutomation.takeScreenshot());File(dir,"screen.png").outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        Log.d("NotflixWikipediaTest","artifact_dir=${dir.absolutePath}")
    }
    @Test fun delayedExtractDoesNotStealInitialPlayFocusOrChangeOfficialRatings() {
        val s=DetailFixtureState().apply{mdbActive=true;mdbRatings=MDBListRatings(imdb=8.1,metacritic=77.0)};show(s)
        play().performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        compose.runOnIdle{extract=fixture()};compose.waitForIdle();play().assertIsFocused()
        compose.onNodeWithTag("notflix-rating-imdb").assertTextEquals("IMDb 8.1/10")
        compose.runOnIdle{assertTrue(s.actions.isEmpty());assertFalse(s.overlay)}
    }
    @Test fun spanishBlockAndAttributionAreReadableWithNoAutomaticExternalAction() {
        extract=fixture();val s=DetailFixtureState();show(s,true);scrollToBlock();capture("spanish")
        compose.onNodeWithText("Qué dijeron los críticos · Wikipedia").assertIsDisplayed()
        compose.onNodeWithText("Extracto enciclopédico de recepción crítica · en español").assertIsDisplayed()
        compose.onNodeWithTag("notflix-wikipedia-credit").assertIsDisplayed()
        source().performSemanticsAction(SemanticsActions.RequestFocus);key(Key.DirectionCenter)
        compose.onNodeWithTag("notflix-wikipedia-attribution").assertExists();compose.onNodeWithText("https://es.wikipedia.org/wiki/NF-M18_synthetic_fixture").assertExists()
        readingAreaFocused();capture("attribution");readToClose()
        key(Key.DirectionCenter);source().assertIsFocused()
        compose.runOnIdle{assertTrue(s.actions.isEmpty());assertTrue(s.navigation.isEmpty())}
    }
    @Test fun englishFallbackIsExplicitAndDoesNotClaimTranslationOrConsensus() {
        extract=fixture("en");show(DetailFixtureState(),true);scrollToBlock();capture("english")
        compose.onNodeWithText("Extracto enciclopédico de recepción crítica · en inglés").assertIsDisplayed()
        compose.onNodeWithText("Synthetic NF-M18",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Rotten Tomatoes consensus",substring=true).assertDoesNotExist()
    }
    @Test fun absentInvalidOrNuvioDataHasNoEmptyExtractPanel() {
        val s=DetailFixtureState();show(s);compose.onNodeWithTag("notflix-wikipedia").assertDoesNotExist()
        compose.runOnIdle{extract=fixture().copy(revisionId=0)};compose.waitForIdle();compose.onNodeWithTag("notflix-wikipedia").assertDoesNotExist()
        compose.runOnIdle{s.active=false;extract=fixture()};compose.waitForIdle();compose.onNodeWithTag("notflix-wikipedia").assertDoesNotExist()
        compose.runOnIdle{assertTrue(s.actions.isEmpty())}
    }
    @Test fun maximumThreeParagraphsFitWithoutEllipsisAndCreditRemainsPresent() {
        val v=fixture("en");val p=(v.paragraphs.single()+" ").repeat(2).trim();extract=v.copy(paragraphs=listOf(p,p,p))
        assertTrue(extract!!.paragraphs.joinToString("\n\n").length<=900)
        show(DetailFixtureState());scrollToBlock();capture("bounded-text")
        for(index in 0..2) {
            val node=compose.onNodeWithTag("notflix-wikipedia-paragraph-$index");node.assertExists()
            val layouts=mutableListOf<TextLayoutResult>();node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertTrue(layouts.isNotEmpty());assertTrue(layouts.all{!it.hasVisualOverflow})
        }
        compose.onNodeWithTag("notflix-wikipedia-credit").assertExists();source().assertExists()
    }
    @Test fun seriesKeepsSeasonAndEpisodeEntryAndCanReadAndReturnFromSource() {
        extract=fixture(series=true);val s=DetailFixtureState(true);show(s)
        play().performSemanticsAction(SemanticsActions.RequestFocus);key(Key.DirectionRight)
        val library=compose.onNode(isFocusable() and hasClickAction() and (hasContentDescription("Add to library") or hasAnyDescendant(hasContentDescription("Add to library"))))
        library.assertIsFocused();key(Key.DirectionRight);compose.onNodeWithTag("notflix-detail-season-1").assertIsFocused()
        key(Key.DirectionLeft);play().assertIsFocused()
        scrollToBlock();source().performSemanticsAction(SemanticsActions.RequestFocus);key(Key.DirectionCenter)
        readToClose()
        key(Key.DirectionCenter);source().assertIsFocused()
        compose.runOnIdle{assertEquals(1,s.season);assertTrue(s.actions.isEmpty());assertTrue(s.progress.isEmpty())}
    }
    @Test fun sourceSectionIsReachableUsingOnlyDirectionalKeysFromMoviePlay() {
        extract=fixture();val s=DetailFixtureState();show(s)
        play().performSemanticsAction(SemanticsActions.RequestFocus)
        var reached=false
        for(step in 0..12) {
            if(runCatching{source().assertIsFocused();true}.getOrDefault(false)){reached=true;break}
            key(Key.DirectionDown)
        }
        if(!reached){compose.onRoot(useUnmergedTree=true).printToLog("NotflixWikipediaTree");capture("directional-failure")}
        assertTrue("Source must be reachable with the remote",reached)
        key(Key.DirectionCenter);readToClose()
        key(Key.DirectionCenter);source().assertIsFocused()
        compose.runOnIdle{assertTrue(s.actions.isEmpty());assertTrue(s.navigation.isEmpty())}
    }
}
