package com.nuvio.tv.core.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Eventos de sonido de interfaz. Mover el foco nunca suena. */
enum class UiSoundEvent { CONFIRM, OPEN_SCREEN, FOCUS_MOVE }

/**
 * Decisión pura: nunca con vídeo reproduciéndose, nunca al mover el foco y nunca
 * con el ajuste apagado.
 */
fun shouldPlayUiSound(event: UiSoundEvent, enabled: Boolean, videoPlaying: Boolean): Boolean =
    enabled && !videoPlaying && event != UiSoundEvent.FOCUS_MOVE

/** Punto único que usan los componentes Notflix; el valor por defecto es silencio (Nuvio y pruebas). */
fun interface NotflixSoundPlayer {
    fun play(event: UiSoundEvent)

    companion object {
        val Silent = NotflixSoundPlayer { }
    }
}

val LocalNotflixSoundPlayer = staticCompositionLocalOf { NotflixSoundPlayer.Silent }

@Composable
fun rememberNotflixConfirmSound(): () -> Unit {
    val player = LocalNotflixSoundPlayer.current
    return remember(player) {
        { player.play(UiSoundEvent.CONFIRM) }
    }
}

/**
 * Sonidos del propio sistema Android TV (promoter 09-11: «más parecidos a los default de Google/Android
 * TV»), sin ficheros propios. Con volumen explícito el sistema los reproduce aunque sus sonidos de
 * navegación estén apagados, así el ajuste de Notflix sigue mandando.
 */
@Singleton
class NotflixUiSounds @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        /** Volumen bajo acordado con el promotor el 08-11. */
        const val VOLUME = 0.35f
        /**
         * El sonido de confirmación se retrasa lo justo para que una navegación pueda
         * cancelarlo: si OK abre una pantalla, suena solo OPEN_SCREEN.
         */
        const val CONFIRM_DELAY_MS = 120L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingConfirm: Job? = null
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }

    fun play(event: UiSoundEvent, enabled: Boolean, videoPlaying: Boolean) {
        // Cualquier cambio de pantalla cancela la confirmación pendiente, aunque el
        // propio cambio no suene (por ejemplo, al entrar al reproductor).
        pendingConfirm?.cancel()
        pendingConfirm = null
        if (!shouldPlayUiSound(event, enabled, videoPlaying)) return
        when (event) {
            UiSoundEvent.CONFIRM -> pendingConfirm = scope.launch {
                delay(CONFIRM_DELAY_MS)
                emit(AudioManager.FX_KEY_CLICK)
            }
            UiSoundEvent.OPEN_SCREEN -> emit(AudioManager.FX_KEYPRESS_STANDARD)
            UiSoundEvent.FOCUS_MOVE -> Unit
        }
    }

    /** Con el ajuste apagado no queda nada pendiente. */
    fun setEnabled(enabled: Boolean) {
        if (!enabled) release()
    }

    fun release() {
        pendingConfirm?.cancel()
        pendingConfirm = null
    }

    private fun emit(effect: Int) {
        runCatching { audio?.playSoundEffect(effect, VOLUME) }
    }
}
