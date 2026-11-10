package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.InternalPlayerEngine
import java.util.Locale

/**
 * Sharpness experiment (M45). Pure rules only: no Android, no mpv and no player state.
 *
 * The GPU path (option A of the commission) appends one user shader to mpv's `glsl-shaders`
 * list, so the video output is the only thing that has to support it. It is fixed on purpose:
 * with [isNotflixSharpenAvailable] false the player does not touch any decoder or shader.
 */

/** Where the shader lives inside the APK. Copied to the app files dir on first use. */
internal const val NOTFLIX_SHARPEN_SHADER_ASSET = "shaders/notflix_sharpen.glsl"

/** Name of the local copy passed to mpv (the app files dir path is added by the caller). */
internal const val NOTFLIX_SHARPEN_SHADER_FILE = "notflix_sharpen.glsl"

/** mpv list option that holds the user shaders. */
internal const val NOTFLIX_SHARPEN_SHADER_LIST = "glsl-shaders"

/** Video outputs of mpv that run GPU user shaders. */
private val NOTFLIX_SHARPEN_VIDEO_OUTPUTS = setOf("gpu", "gpu-next")

/**
 * Hardware decoders that cannot feed the MAIN hook in this build.
 *
 * Empty today: the shader hooks MAIN, so copy paths hand over normal textures and direct
 * MediaCodec frames arrive as external textures. It stays here as the single place to exclude
 * a decoder without touching the visibility rule.
 */
private val NOTFLIX_SHARPEN_UNSUPPORTED_HWDEC = emptySet<String>()

/** True for a video output that can run the shader (the app uses `gpu`, or `gpu-next`). */
internal fun isNotflixSharpenVideoOutput(videoOutput: String?): Boolean =
    videoOutput?.trim()?.lowercase(Locale.ROOT) in NOTFLIX_SHARPEN_VIDEO_OUTPUTS

/**
 * Whether the sharpness icon may be offered for the current playback.
 *
 * [hardwareDecoder] is mpv's `hwdec-current` (or `hwdec`). It does not narrow the rule today;
 * it is part of the check so the caller can log it and a future decoder can be excluded here.
 */
internal fun isNotflixSharpenAvailable(
    engine: InternalPlayerEngine,
    videoOutput: String?,
    hardwareDecoder: String?
): Boolean {
    if (engine != InternalPlayerEngine.MVP_PLAYER) return false
    if (hardwareDecoder?.trim()?.lowercase(Locale.ROOT) in NOTFLIX_SHARPEN_UNSUPPORTED_HWDEC) return false
    return isNotflixSharpenVideoOutput(videoOutput)
}

/**
 * Per-playback state. Every new playback starts with [enabled] false, whatever the previous one
 * did, and availability can drop (engine switch, video output change) at any time.
 */
internal data class NotflixSharpenSession(
    val available: Boolean,
    val enabled: Boolean = false
) {
    fun withAvailability(available: Boolean): NotflixSharpenSession =
        copy(available = available, enabled = enabled && available)

    fun toggled(): NotflixSharpenSession = copy(enabled = available && !enabled)

    fun failed(): NotflixSharpenSession = copy(enabled = false)

    companion object {
        /** Start of a playback: the experiment is always off. */
        fun forPlayback(available: Boolean = false): NotflixSharpenSession =
            NotflixSharpenSession(available = available, enabled = false)
    }
}
