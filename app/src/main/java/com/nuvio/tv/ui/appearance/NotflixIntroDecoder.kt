package com.nuvio.tv.ui.appearance

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nuvio.tv.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal fun interface IntroHandle { fun close() }
internal fun interface IntroDecoderFactory {
    fun create(view: PlayerView, sound: Boolean, complete: () -> Unit, error: () -> Unit): IntroHandle
}
internal class OnceIntroHandle(private val delegate: IntroHandle) : IntroHandle {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) delegate.close() }
}

internal fun introMotionAllowed(): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled() else true
}.getOrDefault(false)

internal fun introSystemMediaBusy(context: Context): Boolean = runCatching {
    (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
}.getOrDefault(true)

internal fun createIntroPlayerView(context: Context): PlayerView = PlayerView(context).apply {
    useController = false
    isFocusable = false
    isFocusableInTouchMode = false
    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    setShutterBackgroundColor(Color.BLACK)
    runCatching { PlayerView::class.java.getMethod("setEnableComposeSurfaceSyncWorkaround", Boolean::class.javaPrimitiveType).invoke(this, true) }
}

/** Reads only: never changes stream volume, ringer, interruption mode or output routing. */
private fun requestedVolume(context: Context, sound: Boolean): Float = runCatching {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val filter = notifications.currentInterruptionFilter
    introRequestedVolume(sound,
        mediaSilent = audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0,
        ringerSilent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL,
        interruptionSilent = filter != NotificationManager.INTERRUPTION_FILTER_ALL)
}.getOrDefault(0f)

/** The sole media item is a packaged raw resource. No service, session, pool, main engine or URL input. */
internal object LocalIntroDecoderFactory : IntroDecoderFactory {
    private val active = AtomicInteger(0)
    override fun create(view: PlayerView, sound: Boolean, complete: () -> Unit, error: () -> Unit): IntroHandle {
        val context = view.context.applicationContext
        val started = SystemClock.elapsedRealtime()
        val volume = requestedVolume(context, sound)
        val player = ExoPlayer.Builder(context).build()
        val closed = AtomicBoolean(false)
        active.incrementAndGet()
        val handle = IntroHandle {
            if (closed.compareAndSet(false, true)) {
                try {
                    runCatching { view.player = null }
                    runCatching { player.stop() }
                    runCatching { player.release() }.onFailure { Log.d("NotflixIntro", "release_api_failure") }
                } finally {
                    Log.d("NotflixIntro", "release active=${active.decrementAndGet()} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
                }
            }
        }
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), volume > 0f)
            player.volume = volume
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setHandleAudioBecomingNoisy(true)
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (!closed.get()) Log.d("NotflixIntro", "first_frame position_ms=${player.currentPosition} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (!closed.get() && state == Player.STATE_ENDED) complete()
                }
                override fun onPlayerError(playbackError: PlaybackException) {
                    if (!closed.get()) {
                        Log.d("NotflixIntro", "decoder_error code=${playbackError.errorCode}")
                        error()
                    }
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!closed.get() && !playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) error()
                }
            })
            view.player = player
            player.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.notflix_intro_v1}")))
            player.prepare()
            player.playWhenReady = true
            Log.d("NotflixIntro", "start local_asset=true sound_preference=$sound requested_volume=$volume audio_focus=${volume > 0f} active=${active.get()}")
        } catch (_: Exception) {
            handle.close()
            error()
        }
        return handle
    }
}
