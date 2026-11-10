package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.MetaPreview
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal data class PreviewBinding(val context: AppearanceContext, val confirmation: Long)
internal data class PreviewVisit(val binding: PreviewBinding, val identity: String, val canonical: String, val epoch: Long)
internal data class PreviewCandidate(val visit: PreviewVisit, val item: MetaPreview, val expanded: Boolean)
internal data class PreviewEnvironment(
    val binding: PreviewBinding? = null, val enabled: Boolean = false, val sound: Boolean = false,
    val valid: Boolean = false, val reduced: Boolean = true, val resumed: Boolean = false,
    val windowFocused: Boolean = false, val introActive: Boolean = true,
    val blocked: Boolean = false, val otherVideo: Boolean = false
)
internal data class PreviewSource(val video: String, val audio: String? = null) {
    fun valid(): Boolean = validUrl(video) && (audio == null || validUrl(audio))
    private fun validUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
internal data class PreviewLease(val visit: PreviewVisit, val serial: Long, val item: MetaPreview, val sound: Boolean)
internal enum class PreviewEnd { FOCUS, NAVIGATION, BLOCKED, CONTEXT, ABSENT, ERROR, COMPLETE, TIMEOUT, DISPOSED }
internal sealed interface PreviewPhase {
    data object Idle : PreviewPhase
    data class Waiting(val visit: PreviewVisit) : PreviewPhase
    data class Resolving(val lease: PreviewLease) : PreviewPhase
    data class Playing(val lease: PreviewLease, val source: PreviewSource, val firstFrame: Boolean = false) : PreviewPhase
    data class Finished(val visit: PreviewVisit, val reason: PreviewEnd) : PreviewPhase
}
internal fun PreviewPhase.lease(): PreviewLease? = when (this) {
    is PreviewPhase.Resolving -> lease
    is PreviewPhase.Playing -> lease
    else -> null
}
internal interface NotflixPreviewResolver {
    suspend fun resolve(lease: PreviewLease, authorized: () -> Boolean): PreviewSource?
    fun cancel(lease: PreviewLease)
}

/** One attempt per actual focus visit. No routes, playback progress, preferences or player is owned here. */
internal class NotflixPreviewCoordinator(
    private val scope: CoroutineScope,
    private val resolver: NotflixPreviewResolver,
    private val dwellMillis: Long = 600L,
    private val stallMillis: Long = 15_000L
) {
    private val mutable = MutableStateFlow<PreviewPhase>(PreviewPhase.Idle)
    val state: StateFlow<PreviewPhase> = mutable
    private var environment = PreviewEnvironment()
    private var candidate: PreviewCandidate? = null
    private var dwell: Job? = null
    private var resolving: Job? = null
    private var guard: Job? = null
    private var cleanup: (() -> Unit)? = null
    private var serial = 0L
    private var dwelled = false
    private var spent = false
    private var disposed = false
    private var lastPosition = -1L
    private val audible = MutableStateFlow<Pair<Long, Boolean>?>(null)
    /** Ephemeral sound choice of one lease (serial to audible); never a preference, cleared when the lease ends. */
    val audibleOverride: StateFlow<Pair<Long, Boolean>?> = audible
    var cleanupFailed = false
        private set

    fun updateEnvironment(next: PreviewEnvironment) {
        if (disposed) return
        environment = next
        val lease = state.value.lease()
        if (lease != null && (!allowed(lease.visit) || lease.sound != next.sound)) {
            finish(lease, if (next.binding != lease.visit.binding) PreviewEnd.CONTEXT else PreviewEnd.BLOCKED)
        } else tryStart()
    }

    private var immediateIdentity: String? = null
    private var suppressIdentity: String? = null

    fun updateCandidate(next: PreviewCandidate?) {
        if (disposed) return
        if (next?.visit != candidate?.visit) {
            state.value.lease()?.let { finish(it, PreviewEnd.FOCUS) }
            dwell?.cancel(); dwell = null
            val immediate = next?.visit?.identity == immediateIdentity
            val suppressed = next?.visit?.identity == suppressIdentity
            if (next != null) immediateIdentity = null
            if (next != null && suppressed) suppressIdentity = null
            candidate = next; dwelled = immediate; spent = suppressed; lastPosition = -1L
            mutable.value = if (suppressed) PreviewPhase.Idle
                else next?.let { PreviewPhase.Waiting(it.visit) } ?: PreviewPhase.Idle
            if (next != null && !suppressed) {
                if (immediate) tryStart()
                else dwell = scope.launch {
                    delay(dwellMillis.coerceAtLeast(0))
                    if (candidate?.visit == next.visit && !disposed) { dwelled = true; tryStart() }
                }
            }
        } else { candidate = next; tryStart() }
    }

    private fun allowed(visit: PreviewVisit): Boolean = with(environment) {
        binding == visit.binding && enabled && valid && !reduced && resumed && windowFocused &&
            !introActive && !blocked && !otherVideo
    }

    private fun tryStart() {
        val target = candidate ?: return
        if (disposed || spent || !dwelled || !target.expanded || !allowed(target.visit)) return
        spent = true
        val lease = PreviewLease(target.visit, ++serial, target.item, environment.sound)
        mutable.value = PreviewPhase.Resolving(lease)
        resetGuard(lease)
        resolving = scope.launch {
            try {
                val source = resolver.resolve(lease) { isCurrent(lease) && allowed(lease.visit) }
                currentCoroutineContext().ensureActive()
                if (!isCurrent(lease) || !allowed(lease.visit)) return@launch
                if (source?.valid() == true) mutable.value = PreviewPhase.Playing(lease, source)
                else finish(lease, PreviewEnd.ABSENT)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { finish(lease, PreviewEnd.ERROR) }
        }
    }

    fun isCurrent(lease: PreviewLease): Boolean = !disposed && state.value.lease() == lease

    /** Última posición conocida del tráiler; 0 si aún no ha emitido progreso. */
    fun lastKnownPosition(): Long = lastPosition.coerceAtLeast(0L)

    /**
     * La próxima visita de esta tarjeta no arranca sola (el tráiler terminó en la vista grande):
     * evita que el regreso del foco reproduzca otra vez lo que el usuario acaba de terminar.
     */
    fun suppressNextVisit(identity: String) {
        if (disposed) return
        suppressIdentity = identity
        immediateIdentity = null
    }

    /**
     * Reabre la visita de la tarjeta; si aún no hay candidato, la próxima visita con la misma
     * identidad arranca sin volver a esperar la permanencia (regreso de la vista grande).
     */
    fun restartVisit(identity: String) {
        if (disposed) return
        immediateIdentity = identity
        suppressIdentity = null
        val target = candidate ?: return
        if (target.visit.identity != identity) return
        immediateIdentity = null
        spent = false
        dwelled = true
        lastPosition = -1L
        mutable.value = PreviewPhase.Waiting(target.visit)
        tryStart()
    }

    /** Effective sound of the lease: this visit's choice, else the official profile value it started with. */
    fun audible(lease: PreviewLease): Boolean = audible.value?.takeIf { it.first == lease.serial }?.second ?: lease.sound

    /** Only a playing current lease accepts the toggle; no restart, resolution or preference write. */
    fun setAudible(lease: PreviewLease, value: Boolean): Boolean {
        if (!isCurrent(lease) || state.value !is PreviewPhase.Playing) return false
        audible.value = lease.serial to value
        return true
    }

    fun registerCleanup(lease: PreviewLease, close: () -> Unit) {
        if (isCurrent(lease) && cleanup == null) cleanup = close else closeSafely(close)
    }

    fun firstFrame(lease: PreviewLease) {
        val phase = state.value as? PreviewPhase.Playing ?: return
        if (!isCurrent(lease) || phase.firstFrame) return
        mutable.value = phase.copy(firstFrame = true)
        resetGuard(lease)
    }

    fun progress(lease: PreviewLease, position: Long) {
        val playing = state.value as? PreviewPhase.Playing ?: return
        if (!isCurrent(lease) || !playing.firstFrame || position <= lastPosition) return
        lastPosition = position
        // Buffering or a paused decoder cannot retain this owner indefinitely.
        resetGuard(lease)
    }

    private fun resetGuard(lease: PreviewLease) {
        guard?.cancel()
        guard = scope.launch { delay(stallMillis.coerceAtLeast(1)); finish(lease, PreviewEnd.TIMEOUT) }
    }

    fun cancelVisit(reason: PreviewEnd = PreviewEnd.NAVIGATION) {
        spent = true
        state.value.lease()?.let { finish(it, reason) }
    }

    fun finish(lease: PreviewLease, reason: PreviewEnd): Boolean {
        if (!isCurrent(lease)) return false
        mutable.value = PreviewPhase.Finished(lease.visit, reason)
        audible.value = null
        spent = true
        resolving?.cancel(); resolving = null
        guard?.cancel(); guard = null
        val release = cleanup; cleanup = null
        release?.let(::closeSafely)
        runCatching { resolver.cancel(lease) }.onFailure { cleanupFailed = true }
        return true
    }

    fun dispose() {
        if (disposed) return
        state.value.lease()?.let { finish(it, PreviewEnd.DISPOSED) }
        dwell?.cancel(); dwell = null; candidate = null; disposed = true
    }

    private fun closeSafely(close: () -> Unit) {
        try { close() } catch (_: Exception) { cleanupFailed = true }
    }
}

internal fun previewShouldMute(sound: Boolean, mediaSilent: Boolean, ringerSilent: Boolean, interruptionSilent: Boolean): Boolean =
    !sound || mediaSilent || ringerSilent || interruptionSilent
