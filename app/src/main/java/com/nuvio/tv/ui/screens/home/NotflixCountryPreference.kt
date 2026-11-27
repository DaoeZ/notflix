package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * User-selectable country/region override for international Top 10 rows on TV.
 * Empty string ("") means Automatic (OS region -> locale region -> US).
 */
object NotflixCountryPreference {
    const val AUTO = ""
    private const val PREFS_NAME = "notflix_country"
    private const val KEY = "top10_country_override"

    val SUPPORTED_COUNTRIES: List<String> = listOf(
        "ES", "MX", "AR", "CO", "CL", "PE", "EC", "UY", "VE", "CR", "DO",
        "US", "GB", "CA", "FR", "DE", "IT", "PT", "BR",
        "NL", "BE", "CH", "AT", "PL", "SE", "NO", "DK", "FI", "IE", "GR",
        "TR", "RO", "CZ", "HU", "UA", "RU",
        "JP", "KR", "IN", "PH", "AU", "NZ", "ZA", "SA", "AE", "EG"
    )

    private var prefs: SharedPreferences? = null
    private val _selectedCountry = MutableStateFlow(AUTO)
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    private const val SHOW_TOP10_KEY = "top10_rows_visible"
    private val _showTop10 = MutableStateFlow(true)
    /** Home shows the Top 10 rows; off for people who only want their own catalogs. */
    val showTop10: StateFlow<Boolean> = _showTop10.asStateFlow()

    fun initialize(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val stored = sp.getString(KEY, null)?.trim()?.uppercase().orEmpty()
        _selectedCountry.value = if (stored.length == 2) stored else AUTO
        _showTop10.value = sp.getBoolean(SHOW_TOP10_KEY, true)
    }

    fun setShowTop10(show: Boolean) {
        _showTop10.value = show
        prefs?.edit()?.putBoolean(SHOW_TOP10_KEY, show)?.apply()
    }

    fun select(code: String) {
        val clean = code.trim().uppercase()
        val normalized = if (clean.length == 2) clean else AUTO
        _selectedCountry.value = normalized
        prefs?.edit()?.apply {
            if (normalized.isEmpty()) remove(KEY) else putString(KEY, normalized)
            apply()
        }
    }

    fun resolveCountryCode(
        fallbackLocale: Locale = Locale.getDefault(),
        overrideCode: String = _selectedCountry.value
    ): String {
        val clean = overrideCode.trim().uppercase()
        if (clean.length == 2) return clean
        return notflixSystemCountryCode(fallbackLocale)
    }

    fun displayCountryName(code: String, displayLocale: Locale = Locale.getDefault()): String {
        val clean = code.trim().uppercase()
        if (clean.length != 2) return clean.ifBlank { "US" }
        return Locale.Builder().setRegion(clean).build().getDisplayCountry(displayLocale).ifBlank { clean }
    }
}
