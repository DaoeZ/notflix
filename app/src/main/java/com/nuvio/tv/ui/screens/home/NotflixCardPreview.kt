package com.nuvio.tv.ui.screens.home

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.ViewTreeObserver
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.core.player.ExternalPlaybackTracker
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.ui.appearance.AppearanceState
import com.nuvio.tv.ui.appearance.IntroPhase
import com.nuvio.tv.ui.appearance.LocalAppearanceViewModel
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixIntroProcess
import com.nuvio.tv.ui.components.LocalStartupLoadingState
import com.nuvio.tv.ui.components.LocalStartupSplashEnabled
import com.nuvio.tv.ui.components.TrailerPlayer
import java.util.concurrent.atomic.AtomicBoolean

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
private interface NotflixPreviewPlaybackEntryPoint {
    fun externalPlaybackTracker(): ExternalPlaybackTracker
}

internal sealed interface PreviewSoundUi {
    data object Loading : PreviewSoundUi
    data class Ready(val audible: Boolean, val systemSilenced: Boolean) : PreviewSoundUi
}

internal interface NotflixPreviewRenderer {
    @Composable fun Content(
        phase: PreviewPhase.Playing,
        coordinator: NotflixPreviewCoordinator,
        modifier: Modifier,
        startPositionMs: Long = 0L
    )
}

/** Presentation/session handle. Production and tests supply different resolvers/renderers, not different card UIs. */
class NotflixPreviewSession internal constructor(
    internal val binding: PreviewBinding?,
    internal val coordinator: NotflixPreviewCoordinator,
    private val renderer: NotflixPreviewRenderer,
    private val liveBlocked: () -> Boolean = { false }
) {
    private var host by mutableStateOf(PreviewEnvironment())
    private var resumePositionMs = 0L
    private var resumeIdentity: String? = null
    internal val billboardReady: Boolean get() = host.billboardReady(runCatching(liveBlocked).getOrDefault(true))
    private var cardBlocked = false
    internal val ownsVideo: Boolean get() = coordinator.state.value is PreviewPhase.Playing

    internal fun isPlaying(identity: String): Boolean {
        val phase = coordinator.state.value as? PreviewPhase.Playing ?: return false
        return phase.lease.visit.identity == identity && coordinator.isCurrent(phase.lease)
    }

    internal fun playingSource(identity: String): PreviewSource? {
        val phase = coordinator.state.value as? PreviewPhase.Playing ?: return null
        return phase.source.takeIf { phase.lease.visit.identity == identity && coordinator.isCurrent(phase.lease) }
    }

    internal fun currentPositionMs(): Long = coordinator.lastKnownPosition()

    /** Reabre la visita de la tarjeta y arranca desde la posición guardada (vista grande). */
    internal fun restartWithPosition(identity: String, positionMs: Long) {
        resumePositionMs = positionMs.coerceAtLeast(0L)
        resumeIdentity = identity
        coordinator.restartVisit(identity)
    }

    /** Evita que el regreso del foco reproduzca otra vez un tráiler terminado en la vista grande. */
    internal fun suppressNextVisit(identity: String) = coordinator.suppressNextVisit(identity)

    /** Tráiler reproduciéndose ahora mismo para esta tarjeta (borde del foco de arte). */
    @Composable
    internal fun artworkPlaying(identity: String): Boolean {
        val phase by coordinator.state.collectAsState()
        val playing = phase as? PreviewPhase.Playing ?: return false
        return playing.lease.visit.identity == identity && coordinator.isCurrent(playing.lease)
    }

    internal fun environment(next: PreviewEnvironment) {
        host = next
        coordinator.updateEnvironment(combined(next))
    }
    private fun combined(value: PreviewEnvironment) = value.copy(blocked = value.blocked || cardBlocked ||
        runCatching(liveBlocked).getOrDefault(true))
    internal fun card(candidate: PreviewCandidate?, blocked: Boolean) {
        cardBlocked = blocked
        coordinator.updateEnvironment(combined(host))
        coordinator.updateCandidate(candidate)
    }
    internal fun cancel() = coordinator.cancelVisit()
    internal fun dispose() = coordinator.dispose()

    /** Honest sound state of this card's visit: null without a preview, Loading until the first frame. */
    @Composable
    internal fun sound(identity: String): PreviewSoundUi? {
        val phase by coordinator.state.collectAsState()
        val chosen by coordinator.audibleOverride.collectAsState()
        val context = LocalContext.current
        val lease = phase.lease()?.takeIf { it.visit.identity == identity && coordinator.isCurrent(it) } ?: return null
        val playing = phase as? PreviewPhase.Playing
        if (playing == null || !playing.firstFrame) return PreviewSoundUi.Loading
        val silenced = remember(lease) { previewSystemMuted(context, true) }
        val audible = chosen?.takeIf { it.first == lease.serial }?.second ?: lease.sound
        return PreviewSoundUi.Ready(audible, silenced)
    }

    internal fun toggleSound(identity: String): Boolean {
        val lease = (coordinator.state.value as? PreviewPhase.Playing)?.lease?.takeIf { it.visit.identity == identity } ?: return false
        return coordinator.setAudible(lease, !coordinator.audible(lease))
    }

    @Composable
    internal fun Artwork(identity: String, modifier: Modifier) {
        val phase by coordinator.state.collectAsState()
        val playing = phase as? PreviewPhase.Playing
        if (playing != null && playing.lease.visit.identity == identity && coordinator.isCurrent(playing.lease)) {
            val startPosition = remember(playing.lease) {
                // La posición pendiente solo vale para la visita reiniciada; nunca para otro tráiler.
                val pending = if (resumeIdentity == playing.lease.visit.identity) resumePositionMs else 0L
                resumePositionMs = 0L
                resumeIdentity = null
                pending
            }
            renderer.Content(playing, coordinator, modifier, startPosition)
        }
    }
}

@Composable
internal fun rememberNotflixPreviewSession(model: HomeViewModel, state: HomeUiState, dialog: Boolean): NotflixPreviewSession {
    val resolver = remember(model) { OfficialNotflixPreviewResolver(model) }
    val route = LocalNotflixPresentation.current.navigation?.selectedRoute
    return rememberNotflixPreviewSession(
        resolver = resolver,
        dialog = dialog,
        routeActive = route == "home",
        validContext = { context ->
            state.notflixCatalog.configured && state.notflixCatalog.context == context.profileId
        }
    )
}

/**
 * M63: la misma sesión/coordinador para otra pantalla Notflix. El resolvedor lo pone la pantalla
 * (Inicio o carpeta de colección); la validez de apariencia, perfil y ruta se comprueba igual.
 */
@Composable
internal fun rememberNotflixPreviewSession(
    resolver: NotflixPreviewResolver,
    dialog: Boolean,
    routeActive: Boolean,
    validContext: (com.nuvio.tv.domain.model.AppearanceContext) -> Boolean,
    lockContext: Boolean = false
): NotflixPreviewSession {
    val appearance = LocalAppearanceViewModel.current?.let { it.state.collectAsStateWithLifecycle().value } ?: AppearanceState()
    val presentation = LocalNotflixPresentation.current
    // M56: a slow opening also delays the clip; the preview never starts under a growing card.
    val previewDwell = com.nuvio.tv.ui.appearance.NotflixVisualSpec.previewDwellMs(presentation.cardOpening)
    val pool = LocalTrailerPlayerPool.current
    val poolPlaying = pool?.let { it.isTrailerPlaying.collectAsState().value } ?: false
    val application = LocalContext.current.applicationContext
    val external = remember(application) {
        runCatching { dagger.hilt.android.EntryPointAccessors.fromApplication(application,
            NotflixPreviewPlaybackEntryPoint::class.java).externalPlaybackTracker() }.getOrNull()
    }
    val externalOverlay = external?.let { it.autoNextOverlay.collectAsState().value }
    val intro by NotflixIntroProcess.coordinator.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsState()
    val view = LocalView.current
    var windowFocused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    val binding = appearance.context?.let { PreviewBinding(it, appearance.selectionGeneration) }
    val entryContext = remember(resolver) { appearance.context }
    val scope = rememberCoroutineScope()
    val session = remember(resolver, pool, binding, external, previewDwell) {
        NotflixPreviewSession(binding,
            NotflixPreviewCoordinator(scope, resolver, dwellMillis = previewDwell),
            OfficialNotflixPreviewRenderer(pool),
            liveBlocked = { external == null || external.isTracking || external.autoNextOverlay.value != null })
    }
    DisposableEffect(session, view, owner) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            windowFocused = focused
            if (!focused) session.cancel()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) session.cancel()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
            session.dispose()
        }
    }
    val startupReady = !LocalStartupSplashEnabled.current || LocalStartupLoadingState.current?.complete == true
    val environment = PreviewEnvironment(binding,
        enabled = appearance.previewEnabled, sound = appearance.previewSoundEnabled,
        valid = presentation.active && appearance.effective == AppAppearance.NOTFLIX && !appearance.readFailed &&
            appearance.previewOptionsValid && binding != null && validContext(binding!!.context) &&
            (!lockContext || binding.context == entryContext),
        reduced = presentation.reduceHomeMotion,
        resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED), windowFocused = windowFocused,
        introActive = intro !is IntroPhase.Finished,
        blocked = dialog || pool == null || !startupReady || !routeActive,
        otherVideo = externalOverlay != null || (poolPlaying && !session.ownsVideo))
    SideEffect { session.environment(environment) }
    return session
}

private fun previewSystemMuted(context: Context, sound: Boolean): Boolean = runCatching {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    previewShouldMute(sound,
        audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0,
        audio.ringerMode != AudioManager.RINGER_MODE_NORMAL,
        notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
}.getOrDefault(true)

/** Reuses both official player component and official pool. No player/resolver implementation is copied. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class OfficialNotflixPreviewRenderer(private val pool: TrailerPlayerPool?) : NotflixPreviewRenderer {
    @Composable
    override fun Content(
        phase: PreviewPhase.Playing,
        coordinator: NotflixPreviewCoordinator,
        modifier: Modifier,
        startPositionMs: Long
    ) {
        val lease = phase.lease
        val context = LocalContext.current
        val muted = remember(lease) { previewSystemMuted(context, lease.sound) }
        val player = remember(lease, pool) { if (coordinator.isCurrent(lease)) pool?.acquire() else null }
        // The visit's choice changes only the pooled player's volume: TrailerPlayer's muted key would re-prepare the media.
        val override by coordinator.audibleOverride.collectAsState()
        val chosen = override?.takeIf { it.first == lease.serial }?.second
        LaunchedEffect(player, chosen) {
            if (player != null && chosen != null && coordinator.isCurrent(lease)) {
                player.volume = if (previewSystemMuted(context, chosen)) 0f else 1f
                Log.d("NotflixPreview", "sound lease=${lease.serial} audible=$chosen volume=${player.volume}")
            }
        }
        DisposableEffect(player, lease) {
            val closed = AtomicBoolean(false)
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.d("NotflixPreview", "error lease=${lease.serial} code=${error.errorCode}")
                    coordinator.finish(lease, PreviewEnd.ERROR)
                }
            }
            val close = {
                if (closed.compareAndSet(false, true)) {
                    player?.removeListener(listener)
                    pool?.stop()
                    Log.d("NotflixPreview", "stop lease=${lease.serial} media_count=${player?.mediaItemCount ?: 0}")
                }
                Unit
            }
            if (player == null) coordinator.finish(lease, PreviewEnd.ERROR)
            else {
                player.addListener(listener)
                coordinator.registerCleanup(lease, close)
                Log.d("NotflixPreview", "mount lease=${lease.serial} pool=true muted=$muted sound_pref=${lease.sound}")
            }
            onDispose { close() }
        }
        if (player != null) TrailerPlayer(
            trailerUrl = phase.source.video, trailerAudioUrl = phase.source.audio, isPlaying = true,
            onEnded = { coordinator.finish(lease, PreviewEnd.COMPLETE) },
            onFirstFrameRendered = {
                Log.d("NotflixPreview", "first_frame lease=${lease.serial} position_ms=${player.currentPosition}")
                coordinator.firstFrame(lease)
            },
            onProgressChanged = { position, _ -> coordinator.progress(lease, position) },
            muted = muted, cropToFill = true, overscanZoom = 1f, autoCropLetterbox = false,
            startPositionMs = startPositionMs,
            modifier = modifier, enter = EnterTransition.None, exit = ExitTransition.None, trailerPlayerPool = pool)
    }
}
