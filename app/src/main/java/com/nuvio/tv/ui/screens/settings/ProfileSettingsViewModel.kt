package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.data.local.ProfileAgeLimitStore
import com.nuvio.tv.data.local.ProfileLockStateDataStore
import com.nuvio.tv.data.remote.supabase.SupabaseProfilePinVerifyResult
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.domain.repository.HiddenTitlesRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HiddenTitleEntry(val key: String, val title: String, val legacyTitle: String = title)

internal fun hiddenTitleLabel(key: String, storedTitle: String?, cachedTitle: String?): String =
    storedTitle?.takeIf { it.isNotBlank() } ?: cachedTitle?.takeIf { it.isNotBlank() } ?: key.substringAfter(':', key)

/** Existing memory caches only: an old hidden key must never trigger metadata/network work. */
internal fun cachedHiddenTmdbTitle(key: String, language: String, tmdb: TmdbService, metadata: TmdbMetadataService): String? {
    val type = ContentType.fromString(key.substringBefore(':'))
    if (type !in setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.TV)) return null
    val id = key.substringAfter(':', "")
    val tmdbId = if (id.startsWith("tmdb:")) id.removePrefix("tmdb:").toIntOrNull() else tmdb.cachedTmdbId(id)
    return tmdbId?.let {
        metadata.cachedLocalizedTitle(it.toString(), type, language)?.takeIf(String::isNotBlank)
            ?: metadata.cachedOriginalTitle(it.toString(), type)?.takeIf(String::isNotBlank)
    }
}

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val profileSyncService: ProfileSyncService,
    private val ageLimitStore: ProfileAgeLimitStore,
    profileLockStateDataStore: ProfileLockStateDataStore,
    private val hiddenTitlesRepository: HiddenTitlesRepository,
    private val metaRepository: MetaRepository,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    tmdbSettings: TmdbSettingsDataStore
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles
    val settingsProfileId: StateFlow<Int> = profileManager.activeProfileId

    val activeProfile: StateFlow<UserProfile?> = combine(profiles, profileManager.activeProfileId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Límite del perfil activo; null = sin límite. Solo en el dispositivo. */
    val activeAgeLimit: StateFlow<AgeLevel?> = profileManager.activeProfileId
        .flatMapLatest { ageLimitStore.observe(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** El PIN del perfil principal (id 1) es el que protege el límite. */
    val primaryPinEnabled: StateFlow<Boolean> = profileLockStateDataStore.pinEnabled
        .map { it[1] == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Títulos ocultos del perfil activo, con nombre cacheado cuando existe. */
    val hiddenTitles: StateFlow<List<HiddenTitleEntry>> = profileManager.activeProfileId
        .flatMapLatest { profile -> combine(hiddenTitlesRepository.observeMetadata(profile), tmdbSettings.settings,
            tmdbMetadataService.enrichmentVersion) { metadata, settings, _ -> metadata to settings.language } }
        .map { (metadata, language) ->
            metadata.keys.sorted().map { key ->
                val parts = key.split(":", limit = 2)
                val cached = if (parts.size == 2) metaRepository.getCachedMeta(parts[0], parts[1]) else null
                val known = metadata[key]?.title?.takeIf(String::isNotBlank)
                    ?: cached?.name?.takeIf(String::isNotBlank)
                    ?: cachedHiddenTmdbTitle(key, language, tmdbService, tmdbMetadataService)
                HiddenTitleEntry(key, hiddenTitleLabel(key, known, null),
                    cached?.name?.takeIf { it.isNotBlank() } ?: parts.getOrElse(1) { key })
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun unhideTitle(key: String) {
        viewModelScope.launch {
            hiddenTitlesRepository.show(profileManager.activeProfileId.value, key)
        }
    }

    val isPrimaryProfileActive: StateFlow<Boolean> = profileManager.activeProfileId
        .map { it == 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val canAddProfile: Boolean
        get() = profileManager.canCreateProfile

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _isPinOperationInProgress = MutableStateFlow(false)
    val isPinOperationInProgress: StateFlow<Boolean> = _isPinOperationInProgress.asStateFlow()

    fun setAgeLimit(level: AgeLevel?) {
        viewModelScope.launch {
            ageLimitStore.set(profileManager.activeProfileId.value, level)
        }
    }

    fun verifyPrimaryPin(pin: String, onComplete: (Result<SupabaseProfilePinVerifyResult>) -> Unit) {
        if (_isPinOperationInProgress.value) return
        viewModelScope.launch {
            _isPinOperationInProgress.value = true
            val result = profileSyncService.verifyProfilePin(1, pin)
            _isPinOperationInProgress.value = false
            onComplete(result)
        }
    }

    fun createProfile(
        name: String,
        avatarColorHex: String,
        usesPrimaryAddons: Boolean,
        usesPrimaryPlugins: Boolean,
        avatarId: String? = null
    ) {
        if (_isCreating.value) return
        viewModelScope.launch {
            _isCreating.value = true
            val newProfile = profileManager.createProfile(
                name = name,
                avatarColorHex = avatarColorHex,
                avatarId = avatarId
            )
            if (newProfile != null) {
                if (usesPrimaryAddons || usesPrimaryPlugins) {
                    profileManager.updateProfile(
                        newProfile.copy(
                            usesPrimaryAddons = usesPrimaryAddons,
                            usesPrimaryPlugins = usesPrimaryPlugins
                        )
                    )
                }
                profileSyncService.pushToRemote()
            }
            _isCreating.value = false
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            profileManager.updateProfile(profile)
            profileSyncService.pushToRemote()
        }
    }

    fun deleteProfile(id: Int) {
        viewModelScope.launch {
            profileManager.deleteProfile(id)
            profileSyncService.deleteProfileData(id)
            profileSyncService.pushToRemote()
        }
    }
}
