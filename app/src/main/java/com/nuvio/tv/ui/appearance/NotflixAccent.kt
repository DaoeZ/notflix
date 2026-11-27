package com.nuvio.tv.ui.appearance

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Theme colour choices; red is the Notflix default. Same list and stored names as the phone app. */
enum class NotflixAccentColor(val argb: Long) {
    RED(0xFFE50914),
    BLUE(0xFF2D7FF9),
    GREEN(0xFF1DB954),
    PURPLE(0xFF8B5CF6),
    ORANGE(0xFFF5761A),
    PINK(0xFFE5197F);

    val color: Color get() = Color(argb)
}

/** The accent every Notflix screen reads ([NotflixVisualSpec.Accent]); Compose state, so a change re-themes. */
object NotflixAccent {
    private const val PREFS_NAME = "notflix_appearance"
    private const val KEY = "accent_color"

    private var prefs: SharedPreferences? = null
    var selected by mutableStateOf(NotflixAccentColor.RED)
        private set

    val color: Color get() = selected.color

    /** Darker shade for secondary fills (the red's was #B20710). */
    val dark: Color get() = lerp(color, Color.Black, 0.22f)

    private val BaseBackground = Color(0xFF0B0B0D)
    private val BaseSurface = Color(0xFF18181D)
    private val BaseSurfaceVariant = Color(0xFF24242D)
    private val BaseDivider = Color(0xFF26262C)

    /** Subtle dark tint matching the chosen accent (classic neutral dark for Red). */
    val background: Color get() = if (selected == NotflixAccentColor.RED) BaseBackground else lerp(BaseBackground, color, 0.08f)
    val surface: Color get() = if (selected == NotflixAccentColor.RED) BaseSurface else lerp(BaseSurface, color, 0.08f)
    val surfaceVariant: Color get() = if (selected == NotflixAccentColor.RED) BaseSurfaceVariant else lerp(BaseSurfaceVariant, color, 0.08f)
    val divider: Color get() = if (selected == NotflixAccentColor.RED) BaseDivider else lerp(BaseDivider, color, 0.08f)

    fun initialize(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val stored = sp.getString(KEY, null)
        selected = NotflixAccentColor.entries.firstOrNull { it.name == stored } ?: NotflixAccentColor.RED
        NotflixUiScale.initialize(sp)
    }

    fun select(choice: NotflixAccentColor) {
        selected = choice
        prefs?.edit()?.putString(KEY, choice.name)?.apply()
    }
}

/** Customizable TV interface scale (60%–130% in 5% steps, default 100%). */
object NotflixUiScale {
    private const val KEY = "ui_scale_percent"
    const val DEFAULT_PERCENT = 100
    val STEPS: List<Int> = (60..130 step 5).toList()

    private var prefs: SharedPreferences? = null
    var scalePercent by mutableStateOf(DEFAULT_PERCENT)
        private set

    internal fun initialize(sp: SharedPreferences) {
        prefs = sp
        val stored = sp.getInt(KEY, DEFAULT_PERCENT)
        scalePercent = stored.coerceIn(STEPS.first(), STEPS.last())
    }

    fun select(percent: Int) {
        val clamped = percent.coerceIn(STEPS.first(), STEPS.last())
        scalePercent = clamped
        prefs?.edit()?.putInt(KEY, clamped)?.apply()
    }
}
