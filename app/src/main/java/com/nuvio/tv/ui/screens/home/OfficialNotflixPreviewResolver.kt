package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * M63: lo mínimo que un ViewModel ofrece para resolver el tráiler de una tarjeta. Inicio y la
 * carpeta de colección tienen cada uno su almacén; el resolvedor y el coordinador son los mismos.
 */
internal interface NotflixPreviewRequestHost {
    val previewRequestsAllowed: Boolean
    val previewRequestVersion: Long
    val previewRequestJob: Job?
    fun previewLoading(itemId: String): Boolean
    fun previewNegative(itemId: String): Boolean
    fun previewResolved(itemId: String): Boolean
    fun previewSource(itemId: String): PreviewSource?
    /** Lanza el pedido oficial y devuelve su versión, o null si no se reclamó. */
    fun requestPreview(item: MetaPreview): Long?
    /** Esta visita posee el pedido: el aviso de reintento por enriquecimiento ya no hace falta. */
    fun claimPreviewRequest(itemId: String)
    fun cancelPreview(itemId: String, version: Long, job: Job?)
}

/** Adapter over HomeViewModel; nothing is copied from the official request, debounce or caches. */
internal class HomeNotflixPreviewRequestHost(
    private val model: HomeViewModel,
    private val requiresHomePresentation: Boolean = true
) : NotflixPreviewRequestHost {
    override val previewRequestsAllowed: Boolean get() = !requiresHomePresentation || model._uiState.value.notflixPresentationActive
    override val previewRequestVersion: Long get() = model.trailerPreviewRequestVersion
    override val previewRequestJob: Job? get() = model.trailerPreviewJob

    override fun previewLoading(itemId: String): Boolean = itemId in model.trailerPreviewLoadingIds
    override fun previewNegative(itemId: String): Boolean = itemId in model.trailerPreviewNegativeCache
    override fun previewResolved(itemId: String): Boolean = model.trailerPreviewUrlsState.containsKey(itemId)
    override fun previewSource(itemId: String): PreviewSource? =
        model.trailerPreviewUrlsState[itemId]?.let { PreviewSource(it, model.trailerPreviewAudioUrlsState[itemId]) }

    override fun requestPreview(item: MetaPreview): Long? {
        val before = model.trailerPreviewRequestVersion
        model.requestTrailerPreview(item)
        return model.trailerPreviewRequestVersion.takeIf { it != before }
    }

    override fun claimPreviewRequest(itemId: String) {
        if (model.activeTrailerPreviewItemId == itemId) model.activeTrailerPreviewItemId = null
    }

    override fun cancelPreview(itemId: String, version: Long, job: Job?) {
        if (model.trailerPreviewRequestVersion != version) return
        if (job != null && model.trailerPreviewJob !== job) return
        model.trailerPreviewRequestVersion++
        if (model.activeTrailerPreviewItemId == itemId) model.activeTrailerPreviewItemId = null
        job?.cancel()
    }
}

/** Adapter over FolderDetailViewModel: its own official folder request and caches, same policy. */
internal class FolderNotflixPreviewRequestHost(
    private val model: com.nuvio.tv.ui.screens.collection.FolderDetailViewModel
) : NotflixPreviewRequestHost {
    override val previewRequestsAllowed: Boolean get() = true
    override val previewRequestVersion: Long get() = model.trailerPreviewVersion
    override val previewRequestJob: Job? get() = model.trailerPreviewJobRef

    override fun previewLoading(itemId: String): Boolean = model.trailerPreviewLoading(itemId)
    override fun previewNegative(itemId: String): Boolean = model.trailerPreviewNegative(itemId)
    override fun previewResolved(itemId: String): Boolean = model.trailerPreviewVideoUrl(itemId) != null
    override fun previewSource(itemId: String): PreviewSource? =
        model.trailerPreviewVideoUrl(itemId)?.let { PreviewSource(it, model.trailerPreviewAudioUrl(itemId)) }

    override fun requestPreview(item: MetaPreview): Long? {
        val before = model.trailerPreviewVersion
        model.requestTrailerPreview(item.id, item.name, item.releaseInfo, item.apiType, item.trailerYtIds.firstOrNull(), newVisit = true)
        return model.trailerPreviewVersion.takeIf { it != before }
    }

    override fun claimPreviewRequest(itemId: String) {
        // The folder request needs this id to accept its completion. Its Notflix enrichment
        // path disables automatic trailer retries instead of clearing the in-flight owner.
    }

    override fun cancelPreview(itemId: String, version: Long, job: Job?) {
        model.cancelTrailerPreview(itemId, version, job)
    }
}

/** Adapter over the unchanged official request, debounce, provider and caches; no duplicate resolution. */
internal class OfficialNotflixPreviewResolver(
    private val host: NotflixPreviewRequestHost
) : NotflixPreviewResolver {
    constructor(model: HomeViewModel) : this(HomeNotflixPreviewRequestHost(model))

    private data class Owned(val lease: PreviewLease, val version: Long, val job: Job?)
    private var owned: Owned? = null

    override suspend fun resolve(lease: PreviewLease, authorized: () -> Boolean): PreviewSource? =
        withContext(Dispatchers.Main.immediate) {
            currentCoroutineContext().ensureActive()
            if (!authorized() || !host.previewRequestsAllowed) return@withContext null
            val version = host.requestPreview(lease.item) ?: return@withContext null
            val job = host.previewRequestJob.takeIf { host.previewLoading(lease.item.id) }
            owned = Owned(lease, version, job)
            // This visit owns retries. Do not let late metadata enrichment schedule an unsolicited retry.
            host.claimPreviewRequest(lease.item.id)
            if (!host.previewResolved(lease.item.id) && !host.previewNegative(lease.item.id)) job?.join()
            currentCoroutineContext().ensureActive()
            if (!authorized() || owned?.lease != lease || !host.previewRequestsAllowed) return@withContext null
            host.previewSource(lease.item.id)
        }

    override fun cancel(lease: PreviewLease) {
        val request = owned?.takeIf { it.lease == lease } ?: return
        owned = null
        // Another card/official consumer may already have replaced the same id/job/cache.
        host.cancelPreview(lease.item.id, request.version, request.job)
    }
}
