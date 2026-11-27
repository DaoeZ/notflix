package com.nuvio.tv.ui.screens.search

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notflix search keyboard: the on-screen Notflix keyboard (default) or a text field with the system keyboard. */
object NotflixSearchModePreference {
    private const val PREFS_NAME = "notflix_search"
    private const val KEY = "system_keyboard"

    private var prefs: SharedPreferences? = null
    private val _systemKeyboard = MutableStateFlow(false)
    val systemKeyboard: StateFlow<Boolean> = _systemKeyboard.asStateFlow()

    fun initialize(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        _systemKeyboard.value = sp.getBoolean(KEY, false)
    }

    fun setSystemKeyboard(enabled: Boolean) {
        _systemKeyboard.value = enabled
        prefs?.edit()?.putBoolean(KEY, enabled)?.apply()
    }
}
