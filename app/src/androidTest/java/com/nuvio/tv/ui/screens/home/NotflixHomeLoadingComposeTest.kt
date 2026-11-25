package com.nuvio.tv.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Offline tests of the production renderer, including actual pixels rather than alpha-blind semantics. */
class NotflixHomeLoadingComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun panelIsOpaqueDuringOpeningAndAnimationDoesNotRecomposeTheCard() {
        compose.mainClock.autoAdvance = false
        var open by mutableStateOf(false)
        val compositions = AtomicInteger()
        val meta = MetaPreview(id = "m69", type = ContentType.MOVIE, name = "Addon title",
            poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
            description = "Overview", releaseInfo = "2026", imdbRating = null, genres = listOf("Drama"))
        compose.setContent {
            CompositionLocalProvider(
                LocalNotflixPresentation provides NotflixPresentation(true, reduceHomeMotion = false),
                LocalNotflixCompositionObserver provides { compositions.incrementAndGet(); Unit }
            ) {
                NotflixCard(NotflixTile("m69", "movie:m69", meta.name, poster = null, meta = meta), "m69", false, open,
                    Modifier, null, {},
                    actions = listOf(NotflixCardActionUi(NotflixCardAction.PLAY, "Play", null, true, Icons.Default.PlayArrow)))
            }
        }
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        compose.runOnIdle { open = true }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        val action = compose.onNodeWithTag("notflix-card-action-m69-play", true)
        action.assertIsDisplayed()
        val pixels = action.captureToImage().toPixelMap()
        val color = pixels[pixels.width / 2, pixels.height / 4]
        assertTrue("The white Play button must already be opaque during the opening", color.red > .8f && color.green > .8f)
        val count = compositions.get()
        repeat(6) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle() }
        assertEquals("Animation reads belong to layout/draw, not composition", count, compositions.get())
    }

    @Test fun fastRemoteFocusMovesDoNotRecomposeTheHomeRoot() {
        val state = PreviewFixtureState().apply { environment = environment.copy(enabled = false, reduced = true) }
        val roots = AtomicInteger()
        compose.setContent {
            CompositionLocalProvider(LocalNotflixCompositionObserver provides { scope ->
                if (scope == "home") roots.incrementAndGet()
            }) { PreviewFixture(state) }
        }
        compose.onNodeWithTag(state.tag()).assertIsFocused()
        compose.mainClock.autoAdvance = false
        val start = roots.get()
        fun focusedWithinFrames(tag: String) {
            repeat(20) {
                if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
                        it.config.getOrElseNullable(SemanticsProperties.Focused) { false } == true }) return
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            }
            compose.onNodeWithTag(tag).assertIsFocused()
        }
        repeat(3) {
            compose.onNodeWithTag(state.tag()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }
            focusedWithinFrames(state.tag(0, 1))
            compose.onNodeWithTag(state.tag(0, 1)).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionLeft) }
            focusedWithinFrames(state.tag())
        }
        compose.onNodeWithTag(state.tag()).assertIsFocused()
        assertEquals(start, roots.get())
    }

    private fun image(color: Int, name: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "m69-$name.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    @Test fun backdropRetainsDecodedPixelsWhileLoadingAndClearsOnErrorMissingArtAndProfileChange() {
        val red = image(android.graphics.Color.RED, "red")
        val blue = image(android.graphics.Color.BLUE, "blue")
        val green = image(android.graphics.Color.GREEN, "green")
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val greenGate = CompletableDeferred<Unit>()
        val greenStarted = CompletableDeferred<Unit>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = ImageLoader.Builder(context).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    if (chain.request.data == blue) { started.complete(Unit); gate.await() }
                    if (chain.request.data == green) { greenStarted.complete(Unit); greenGate.await() }
                    return chain.proceed()
                }
            })
        }.build()
        var url by mutableStateOf<String?>(red)
        var profile by mutableIntStateOf(1)
        compose.setContent {
            // Black host: the test window background is light, so "no art" must be told apart from it.
            androidx.compose.foundation.layout.Box(Modifier.size(64.dp).background(androidx.compose.ui.graphics.Color.Black).testTag("m69-backdrop")) {
                NotflixRetainedBackdrop(url, profile, true, Modifier.matchParentSize(), imageLoader = loader)
            }
        }
        fun color() = compose.onNodeWithTag("m69-backdrop").captureToImage().toPixelMap()[8, 8]
        fun blank() = color().let { it.red < .1f && it.green < .1f && it.blue < .1f }
        try {
            compose.waitUntil(10_000) { color().red > .9f }
            compose.runOnIdle { url = blue }
            compose.waitUntil(10_000) { started.isCompleted }
            assertTrue("Loading keeps the previous decoded art instead of a black frame", color().red > .9f)
            compose.runOnIdle { url = "file:///m69-does-not-exist.png" }
            compose.waitUntil(10_000) { blank() }
            gate.complete(Unit); compose.waitForIdle()
            assertTrue("A superseded result must not replace the current backdrop", blank())
            compose.runOnIdle { url = red }
            compose.waitUntil(10_000) { color().red > .9f }
            compose.runOnIdle { url = null }
            compose.waitUntil(10_000) { blank() }
            compose.runOnIdle { url = red }
            compose.waitUntil(10_000) { color().red > .9f }
            compose.runOnIdle { url = green }
            compose.waitUntil(10_000) { greenStarted.isCompleted }
            assertTrue(color().red > .9f)
            compose.runOnIdle { profile = 2 }
            compose.waitForIdle()
            assertTrue("Another profile must not retain the old decoded art", blank())
        } finally {
            gate.complete(Unit)
            greenGate.complete(Unit)
            loader.shutdown()
        }
    }
}
