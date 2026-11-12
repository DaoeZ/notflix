package com.nuvio.tv.ui.appearance

/** Only display a finite rating supplied by the official model, never a placeholder. */
// TMDB sends up to three decimals (8.332); badges show one, like IMDb.
internal fun notflixRating(value: Float?): String? =
    value?.takeIf { it.isFinite() && it in 0f..10f }?.let { (Math.round(it * 10f) / 10f).toString() }
