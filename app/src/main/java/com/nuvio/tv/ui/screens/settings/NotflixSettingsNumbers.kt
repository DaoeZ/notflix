package com.nuvio.tv.ui.screens.settings

import java.math.BigDecimal

/** Bounded picker, including the current value: no large intermediate range on a 2 GB device. */
internal fun notflixNumberOptions(min: Int, max: Int, step: Int, current: Int): List<Int> {
    if (max < min || step <= 0) return listOf(current)
    val count = (max.toLong() - min) / step
    val stride = ((count + 39) / 40).coerceAtLeast(1)
    val values = mutableListOf<Int>()
    var index = 0L
    while (index <= count) {
        values += (min.toLong() + index * step).toInt()
        index += stride
    }
    values += max
    values += current
    return values.distinct().sorted()
}

internal fun notflixNumberLabel(value: Int, current: Int, currentLabel: String, scale: Float = 1f): String {
    if (value == current) return currentLabel
    val match = Regex("^(-?[0-9]+(?:[.,][0-9]+)?)(.*)$").matchEntire(currentLabel.trim())
    // Units are presentation only. Never infer a scale from an effective/compound value such
    // as "128+64 MB", which would silently change what another candidate means.
    var number = BigDecimal.valueOf(value * scale.toDouble()).setScale(3, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
    if (match?.groupValues?.get(1)?.contains(',') == true) number = number.replace('.', ',')
    val suffix = match?.groupValues?.get(2).orEmpty()
    val unit = if (suffix.any(Char::isDigit)) Regex("[ ]*[a-zA-Z%]+$").find(currentLabel)?.value.orEmpty() else suffix
    return number + unit
}
