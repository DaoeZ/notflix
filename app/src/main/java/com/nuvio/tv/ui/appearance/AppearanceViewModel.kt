package com.nuvio.tv.ui.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.NotflixCardOpening
import com.nuvio.tv.domain.model.NotflixCardSize
import com.nuvio.tv.domain.model.PersistedAppearanceProfile
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val manager: ProfileManager,
    private val store: NotflixAppearanceStore
) : ViewModel() {
    private val failedSelection = MutableStateFlow<Long?>(null)
    private val wikipediaRevoked = MutableStateFlow<Long?>(null)
    private val refresh = MutableStateFlow(0L)
    private val session = AppearanceSessionConfirmation()
    private val selection: Flow<AppearanceState> = combine(
        manager.persistedAppearanceProfile, manager.profiles, store.revision, refresh, session.confirmed
    ) { _, _, _, refreshId, _ ->
        // A queued combine emission must not revoke a newer explicit decision.
        val authorized = session.authorized(availableContext())
        Triple(authorized?.context?.storage, authorized?.attempt, refreshId)
    }
        .distinctUntilChanged()
        .flatMapLatest { (context, generation, _) ->
            if (context == null) flowOf(AppearanceState())
            else store.observe(context).map { read ->
                if (!store.isCurrent(context) || currentContext() != context) AppearanceState()
                else AppearanceState(context, read.requested, AppearancePolicy.effective(read.requested), read.failed,
                    selectionGeneration = requireNotNull(generation), reduceHomeMotion = read.reduceHomeMotion,
                    introEnabled = read.introEnabled, introSoundEnabled = read.introSoundEnabled,
                    introOptionsValid = read.introOptionsValid,
                    previewEnabled = read.previewEnabled, previewSoundEnabled = read.previewSoundEnabled,
                    previewOptionsValid = read.previewOptionsValid,
                    uiSoundsEnabled = read.uiSoundsEnabled, uiSoundsOptionsValid = read.uiSoundsOptionsValid,
                    wikipediaEnabled = read.wikipediaEnabled, wikipediaOptionsValid = read.wikipediaOptionsValid,
                    cardOpening = read.cardOpening, cardSize = read.cardSize)
            }.onStart {
                // Retrying a failed read in the same authorized session preserves
                // its last confirmed state. A different session never inherits it.
                if (state.value.context != context || state.value.selectionGeneration != generation) {
                    emit(AppearanceState())
                }
            }
        }

    val state: StateFlow<AppearanceState> = combine(selection, failedSelection, wikipediaRevoked) { selected, failed, revoked ->
        selected.copy(saveFailed = selected.context != null && selected.selectionGeneration == failed,
            wikipediaEnabled = selected.wikipediaEnabled && selected.selectionGeneration != revoked)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceState())

    fun select(appearance: AppAppearance) {
        saveSelection { context, _ -> store.select(context, appearance) }
    }

    fun selectHomeMotion(reduced: Boolean) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectHomeMotion(context, reduced, authorized) }
    }

    fun selectCardOpening(opening: NotflixCardOpening) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectCardOpening(context, opening, authorized) }
    }

    fun selectCardSize(size: NotflixCardSize) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectCardSize(context, size, authorized) }
    }

    fun selectIntroEnabled(enabled: Boolean) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectIntroEnabled(context, enabled, authorized) }
    }

    fun selectIntroSoundEnabled(enabled: Boolean) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectIntroSoundEnabled(context, enabled, authorized) }
    }

    fun selectPreviewEnabled(enabled: Boolean) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectPreviewEnabled(context, enabled, authorized) }
    }

    fun selectPreviewSoundEnabled(enabled: Boolean) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectPreviewSoundEnabled(context, enabled, authorized) }
    }

    fun selectUiSoundsEnabled(enabled: Boolean) {
        saveSelection(requireNotflix = true) { context, authorized -> store.selectUiSoundsEnabled(context, enabled, authorized) }
    }

    fun selectWikipediaEnabled(enabled: Boolean) {
        if (!AppearancePolicy.NOTFLIX_AVAILABLE) return
        if (!enabled) wikipediaRevoked.value = state.value.selectionGeneration
        saveSelection(requireNotflix = true) { context, authorized ->
            store.selectWikipediaEnabled(context, enabled, authorized).also { result ->
                if (enabled && result.isSuccess && authorized()) wikipediaRevoked.value = null
            }
        }
    }

    /** Reuses the captured M1 authorization at request, publication and disk write boundaries. */
    fun authorizesWikipedia(context: AppearanceContext, generation: Long): Boolean =
        AppearancePolicy.NOTFLIX_AVAILABLE && selectionIsCurrent(context, generation) &&
            wikipediaRevoked.value != generation && state.value.effective == AppAppearance.NOTFLIX &&
            !state.value.readFailed && state.value.wikipediaOptionsValid && state.value.wikipediaEnabled

    /** Captured authorization for the additive collection creator; no preference is changed. */
    fun authorizesCollectionCreation(context: AppearanceContext, generation: Long): Boolean =
        AppearancePolicy.NOTFLIX_AVAILABLE && selectionIsCurrent(context, generation) &&
            state.value.effective == AppAppearance.NOTFLIX && !state.value.readFailed

    private fun selectionIsCurrent(context: AppearanceContext, generation: Long): Boolean =
        state.value.context == context && currentContext() == context &&
            state.value.selectionGeneration == generation && currentAuthorization()?.attempt == generation &&
            store.isCurrent(context)

    private fun saveSelection(
        requireNotflix: Boolean = false,
        write: suspend (AppearanceContext, () -> Boolean) -> Result<Unit>
    ) {
        val captured = state.value.context ?: return
        val generation = state.value.selectionGeneration
        val authorized = {
            selectionIsCurrent(captured, generation) && (!requireNotflix || state.value.effective == AppAppearance.NOTFLIX)
        }
        if (!authorized()) return
        viewModelScope.launch {
            // A queued coroutine may start after a profile/session change.
            if (!authorized()) return@launch
            val result = write(captured, authorized)
            // A completion from another profile/session must never affect the incoming UI.
            if (authorized()) {
                failedSelection.value = if (result.isFailure) generation else null
                // A failed read completes its stream with a fallback. A confirmed
                // save must reopen it too, rather than leaving that fallback stuck.
                if (result.isSuccess && state.value.readFailed) retry()
            }
        }
    }

    fun retry() { refresh.value++ }

    fun beginSessionConfirmation(expectedProfileId: Int): AppearanceConfirmationRequest? =
        session.begin(availableContext()?.takeIf { it.persisted.profileId == expectedProfileId })

    fun confirmSession(request: AppearanceConfirmationRequest): Boolean =
        session.confirm(request, availableContext())

    fun cancelSessionConfirmation() = session.revoke()

    private fun availableContext(
        persisted: PersistedAppearanceProfile? = manager.persistedAppearanceProfile.value,
        profiles: List<UserProfile> = manager.profiles.value
    ): AppearanceSessionContext? = persisted?.let { disk ->
        store.contextFor(disk.profileId, true, profiles.map { it.id }.toSet())?.let { storage ->
            AppearanceSessionContext(disk, storage)
        }
    }

    private fun currentAuthorization() = session.authorized(availableContext())
    private fun currentContext() = currentAuthorization()?.context?.storage
}
