package com.nuvio.tv.ui.appearance

/** Only display a finite rating supplied by the official model, never a placeholder. */
internal fun notflixRating(value: Float?): String? = value?.takeIf { it.isFinite() && it in 0f..10f }?.toString()
