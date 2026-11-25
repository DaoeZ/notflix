package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

/**
 * Lightweight Night Mode / Clear Dialogue DRC processor for ExoPlayer (API 24+).
 * Uses linked multi-channel frame envelope detection so 5.1 / 7.1 surround tracks
 * keep balanced dialogue and spatial imaging without per-channel pumping.
 */
@UnstableApi
internal class NightModeDrcAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var envelope: Float = 0f

    fun isNightModeEnabled(): Boolean = enabled

    fun setNightModeEnabled(value: Boolean) {
        if (enabled != value) {
            enabled = value
            if (!value) envelope = 0f
        }
    }

    override fun isActive(): Boolean = super.isActive() && enabled

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000).toFloat()
        val attackCoeff = 1f - kotlin.math.exp(-1f / (0.008f * sampleRate))
        val releaseCoeff = 1f - kotlin.math.exp(-1f / (0.180f * sampleRate))
        val threshold = 0.22f
        val makeupGain = 1.65f // ~+4.3 dB clear dialogue lift
        val ratioInv = 1f / 4.0f
        var env = if (envelope.isFinite()) envelope else 0f
        val frame = FloatArray(channels)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = channels * 2
                while (inputBuffer.remaining() >= frameBytes) {
                    var peak = 0f
                    for (ch in 0 until channels) {
                        val s = inputBuffer.short / 32768f
                        frame[ch] = s
                        val a = abs(s)
                        if (a > peak) peak = a
                    }
                    env += (peak - env) * if (peak > env) attackCoeff else releaseCoeff
                    val gain = computeGain(env, threshold, ratioInv, makeupGain)
                    for (ch in 0 until channels) {
                        val processed = softLimit(frame[ch] * gain)
                        outputBuffer.putShort((processed * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                    }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = channels * 4
                while (inputBuffer.remaining() >= frameBytes) {
                    var peak = 0f
                    for (ch in 0 until channels) {
                        val raw = inputBuffer.float
                        val s = if (raw.isFinite()) raw else 0f
                        frame[ch] = s
                        val a = abs(s)
                        if (a > peak) peak = a
                    }
                    env += (peak - env) * if (peak > env) attackCoeff else releaseCoeff
                    val gain = computeGain(env, threshold, ratioInv, makeupGain)
                    for (ch in 0 until channels) {
                        outputBuffer.putFloat(softLimit(frame[ch] * gain))
                    }
                }
            }
            C.ENCODING_PCM_24BIT -> {
                val frameBytes = channels * 3
                while (inputBuffer.remaining() >= frameBytes) {
                    var peak = 0f
                    for (ch in 0 until channels) {
                        val b0 = inputBuffer.get().toInt() and 0xFF
                        val b1 = inputBuffer.get().toInt() and 0xFF
                        val b2 = inputBuffer.get().toInt()
                        val sampleInt = (b2 shl 16) or (b1 shl 8) or b0
                        val s = sampleInt / 8388608f
                        frame[ch] = s
                        val a = abs(s)
                        if (a > peak) peak = a
                    }
                    env += (peak - env) * if (peak > env) attackCoeff else releaseCoeff
                    val gain = computeGain(env, threshold, ratioInv, makeupGain)
                    for (ch in 0 until channels) {
                        val processed = (softLimit(frame[ch] * gain) * 8388607f).toInt().coerceIn(-8388608, 8388607)
                        outputBuffer.put((processed and 0xFF).toByte())
                        outputBuffer.put(((processed shr 8) and 0xFF).toByte())
                        outputBuffer.put(((processed shr 16) and 0xFF).toByte())
                    }
                }
            }
            C.ENCODING_PCM_32BIT -> {
                val frameBytes = channels * 4
                while (inputBuffer.remaining() >= frameBytes) {
                    var peak = 0f
                    for (ch in 0 until channels) {
                        val s = inputBuffer.int / 2147483648f
                        frame[ch] = s
                        val a = abs(s)
                        if (a > peak) peak = a
                    }
                    env += (peak - env) * if (peak > env) attackCoeff else releaseCoeff
                    val gain = computeGain(env, threshold, ratioInv, makeupGain)
                    for (ch in 0 until channels) {
                        val processed = (softLimit(frame[ch] * gain) * 2147483647.0).toLong()
                            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                        outputBuffer.putInt(processed)
                    }
                }
            }
            else -> outputBuffer.put(inputBuffer)
        }
        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
        envelope = if (env.isFinite()) env else 0f
        outputBuffer.flip()
    }

    override fun onFlush() {
        envelope = 0f
    }

    override fun onReset() {
        envelope = 0f
    }

    private fun computeGain(env: Float, threshold: Float, ratioInv: Float, makeupGain: Float): Float {
        val compGain = if (env > threshold && env > 0f) {
            (threshold * (env / threshold).pow(ratioInv)) / env
        } else 1f
        return compGain * makeupGain
    }

    private fun softLimit(x: Float): Float {
        if (!x.isFinite()) return 0f
        val ax = abs(x)
        return if (ax <= 0.92f) x else {
            val sign = if (x >= 0f) 1f else -1f
            sign * (0.92f + 0.08f * kotlin.math.tanh((ax - 0.92f) / 0.08f))
        }
    }
}
