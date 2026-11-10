package com.nuvio.tv.ui.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.ProfileSettingsSyncService
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.core.sync.SetProfilePinResult
import com.nuvio.tv.data.local.ProfileLockStateDataStore
import com.nuvio.tv.data.remote.supabase.AvatarCatalogItem
import com.nuvio.tv.data.remote.supabase.AvatarRepository
import com.nuvio.tv.data.remote.supabase.ProfileBackgroundCatalogItem
import com.nuvio.tv.data.remote.supabase.ProfileBackgroundRepository
import com.nuvio.tv.data.remote.supabase.SupabaseProfilePinVerifyResult
import com.nuvio.tv.data.repository.MemberAccessRepository
import com.nuvio.tv.domain.model.CosmeticEntitlement
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/** M52: outcome of an age-limit change, enforced by [ProfileSelectionViewModel.setAgeLimit]. */
sealed interface AgeLimitUpdateResult {
    data object Applied : AgeLimitUpdateResult

    /** The primary profile has a PIN and the change still needs it; nothing was written. */
    data object AuthorizationRequired : AgeLimitUpdateResult

    data class InvalidPin(val retryAfterSeconds: Int) : AgeLimitUpdateResult
    data object VerificationFailed : AgeLimitUpdateResult
}

/**
 * M52: changing an age limit needs the primary PIN when that profile has one, and only when the
 * value really changes (setting the same level never asks for a PIN).
 */
internal fun ageLimitChangeNeedsPrimaryPin(
    primaryHasPin: Boolean,
    currentLevel: com.nuvio.tv.core.profile.AgeLevel?,
    newLevel: com.nuvio.tv.core.profile.AgeLevel?
): Boolean {
    if (currentLevel == newLevel) return false
    return com.nuvio.tv.core.profile.requiresPrimaryPin(
        primaryHasPin = primaryHasPin,
        changingAgeLimit = true,
        leavingLimitedProfile = false
    )
}

sealed interface CreateProfileResult {    data class Created(
        val profile: UserProfile,
        val settingsCopyResult: Result<Unit>?
    ) : CreateProfileResult

    data object Failed : CreateProfileResult
}

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val profileSyncService: ProfileSyncService,
    private val profileSettingsSyncService: ProfileSettingsSyncService,
    private val avatarRepository: AvatarRepository,
    private val profileBackgroundRepository: ProfileBackgroundRepository,
    memberAccessRepository: MemberAccessRepository,
    private val profileLockStateDataStore: ProfileLockStateDataStore,
    private val ageLimitStore: com.nuvio.tv.data.local.ProfileAgeLimitStore,
    private val themeDataStore: com.nuvio.tv.data.local.ThemeDataStore
) : ViewModel() {
    val activeProfileId: StateFlow<Int> = profileManager.activeProfileId
    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles

    /** Límite del perfil activo; si no es null, salir de él exige el PIN principal (decisión 4). */
    val activeAgeLimit: StateFlow<com.nuvio.tv.core.profile.AgeLevel?> = profileManager.activeProfileId
        .flatMapLatest { ageLimitStore.observe(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val canAddProfile: Boolean
        get() = profileManager.canCreateProfile

    private val _avatarCatalog = MutableStateFlow<List<AvatarCatalogItem>>(emptyList())
    val avatarCatalog: StateFlow<List<AvatarCatalogItem>> = _avatarCatalog.asStateFlow()

    val hasProfileAvatarAccess: StateFlow<Boolean> = memberAccessRepository.access
        .map { access -> access.entitlements.includes(CosmeticEntitlement.PROFILE_AVATARS) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasProfileBackgroundAccess: StateFlow<Boolean> = memberAccessRepository.access
        .map { access -> access.entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val profileBackgroundCatalog: StateFlow<List<ProfileBackgroundCatalogItem>> = combine(
        profileBackgroundRepository.catalog,
        hasProfileBackgroundAccess
    ) { catalog, hasAccess -> if (hasAccess) catalog else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isCopyingSettings = MutableStateFlow(false)
    val isCopyingSettings: StateFlow<Boolean> = _isCopyingSettings.asStateFlow()

    val profilePinEnabled: StateFlow<Map<Int, Boolean>> = profileLockStateDataStore.pinEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _isPinOperationInProgress = MutableStateFlow(false)
    val isPinOperationInProgress: StateFlow<Boolean> = _isPinOperationInProgress.asStateFlow()

    // Declared before init: the theme collector below can emit synchronously on
    // Main.immediate when the theme stores are already cached.
    private val _profileThemes = MutableStateFlow<Map<Int, com.nuvio.tv.domain.model.AppTheme>>(emptyMap())
    val profileThemes: StateFlow<Map<Int, com.nuvio.tv.domain.model.AppTheme>> = _profileThemes.asStateFlow()

    /** Profiles with the KIDS preset (all-ages limit): the selector draws the KIDS avatar and badge. */
    val kidsProfileIds: StateFlow<Set<Int>> = profileManager.profiles
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptySet())
            else combine(list.map { p -> ageLimitStore.observe(p.id).map { p.id to it } }) { pairs ->
                pairs.filter { it.second == NOTFLIX_KIDS_AGE_LEVEL }.map { it.first }.toSet()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        refreshProfilePinStates()
        viewModelScope.launch {
            hasProfileAvatarAccess.collectLatest { hasAccess ->
                loadAvatarCatalog(hasAccess)
            }
        }
        viewModelScope.launch {
            hasProfileBackgroundAccess.collectLatest { hasAccess ->
                if (hasAccess) {
                    profileBackgroundRepository.ensureLoaded()
                } else {
                    profileBackgroundRepository.invalidateCache()
                }
            }
        }
        viewModelScope.launch {
            profiles.flatMapLatest { profileList ->
                if (profileList.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        profileList.map { profile ->
                            themeDataStore.observeThemeForProfile(profile.id)
                                .map { theme -> profile.id to (theme ?: com.nuvio.tv.domain.model.AppTheme.WHITE) }
                        }
                    ) { entries -> entries.toMap() }
                }
            }.collectLatest { themes ->
                _profileThemes.value = themes
            }
        }
    }

    fun loadAvatarCatalog() {
        viewModelScope.launch {
            loadAvatarCatalog(hasProfileAvatarAccess.value)
        }
    }

    private suspend fun loadAvatarCatalog(hasMemberAccess: Boolean) {
        try {
            _avatarCatalog.value = avatarRepository.getAvatarCatalog(hasMemberAccess)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("ProfileSelectionVM", "Failed to load avatar catalog", error)
        }
    }

    fun getAvatarImageUrl(avatarId: String?): String? {
        if (avatarId == null) return null
        return avatarRepository.getAvatarImageUrl(avatarId, _avatarCatalog.value)
    }

    fun loadProfileBackground(id: String) {
        if (hasProfileBackgroundAccess.value) profileBackgroundRepository.loadSelectedAndPreload(id)
    }

    fun preloadProfileBackgrounds() {
        if (hasProfileBackgroundAccess.value) profileBackgroundRepository.preloadImages()
    }

    var isSelectingProfile = false
        private set

    fun selectProfile(id: Int, onComplete: () -> Unit, onFailure: () -> Unit) {
        if (isSelectingProfile) return
        isSelectingProfile = true
        viewModelScope.launch {
            try {
                profileManager.setActiveProfile(id)
                onComplete()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ProfileSelectionVM", "Failed to select profile", error)
                onFailure()
            } finally {
                isSelectingProfile = false
            }
        }
    }

    fun createProfile(
        name: String,
        avatarColorHex: String,
        avatarId: String? = null,
        // M53: private gallery photo. Kept separate from avatarId (Nuvio catalogue) and written
        // right after creation so the push below already carries it.
        avatarUrl: String? = null,
        copyFromProfileId: Int? = null,
        copyProviderCredentials: Boolean = false,
        ageLimit: com.nuvio.tv.core.profile.AgeLevel? = null,
        // Notflix profiles share the main profile's add-ons and plugins so their Home has catalogs at once.
        usesPrimaryLibrary: Boolean = false,
        onComplete: (CreateProfileResult) -> Unit = {}
    ) {
        if (_isCreating.value) return
        viewModelScope.launch {
            _isCreating.value = true
            val result = try {
                // Creating the second profile swaps the app to "Who's watching?" and clears this
                // ViewModel mid-way; the age limit and the push must still land (promoter 09-11).
                withContext(NonCancellable) {
                val created = profileManager.createProfile(
                    name = name,
                    avatarColorHex = avatarColorHex,
                    usesPrimaryAddons = usesPrimaryLibrary,
                    usesPrimaryPlugins = usesPrimaryLibrary,
                    avatarId = avatarId
                )
                val profile = if (created != null && avatarUrl != null) {
                    created.copy(avatarUrl = avatarUrl).also { profileManager.updateProfile(it) }
                } else {
                    created
                }
                if (profile != null) {
                    if (ageLimit != null) ageLimitStore.set(profile.id, ageLimit)
                    profileSyncService.pushToRemote()
                    val copyResult = copyFromProfileId?.let { sourceProfileId ->
                        profileSettingsSyncService.copyProfileSetup(
                            sourceProfileId = sourceProfileId,
                            targetProfileId = profile.id,
                            copyProviderCredentials = copyProviderCredentials
                        )
                    }
                    refreshProfilePinStates()
                    CreateProfileResult.Created(profile, copyResult)
                } else {
                    CreateProfileResult.Failed
                }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ProfileSelectionVM", "Failed to create profile", error)
                CreateProfileResult.Failed
            } finally {
                _isCreating.value = false
            }
            onComplete(result)
        }
    }

    fun copyProfileSettings(
        sourceProfileId: Int,
        targetProfileId: Int,
        copyProviderCredentials: Boolean,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (_isCopyingSettings.value) return
        viewModelScope.launch {
            _isCopyingSettings.value = true
            val result = try {
                profileSettingsSyncService.copyProfileSetup(
                    sourceProfileId = sourceProfileId,
                    targetProfileId = targetProfileId,
                    copyProviderCredentials = copyProviderCredentials
                )
            } finally {
                _isCopyingSettings.value = false
            }
            onComplete(result)
        }
    }

    fun updateProfile(profile: UserProfile) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            profileManager.updateProfile(profile)
            profileSyncService.pushToRemote()
            refreshProfilePinStates()
            _isSaving.value = false
        }
    }

    /** M52: age limit of one profile, so «Editar perfil» in Notflix can open on the stored value. */
    fun observeAgeLimit(profileId: Int): kotlinx.coroutines.flow.Flow<com.nuvio.tv.core.profile.AgeLevel?> =
        ageLimitStore.observe(profileId)

    /**
     * M52 security rule (mandatory review note, 09-11): an age limit is only written here, and only
     * when the change is authorised. If the primary profile has a PIN, any change needs that PIN;
     * without it the limit is left untouched and [onResult] reports [AgeLimitUpdateResult.AuthorizationRequired].
     */
    fun setAgeLimit(
        profileId: Int,
        level: com.nuvio.tv.core.profile.AgeLevel?,
        primaryPin: String? = null,
        onResult: (AgeLimitUpdateResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val current = ageLimitStore.observe(profileId).first()
            val primary = profiles.value.firstOrNull { it.isPrimary }
            val primaryHasPin = primary != null && profilePinEnabled.value[primary.id] == true
            if (!ageLimitChangeNeedsPrimaryPin(primaryHasPin, current, level)) {
                ageLimitStore.set(profileId, level)
                onResult(AgeLimitUpdateResult.Applied)
                return@launch
            }
            if (primaryPin.isNullOrEmpty() || primary == null) {
                onResult(AgeLimitUpdateResult.AuthorizationRequired)
                return@launch
            }
            verifyProfilePin(primary.id, primaryPin) { result ->
                val verify = result.getOrNull()
                when {
                    verify == null -> onResult(AgeLimitUpdateResult.VerificationFailed)
                    verify.unlocked -> viewModelScope.launch {
                        ageLimitStore.set(profileId, level)
                        onResult(AgeLimitUpdateResult.Applied)
                    }
                    else -> onResult(AgeLimitUpdateResult.InvalidPin(verify.retryAfterSeconds))
                }
            }
        }
    }

    fun deleteProfile(id: Int) {
        viewModelScope.launch {
            profileManager.deleteProfile(id)
            profileSyncService.deleteProfileData(id)
            profileSyncService.pushToRemote()
            refreshProfilePinStates()
        }
    }

    fun refreshProfilePinStates() {
        viewModelScope.launch {
            var attempt = 0
            while (attempt < 4) {
                val result = profileSyncService.pullProfileLockStates()
                if (result.isSuccess) {
                    profileLockStateDataStore.replaceAll(result.getOrNull().orEmpty())
                    return@launch
                }
                Log.e(
                    "ProfileSelectionVM",
                    "Failed to refresh profile PIN states (attempt=$attempt)",
                    result.exceptionOrNull()
                )
                attempt++
                delay(2000L * attempt)
            }
        }
    }

    fun isProfilePinEnabled(profileId: Int): Boolean {
        return profilePinEnabled.value[profileId] == true
    }

    fun setProfilePin(
        profileId: Int,
        pin: String,
        currentPin: String? = null,
        onComplete: (SetProfilePinResult) -> Unit
    ) {
        if (_isPinOperationInProgress.value) return
        viewModelScope.launch {
            _isPinOperationInProgress.value = true
            val result = profileSyncService.setProfilePin(profileId, pin, currentPin)
            // Server reporting CurrentPinRequired means a PIN exists remotely —
            // reconcile local cache so we never forget it again.
            if (result is SetProfilePinResult.Success || result is SetProfilePinResult.CurrentPinRequired) {
                profileLockStateDataStore.setPinEnabled(profileId, true)
            }
            _isPinOperationInProgress.value = false
            onComplete(result)
        }
    }

    fun clearProfilePin(profileId: Int, currentPin: String? = null, onComplete: (Boolean) -> Unit) {
        if (_isPinOperationInProgress.value) return
        viewModelScope.launch {
            _isPinOperationInProgress.value = true
            val success = profileSyncService.clearProfilePin(profileId, currentPin).isSuccess
            if (success) {
                profileLockStateDataStore.setPinEnabled(profileId, false)
            }
            _isPinOperationInProgress.value = false
            onComplete(success)
        }
    }

    fun verifyProfilePin(profileId: Int, pin: String, onComplete: (Result<SupabaseProfilePinVerifyResult>) -> Unit) {
        if (_isPinOperationInProgress.value) return
        viewModelScope.launch {
            _isPinOperationInProgress.value = true
            val result = profileSyncService.verifyProfilePin(profileId, pin)
            _isPinOperationInProgress.value = false
            onComplete(result)
        }
    }
}
