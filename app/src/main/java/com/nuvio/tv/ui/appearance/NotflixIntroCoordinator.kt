package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AppearanceContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal data class IntroBinding(val storage: AppearanceContext, val selectionGeneration: Long)
internal data class IntroDecisionInput(
    val resolved: Boolean = false,
    val binding: IntroBinding? = null,
    val surfaceReady: Boolean = false,
    val notflix: Boolean = false,
    val optionsValid: Boolean = false,
    val enabled: Boolean = false,
    val sound: Boolean = false,
    val blocked: Boolean = false,
    val motionAllowed: Boolean = true,
    val foreground: Boolean = true
)
internal enum class IntroFinishReason { NUVIO, DISABLED, INVALID_OPTIONS, EXTERNAL_OR_VIDEO,
    SYSTEM_MOTION, COMPLETE, SKIPPED, ERROR, DEADLINE, BACKGROUND, CONTEXT_CHANGED, HOST_DISPOSED, RECREATED }
internal data class IntroLease(val owner: Any, val binding: IntroBinding, val sound: Boolean, val id: Long = 1L)
internal sealed interface IntroPhase {
    data object Waiting : IntroPhase
    data class Armed(val lease: IntroLease) : IntroPhase
    data class Running(val lease: IntroLease) : IntroPhase
    data class Finished(val reason: IntroFinishReason) : IntroPhase
}
internal fun IntroPhase.lease(): IntroLease? = when (this) {
    is IntroPhase.Armed -> lease
    is IntroPhase.Running -> lease
    else -> null
}

/** One opportunity in memory. No profile preferences, startupSession, route or player is owned here. */
internal class NotflixIntroCoordinator {
    private val mutable = MutableStateFlow<IntroPhase>(IntroPhase.Waiting)
    val state: StateFlow<IntroPhase> = mutable
    private var cleanup: (() -> Unit)? = null
    var cleanupFailed: Boolean = false
        private set
    private val held = mutableSetOf<Int>()
    val blocking: Boolean get() = state.value.lease() != null

    fun evaluate(owner: Any, input: IntroDecisionInput) {
        var finish: IntroFinishReason? = null
        synchronized(this) {
            val phase = mutable.value
            if (phase is IntroPhase.Finished) return
            val lease = phase.lease()
            if (input.blocked) finish = IntroFinishReason.EXTERNAL_OR_VIDEO
            else if (lease != null) {
                finish = when {
                    lease.owner !== owner -> IntroFinishReason.RECREATED
                    !input.foreground -> IntroFinishReason.BACKGROUND
                    !input.resolved || input.binding != lease.binding -> IntroFinishReason.CONTEXT_CHANGED
                    !input.notflix || !input.optionsValid || !input.enabled || input.sound != lease.sound -> IntroFinishReason.CONTEXT_CHANGED
                    !input.motionAllowed -> IntroFinishReason.SYSTEM_MOTION
                    else -> null
                }
                if (finish == null && phase is IntroPhase.Armed && input.surfaceReady) mutable.value = IntroPhase.Running(lease)
            } else if (input.resolved && input.binding != null) {
                finish = when {
                    !input.notflix -> IntroFinishReason.NUVIO
                    !input.optionsValid -> IntroFinishReason.INVALID_OPTIONS
                    !input.enabled -> IntroFinishReason.DISABLED
                    !input.motionAllowed -> IntroFinishReason.SYSTEM_MOTION
                    else -> null
                }
                if (finish == null && input.foreground) {
                    val first = IntroLease(owner, input.binding, input.sound)
                    mutable.value = if (input.surfaceReady) IntroPhase.Running(first) else IntroPhase.Armed(first)
                }
            }
        }
        finish?.let { this.finish(it) }
    }

    fun attachHost(owner: Any) {
        if (state.value.lease()?.owner?.let { it !== owner } == true) finish(IntroFinishReason.RECREATED)
    }
    fun sceneRemoved(owner: Any) {
        if (state.value.lease()?.owner === owner) finish(IntroFinishReason.HOST_DISPOSED)
    }
    fun background() { finish(IntroFinishReason.BACKGROUND) }
    fun externalEntry() { finish(IntroFinishReason.EXTERNAL_OR_VIDEO) }

    fun isCurrent(lease: IntroLease): Boolean = state.value.lease() == lease

    fun registerCleanup(lease: IntroLease, release: () -> Unit) {
        val keep = synchronized(this) {
            if (isCurrent(lease) && cleanup == null) { cleanup = release; true } else false
        }
        if (!keep) releaseSafely(release)
    }

    fun finish(reason: IntroFinishReason): Boolean {
        val release: (() -> Unit)?
        synchronized(this) {
            if (mutable.value is IntroPhase.Finished) return false
            mutable.value = IntroPhase.Finished(reason)
            release = cleanup
            cleanup = null
        }
        release?.let(::releaseSafely)
        return true
    }

    private fun releaseSafely(release: () -> Unit) {
        try { release() } catch (_: Exception) { cleanupFailed = true }
    }

    /** Same gate is used before Activity dispatch and by the Compose test boundary. */
    fun key(action: Int, code: Int, repeat: Int): Boolean {
        val active = blocking
        synchronized(held) {
            if (!active) {
                if (code !in held) return false
                // A new physical press is useful even if background lost an old release.
                if (action == 0 && repeat == 0) { held.remove(code); return false }
                if (action == 1) held.remove(code)
                return true
            }
            // System Home/volume remain system-owned. They never activate app content.
            if (code in setOf(3, 24, 25, 164)) return false
            if (action == 0) held += code
            if (action == 1) held.remove(code)
        }
        if (action == 0 && repeat == 0 && code in setOf(4, 23, 66, 160, 111)) finish(IntroFinishReason.SKIPPED)
        return true
    }
}

/** Process lifetime only. Creating another Activity/profile does not create another coordinator. */
internal object NotflixIntroProcess { val coordinator = NotflixIntroCoordinator() }

internal fun introRequestedVolume(sound: Boolean, mediaSilent: Boolean, ringerSilent: Boolean, interruptionSilent: Boolean): Float =
    if (sound && !mediaSilent && !ringerSilent && !interruptionSilent) 1f else 0f
