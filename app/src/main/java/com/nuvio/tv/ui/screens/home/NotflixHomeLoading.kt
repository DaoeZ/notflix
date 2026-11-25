package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.MetaPreview
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Optional local probe for Compose QA; production never installs an observer. */
internal val LocalNotflixCompositionObserver = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** Local Debug diagnostics only: monotonic times and opaque identities, never URLs or titles. */
internal fun notflixHomeTiming(event: String, identity: Int = 0) {
    if (com.nuvio.tv.BuildConfig.DEBUG) android.util.Log.d("NotflixHomeTiming",
        "$event id=$identity t_ms=${android.os.SystemClock.elapsedRealtime()}")
}

/** Partial enrichment may fill gaps, but must not erase data already supplied by the addon. */
internal fun notflixCardMetadata(base: MetaPreview, enriched: MetaPreview?): MetaPreview {
    if (enriched == null || notflixMetaKey(base) != notflixMetaKey(enriched)) return base
    return enriched.copy(
        name = enriched.name.takeIf { it.isNotBlank() } ?: base.name,
        description = enriched.description?.takeIf { it.isNotBlank() } ?: base.description,
        releaseInfo = enriched.releaseInfo?.takeIf { it.isNotBlank() } ?: base.releaseInfo,
        genres = enriched.genres.takeIf { it.isNotEmpty() } ?: base.genres,
        runtime = enriched.runtime?.takeIf { it.isNotBlank() } ?: base.runtime,
        imdbRating = enriched.imdbRating ?: base.imdbRating,
        ratingSource = if (enriched.imdbRating == null) base.ratingSource else enriched.ratingSource
    )
}

/** Visible rows first, then three below the viewport. The editorial item precedes every row. */
internal fun notflixRowLoadWindow(
    rowKeys: List<String>, visible: Set<String>, ahead: Int = 3
): List<String> {
    val last = rowKeys.indexOfLast { it in visible }
    if (last < 0 && "editorial" !in visible) return emptyList()
    val next = rowKeys.drop(last + 1).take(ahead.coerceAtLeast(0))
    return (rowKeys.filter { it in visible } + next).distinct()
}

/** UI-dispatcher confined. No debounce, no cancellation/starvation when another row arrives.
 * Arrivals during a suspended projection are conflated into one further pass over the latest data.
 */
internal class NotflixCatalogUpdates(
    private val owner: CoroutineScope,
    private val publish: suspend () -> Unit
) {
    private var job: Job? = null
    private var pending = false

    fun request() {
        pending = true
        if (job?.isActive == true) return
        val next = owner.launch(start = CoroutineStart.LAZY) {
            while (pending && currentCoroutineContext().isActive) {
                pending = false
                notflixHomeTiming("publish-start")
                publish()
                notflixHomeTiming("publish-end")
            }
        }
        job = next
        next.start()
    }

    fun cancel() {
        pending = false
        job?.cancel()
        job = null
    }
}
