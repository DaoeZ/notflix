package com.nuvio.tv.ui.screens.detail

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.domain.model.*
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real MetaDetailsContent/Hero, declared synthetic values/callbacks. No Hilt/store/API/player. */
@OptIn(ExperimentalTestApi::class)
class NotflixRatingsPresentationTest {
    @get:Rule val compose=createComposeRule()
    private val five=MDBListRatings(imdb=8.1,trakt=82.0,tomatoes=91.0,letterboxd=4.2,metacritic=77.0)
    private val fiveIds=listOf("imdb","trakt","tomatoes","letterboxd","metacritic")
    private fun show(s:DetailFixtureState,spanish:Boolean=false) {
        compose.setContent {
            val base=LocalContext.current
            val context=base.createConfigurationContext(Configuration(base.resources.configuration).apply{setLocale(if(spanish)Locale("es")else Locale.US)})
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides context.resources.configuration){DetailFixture(s)}
        };compose.waitForIdle()
    }
    private fun withFive(series:Boolean=false)=DetailFixtureState(series).apply{mdbRatings=five;mdbActive=true}
    private fun badge(id:String)=compose.onNodeWithTag("notflix-rating-$id")
    private fun play()=compose.onNode(isFocusable() and hasClickAction() and (hasText("Play") or hasAnyDescendant(hasText("Play"))))
    private fun assertVisible(ids:List<String>) {
        val stage=compose.onNodeWithTag("notflix_detail_stage").getUnclippedBoundsInRoot()
        for(id in ids) {
            badge(id).assertIsDisplayed()
            val box=badge(id).getUnclippedBoundsInRoot()
            assertTrue("$id clipped horizontally",box.left>=stage.left && box.right<=stage.right)
            assertTrue("$id clipped vertically",box.top>=stage.top && box.bottom<=stage.bottom)
            val layouts=mutableListOf<TextLayoutResult>()
            badge(id).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertTrue(layouts.isNotEmpty());assertTrue("$id text overflow",layouts.all{!it.hasVisualOverflow})
            badge(id).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        }
        play().assertIsDisplayed()
    }
    private fun capture(label:String) {
        val ins=InstrumentationRegistry.getInstrumentation()
        val run=InstrumentationRegistry.getArguments().getString("nf_m16_run")?:"ratings-r1"
        require(Regex("ratings-r[0-9]+").matches(run))
        val dir=File(requireNotNull(ins.targetContext.getExternalFilesDir(null)),"nf003-m16/$run/$label-${UUID.randomUUID()}")
        check(dir.mkdirs());val bitmap=requireNotNull(ins.uiAutomation.takeScreenshot())
        File(dir,"screen.png").outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        Log.d("NotflixRatingsTest","artifact_dir=${dir.absolutePath}")
    }
    @Test fun fiveMovieScoresFitWithNativeScalesNoDuplicateImdbAndUsefulActions() {
        val s=withFive();show(s);capture("five-movie")
        assertVisible(fiveIds);badge("imdb").assertTextEquals("IMDb 8.1/10")
        badge("tomatoes").assertContentDescriptionEquals("Rotten Tomatoes · Critics 91%")
        badge("letterboxd").assertTextEquals("Letterboxd 4.2/5")
        compose.onAllNodesWithTag("notflix-rating-imdb").assertCountEquals(1)
        compose.onNodeWithText("7.6 IMDb").assertDoesNotExist()
        play().performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        compose.runOnIdle{assertTrue(s.actions.isEmpty());assertFalse(s.overlay)}
    }
    @Test fun fiveSeriesScoresKeepEpisodePanelAndOfficialFocusReturn() {
        val s=withFive(true);show(s);capture("five-series");assertVisible(fiveIds)
        play().performSemanticsAction(SemanticsActions.RequestFocus)
        play().performKeyInput{pressKey(Key.DirectionRight)};compose.waitForIdle()
        val library=compose.onNode(isFocusable() and hasClickAction() and
            (hasContentDescription("Add to library") or hasAnyDescendant(hasContentDescription("Add to library"))))
        library.assertIsFocused().performKeyInput{pressKey(Key.DirectionRight)};compose.waitForIdle()
        compose.onNodeWithTag("notflix-detail-season-1").assertIsFocused()
        compose.onNodeWithTag("notflix-detail-season-1").performKeyInput{pressKey(Key.DirectionLeft)}
        play().assertIsFocused();compose.runOnIdle{assertEquals(1,s.season);assertTrue(s.actions.isEmpty());assertTrue(s.progress.isEmpty())}
    }
    @Test fun eightSourcesIncludingExtrasWrapWithoutClippingOrTakingFocus() {
        val s=withFive(true).apply{mdbRatings=five.copy(tmdb=79.0,audience=87.0,mal=7.5)}
        show(s);capture("eight-series");assertVisible(MDBListSettings.DEFAULT_RATING_ORDER)
        badge("audience").assertContentDescriptionEquals("Rotten Tomatoes · Audience 87%")
        badge("tmdb").assertTextEquals("TMDB 79/100");badge("mal").assertTextEquals("MyAnimeList 7.5/10")
    }
    @Test fun officialFilteredOrderHidesImdbWithoutStandardFallbackLeak() {
        val s=withFive().apply{mdbOrder=listOf("metacritic","letterboxd","tomatoes")};show(s)
        badge("imdb").assertDoesNotExist();badge("trakt").assertDoesNotExist()
        assertVisible(s.mdbOrder)
        val a=badge("metacritic").getUnclippedBoundsInRoot();val b=badge("letterboxd").getUnclippedBoundsInRoot()
        assertTrue(a.top<b.top || a.left<b.left)
        compose.onNodeWithText("7.6 IMDb").assertDoesNotExist()
    }
    @Test fun activeButMissingDataDoesNotShowZeroOrAnEmptyRatingsBlock() {
        val s=DetailFixtureState().apply{mdbActive=true};show(s)
        compose.onNodeWithTag("notflix-detail-ratings").assertDoesNotExist();play().assertIsDisplayed()
        compose.runOnIdle{assertTrue(s.actions.isEmpty())}
    }
    @Test fun disabledProviderIgnoresResidualDataAndKeepsOnlyPermittedStandardScore() {
        val s=DetailFixtureState().apply{mdbRatings=five;mdbActive=false};show(s)
        badge("imdb").assertTextEquals("IMDb 7.6/10");badge("trakt").assertDoesNotExist()
        compose.onNodeWithText("Ratings · MDBList").assertDoesNotExist()
    }
    @Test fun hiddenStandardRatingsDoNotCreateAnyFallback() {
        val s=DetailFixtureState().apply{overallRatings=HomeImdbRatingsVisibility.HIDE_ALL;tmdbRating=8.2f};show(s)
        compose.onNodeWithTag("notflix-detail-ratings").assertDoesNotExist();play().assertIsDisplayed()
    }
    @Test fun partialDataAndRefreshReplaceOldScoresWithoutChangingCallbacks() {
        val s=withFive();show(s)
        compose.runOnIdle{s.mdbRatings=MDBListRatings(metacritic=0.0,letterboxd=Double.NaN)};compose.waitForIdle()
        badge("metacritic").assertTextEquals("Metacritic 0/100")
        listOf("imdb","trakt","letterboxd","tomatoes").forEach{badge(it).assertDoesNotExist()}
        play().performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        compose.runOnIdle{assertTrue(s.actions.isEmpty());assertTrue(s.progress.isEmpty())}
    }
    @Test fun spanishLabelsAndDecimalScaleFitTheSameProductComponent() {
        val s=withFive();show(s,true);capture("five-spanish");assertVisible(fiveIds)
        badge("imdb").assertContentDescriptionEquals("IMDb 8,1/10")
        badge("tomatoes").assertContentDescriptionEquals("Rotten Tomatoes · Crítica 91%")
        badge("letterboxd").assertTextEquals("Letterboxd 4,2/5")
        compose.onNodeWithText("Valoraciones · MDBList").assertExists()
    }
    @Test fun nuvioPresentationKeepsItsSharedRatingRowAndDoesNotMountNotflixBlock() {
        val s=withFive().apply{active=false};show(s)
        compose.onNodeWithTag("notflix-detail-ratings").assertDoesNotExist()
        compose.onNodeWithTag("notflix_detail_stage").assertDoesNotExist()
        compose.onNodeWithText("8.1").assertExists();compose.runOnIdle{assertTrue(s.actions.isEmpty())}
    }
}
