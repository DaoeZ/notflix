package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.navigation.Screen

internal enum class BillboardAction { PLAY, INFO }

/** Only project the official hero selection; never fill from another catalog or fetch. */
internal fun notflixBillboardItems(items: List<MetaPreview>): List<MetaPreview> =
    items.distinctBy(::notflixMetaKey).take(6)

internal fun notflixBillboardIndex(items: List<MetaPreview>, key: String?, previous: Int): Int =
    items.indexOfFirst { notflixMetaKey(it) == key }.takeIf { it >= 0 }
        ?: previous.coerceIn(0, items.lastIndex.coerceAtLeast(0))

/** The existing preview host supplies visibility/confirmation gates, independent of preview preference. */
internal fun PreviewEnvironment.billboardReady(externalBlocked: Boolean): Boolean =
    binding != null && valid && resumed && windowFocused && !introActive && !blocked && !otherVideo && !externalBlocked

/** Monotonic visible time only. A completed period emits once; a pause resumes its retained fraction. */
internal class NotflixBillboardClock(initialFraction: Float = 0f) {
    private val initial = initialFraction.coerceIn(0f, 1f)
    private var start: Long? = null
    private var emitted = false
    data class Frame(val fraction: Float, val advance: Boolean)
    fun frame(nowNanos: Long): Frame {
        if (emitted) return Frame(1f, false)
        val origin = start ?: nowNanos.also { start = it }
        val fraction = (initial + (nowNanos - origin).coerceAtLeast(0L).toDouble() / PERIOD_NANOS).coerceAtMost(1.0).toFloat()
        val advance = (nowNanos - origin).coerceAtLeast(0L).toDouble() >= (1.0 - initial.toDouble()) * PERIOD_NANOS
        if (advance) emitted = true
        return Frame(fraction, advance)
    }
    companion object { const val PERIOD_NANOS = 10_000_000_000L }
}

/** Uses the existing official movie/episode decision and stream selector; owns neither. */
internal fun notflixBillboardPlayRoute(id: String, type: String, addonBase: String, manual: Boolean, backdrop: String? = null): String =
    Screen.Detail.createRoute(itemId = id, itemType = type, addonBaseUrl = addonBase,
        heroBackdropUrl = backdrop, playOnLoad = true, manualSelection = manual)