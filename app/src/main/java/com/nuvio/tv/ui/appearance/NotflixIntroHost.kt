package com.nuvio.tv.ui.appearance

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.nuvio.tv.R
import kotlinx.coroutines.delay

@Composable
internal fun NotflixIntroLifetime(owner: Any, coordinator: NotflixIntroCoordinator = NotflixIntroProcess.coordinator) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, owner, coordinator) {
        coordinator.attachHost(owner)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) coordinator.background()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); coordinator.sceneRemoved(owner) }
    }
}

@Composable
internal fun NotflixIntroScene(owner: Any, input: IntroDecisionInput,
    coordinator: NotflixIntroCoordinator = NotflixIntroProcess.coordinator,
    motionAllowed: Boolean = introMotionAllowed(), systemMediaBusy: Boolean? = null) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val context = LocalContext.current
    val busyAtEntry = remember(owner, input.binding) { systemMediaBusy ?: introSystemMediaBusy(context) }
    SideEffect { coordinator.evaluate(owner, input.copy(foreground = state.isAtLeast(Lifecycle.State.RESUMED),
        motionAllowed = motionAllowed, blocked = input.blocked || busyAtEntry)) }
    DisposableEffect(owner, coordinator) { onDispose { coordinator.sceneRemoved(owner) } }
}

/** Always the same wrapper/child slot. Intro has no focus target; the original destination retains focus. */
@Composable
internal fun NotflixIntroContentBoundary(
    coordinator: NotflixIntroCoordinator = NotflixIntroProcess.coordinator,
    content: @Composable () -> Unit
) {
    val phase by coordinator.state.collectAsState()
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        coordinator.key(key.action, key.keyCode, key.repeatCount)
    }.drawWithContent {
        // Beneath the official splash: keep the prepared destination from flashing
        // through its fade before the intro surface mounts. Layout/focus stay intact.
        if (phase is IntroPhase.Armed) drawRect(Color.Black) else drawContent()
    }.then(if (phase.lease() != null) Modifier.clearAndSetSemantics { } else Modifier)) { content() }
}

@Composable
internal fun NotflixIntroOverlay(
    owner: Any,
    coordinator: NotflixIntroCoordinator = NotflixIntroProcess.coordinator,
    decoderFactory: IntroDecoderFactory = LocalIntroDecoderFactory,
    deadlineMillis: Long = 5_000L
) {
    val phase by coordinator.state.collectAsState()
    val lease = phase.lease()
    LaunchedEffect(phase) {
        val label = when (phase) {
            IntroPhase.Waiting -> "waiting"
            is IntroPhase.Armed -> "armed"
            is IntroPhase.Running -> "running"
            is IntroPhase.Finished -> "finished reason=${(phase as IntroPhase.Finished).reason}"
        }
        Log.d("NotflixIntro", label)
    }
    // Bound the official-splash wait separately. The mounted overlay gets at most
    // five seconds including decoder preparation; official fade must not truncate the asset.
    LaunchedEffect(lease?.id, phase is IntroPhase.Running) {
        if (lease != null) { delay(deadlineMillis.coerceIn(1L, 5_000L)); coordinator.finish(IntroFinishReason.DEADLINE) }
    }
    BackHandler(enabled = lease != null) { coordinator.finish(IntroFinishReason.SKIPPED) }
    if (phase is IntroPhase.Running && lease?.owner === owner) {
        val skip = stringResource(R.string.notflix_intro_skip)
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("notflix_intro_overlay")
            .pointerInput(lease) { detectTapGestures { coordinator.finish(IntroFinishReason.SKIPPED) } }
            .clearAndSetSemantics {
                contentDescription = skip
                role = Role.Button
                onClick(label = skip) { coordinator.finish(IntroFinishReason.SKIPPED); true }
            }) {
            IntroMediaSurface(lease, coordinator, decoderFactory)
            // M29 (promoter): no visible "Skip intro" label; Back, tap and the accessibility action still skip.
        }
    }
}

@Composable
private fun IntroMediaSurface(lease: IntroLease, coordinator: NotflixIntroCoordinator, factory: IntroDecoderFactory) {
    val context = LocalContext.current
    val view = remember(lease) { createIntroPlayerView(context) }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    DisposableEffect(lease, view, coordinator, factory) {
        var handle: IntroHandle? = null
        if (coordinator.isCurrent(lease)) {
            try {
                val owned = OnceIntroHandle(factory.create(view, lease.sound,
                    { coordinator.finish(IntroFinishReason.COMPLETE) }, { coordinator.finish(IntroFinishReason.ERROR) }))
                handle = owned
                coordinator.registerCleanup(lease, owned::close)
            } catch (_: Exception) { coordinator.finish(IntroFinishReason.ERROR) }
        }
        onDispose { handle?.close() }
    }
}
