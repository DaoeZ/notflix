package com.nuvio.tv.ui.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nuvio.tv.core.sync.CollectionSyncService

private fun CollectionSyncService.asNotflixSync(): NotflixCollectionSync = object : NotflixCollectionSync {
    override suspend fun pullActiveProfile(): Boolean = pullFromRemote().isSuccess
    override suspend fun pushActiveProfile(): Boolean = pushToRemote().isSuccess
}

@HiltViewModel
internal class NotflixHomeCollectionViewModel private constructor(
    collections: CollectionsDataStore,
    private val currentProfile: () -> UserProfile?,
    private val currentProfileId: () -> Int,
    private val authStates: StateFlow<AuthState>,
    sync: NotflixCollectionSync?
) : ViewModel() {
    @Inject constructor(
        collections: CollectionsDataStore,
        profiles: ProfileManager,
        auth: AuthManager,
        collectionSync: CollectionSyncService
    ) : this(
        collections,
        { profiles.activeProfile },
        { profiles.activeProfileId.value },
        auth.authState,
        collectionSync.asNotflixSync()
    )

    /** Same controller with an owner-supplied auth stream; no live account is needed by an isolated host. */
    internal constructor(
        collections: CollectionsDataStore,
        profiles: ProfileManager,
        auth: StateFlow<AuthState>,
        sync: NotflixCollectionSync? = null
    ) : this(collections, { profiles.activeProfile }, { profiles.activeProfileId.value }, auth, sync)

    private val creator = NotflixHomeCollectionCreator(collections, sync)
    private val mutableState = MutableStateFlow(NotflixCollectionCreationState())
    val state = mutableState.asStateFlow()
    val access = authStates.map {
        when (it) {
            AuthState.Loading -> NotflixCollectionAccess.CHECKING
            AuthState.SignedOut -> NotflixCollectionAccess.LOCAL
            is AuthState.FullAccount -> NotflixCollectionAccess.ACCOUNT_SYNC_PENDING
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotflixCollectionAccess.CHECKING)
    private fun currentAuth() = authStates.value

    fun create(
        appearance: AppearanceState,
        texts: NotflixHomeCollectionTexts,
        year: Int,
        authorizes: (AppearanceContext, Long) -> Boolean
    ) {
        if (mutableState.value.busy) return
        val context = appearance.context ?: return
        val profile = currentProfile() ?: return
        val capturedAuth = currentAuth()
        if (profile.id != context.profileId || capturedAuth is AuthState.Loading) return
        val request = NotflixCollectionRequest(context, appearance.selectionGeneration, capturedAuth is AuthState.FullAccount)
        val current = {
            authorizes(context, request.selectionGeneration) &&
                currentProfileId() == context.profileId && currentProfile() == profile &&
                currentAuth() == capturedAuth
        }
        if (!current()) return
        mutableState.value = NotflixCollectionCreationState(request, busy = true)
        viewModelScope.launch {
            try {
                val result = creator.create(request, texts, year, current)
                if (current()) mutableState.value = NotflixCollectionCreationState(request, result = result)
                else mutableState.value = NotflixCollectionCreationState()
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
}
