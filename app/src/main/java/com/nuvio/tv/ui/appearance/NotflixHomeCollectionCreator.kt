package com.nuvio.tv.ui.appearance

import com.nuvio.tv.data.local.CollectionInsertResult
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.AppearanceContext
import kotlinx.coroutines.CancellationException

internal data class NotflixCollectionRequest(
    val context: AppearanceContext,
    val selectionGeneration: Long,
    val authenticated: Boolean
)

internal enum class NotflixCollectionAccess { CHECKING, LOCAL, ACCOUNT_SYNC_PENDING }

internal enum class NotflixCollectionResult {
    CREATED_LOCAL, CREATED_ACCOUNT, EXISTS, CONTEXT_CHANGED, ACCOUNT_SYNC_PENDING, FAILED
}

/** The official collection sync of the active profile. Both calls return false when they fail. */
internal interface NotflixCollectionSync {
    suspend fun pullActiveProfile(): Boolean
    suspend fun pushActiveProfile(): Boolean
}

/**
 * Only the official collection store and its official sync are written. No ownership marker or parallel store.
 * Signed-in accounts: pull the remote list, insert the preset locally, push the list. If the push fails the
 * local insert is undone, so the next pull cannot find a collection the account never received.
 */
internal class NotflixHomeCollectionCreator(
    private val store: CollectionsDataStore,
    private val sync: NotflixCollectionSync? = null
) {
    suspend fun create(request: NotflixCollectionRequest, authorized: () -> Boolean): NotflixCollectionResult {
        if (!authorized()) return NotflixCollectionResult.CONTEXT_CHANGED
        if (!request.authenticated) return insertLocal(request, authorized)
        // Without a sync contract, an account collection would be a false promise: stay blocked.
        val sync = sync ?: return NotflixCollectionResult.ACCOUNT_SYNC_PENDING
        return try {
            if (!sync.pullActiveProfile()) return NotflixCollectionResult.FAILED
            if (!authorized()) return NotflixCollectionResult.CONTEXT_CHANGED
            val preset = NotflixHomeCollectionPreset.create()
            when (val inserted = insertLocal(request, authorized)) {
                NotflixCollectionResult.CREATED_LOCAL -> {
                    if (!authorized()) {
                        store.removeCollection(preset.id)
                        return NotflixCollectionResult.CONTEXT_CHANGED
                    }
                    if (sync.pushActiveProfile()) {
                        NotflixCollectionResult.CREATED_ACCOUNT
                    } else {
                        store.removeCollection(preset.id)
                        NotflixCollectionResult.FAILED
                    }
                }
                else -> inserted
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NotflixCollectionResult.FAILED
        }
    }

    private suspend fun insertLocal(request: NotflixCollectionRequest, authorized: () -> Boolean): NotflixCollectionResult =
        try {
            when (store.insertCollectionIfAbsent(request.context.profileId, NotflixHomeCollectionPreset.create(), authorized)) {
                CollectionInsertResult.Inserted -> NotflixCollectionResult.CREATED_LOCAL
                is CollectionInsertResult.Exists -> NotflixCollectionResult.EXISTS
                CollectionInsertResult.NotAuthorized -> NotflixCollectionResult.CONTEXT_CHANGED
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NotflixCollectionResult.FAILED
        }
}

internal data class NotflixCollectionCreationState(
    val request: NotflixCollectionRequest? = null,
    val busy: Boolean = false,
    val result: NotflixCollectionResult? = null
) {
    fun forAppearance(state: AppearanceState): NotflixCollectionCreationState =
        if (request != null && request.context == state.context && request.selectionGeneration == state.selectionGeneration) this
        else NotflixCollectionCreationState(busy = busy)
}
