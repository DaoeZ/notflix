package com.nuvio.tv.ui.screens.player

import android.util.Log
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.flow.update

/**
 * Notflix sharpness experiment (M45): a per-playback toggle that appends one GPU user shader to
 * mpv. It never changes `hwdec` or `vo`, and with the toggle off no mpv command is sent.
 */

private const val NOTFLIX_SHARPEN_LOG_TAG = "NotflixSharpen"

/** Called whenever mpv starts (or re-attaches) a media load: the experiment always starts off. */
internal fun PlayerRuntimeController.resetNotflixSharpenForPlayback(view: NuvioMpvSurfaceView) {
    view.removeNotflixSharpen()
    notflixSharpenSession = NotflixSharpenSession.forPlayback(available = false)
    publishNotflixSharpenState()
}

/** Recomputes whether the icon can be offered for the current engine, video output and decoder. */
internal fun PlayerRuntimeController.refreshNotflixSharpenAvailability(view: NuvioMpvSurfaceView) {
    val available = isNotflixSharpenAvailable(
        engine = currentInternalPlayerEngine,
        videoOutput = view.readVideoOutputName(),
        hardwareDecoder = view.readHardwareDecoderName()
    )
    val updated = notflixSharpenSession.withAvailability(available)
    if (updated == notflixSharpenSession && _uiState.value.notflixSharpenAvailable == available) return
    notflixSharpenSession = updated
    Log.d(
        NOTFLIX_SHARPEN_LOG_TAG,
        "Availability=${updated.available} vo=${view.readVideoOutputName()} hwdec=${view.readHardwareDecoderName()}"
    )
    publishNotflixSharpenState()
}

internal fun PlayerRuntimeController.toggleNotflixSharpen() {
    if (!isUsingMpvEngine()) return
    val view = mpvView ?: return
    refreshNotflixSharpenAvailability(view)
    if (!notflixSharpenSession.available) return

    val next = notflixSharpenSession.toggled()
    val applied = if (next.enabled) view.applyNotflixSharpen() else view.removeNotflixSharpen()
    notflixSharpenSession = if (applied) next else next.failed()
    if (!applied) {
        Log.w(
            NOTFLIX_SHARPEN_LOG_TAG,
            "mpv rejected the sharpen ${if (next.enabled) "enable" else "disable"} request"
        )
    }
    publishNotflixSharpenState()
}

/**
 * mpv compiles user shaders asynchronously and reports failures through its log. Our relay sees
 * every line, so a shader error while the experiment is on turns it back off without touching
 * playback.
 */
internal fun PlayerRuntimeController.onNotflixSharpenLogLine(level: Int, text: String) {
    if (!notflixSharpenSession.enabled) return
    if (level > MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN) return
    val lower = text.lowercase()
    if (!lower.contains("shader")) return
    if (!lower.contains("error") && !lower.contains("fail") && !lower.contains("could not")) return
    Log.w(NOTFLIX_SHARPEN_LOG_TAG, "mpv reported a shader error: ${text.take(200)}")
    mpvView?.removeNotflixSharpen()
    notflixSharpenSession = notflixSharpenSession.failed()
    publishNotflixSharpenState()
}

private fun PlayerRuntimeController.publishNotflixSharpenState() {
    val session = notflixSharpenSession
    _uiState.update {
        it.copy(
            notflixSharpenAvailable = session.available,
            notflixSharpenEnabled = session.enabled
        )
    }
}
