package com.nuvio.tv.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Adapter over the unchanged official request, debounce, provider and caches; no duplicate resolution. */
internal class OfficialNotflixPreviewResolver(private val model: HomeViewModel) : NotflixPreviewResolver {
    private data class Owned(val lease: PreviewLease, val version: Long, val job: Job?)
    private var owned: Owned? = null

    override suspend fun resolve(lease: PreviewLease, authorized: () -> Boolean): PreviewSource? =
        withContext(Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive()
            if (!authorized() || !model._uiState.value.notflixPresentationActive) return@withContext null
            val before = model.trailerPreviewRequestVersion
            model.requestTrailerPreview(lease.item)
            val version = model.trailerPreviewRequestVersion
            // Startup grace/server/feature guards return before claiming a request.
            if (version == before) return@withContext null
            val job = model.trailerPreviewJob.takeIf { lease.item.id in model.trailerPreviewLoadingIds }
            owned = Owned(lease, version, job)
            // This visit owns retries. Do not let late metadata enrichment schedule an unsolicited retry.
            if (model.activeTrailerPreviewItemId == lease.item.id) model.activeTrailerPreviewItemId = null
            if (lease.item.id !in model.trailerPreviewUrlsState && lease.item.id !in model.trailerPreviewNegativeCache) job?.join()
            currentCoroutineContext().ensureActive()
            if (!authorized() || owned?.lease != lease || !model._uiState.value.notflixPresentationActive) return@withContext null
            model.trailerPreviewUrlsState[lease.item.id]?.let { video ->
                PreviewSource(video, model.trailerPreviewAudioUrlsState[lease.item.id])
            }
        }

    override fun cancel(lease: PreviewLease) {
        val request = owned?.takeIf { it.lease == lease } ?: return
        owned = null
        // Another card/official consumer may already have replaced the same id/job/cache.
        if (model.trailerPreviewRequestVersion != request.version) return
        if (request.job != null && model.trailerPreviewJob !== request.job) return
        model.trailerPreviewRequestVersion++
        if (model.activeTrailerPreviewItemId == lease.item.id) model.activeTrailerPreviewItemId = null
        request.job?.cancel()
    }
}
