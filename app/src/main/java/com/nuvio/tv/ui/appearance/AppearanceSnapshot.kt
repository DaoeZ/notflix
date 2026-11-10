package com.nuvio.tv.ui.appearance

import android.util.Log
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.domain.model.AppAppearance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AppearanceSnapshot"
private const val PROFILE_READY_TIMEOUT_MS = 5_000L

/**
 * M54: persisted appearance of the active profile for callers outside the composition (the updater
 * ViewModel and its background job). It is a disk observation, never an authorization: it only
 * decides which repository the updater reads and which links «Acerca de» shows.
 */
@Singleton
class AppearanceSnapshot @Inject constructor(
    private val profiles: ProfileManager,
    private val store: NotflixAppearanceStore
) {

    /**
     * Unknown reads fail closed to Notflix, so no Nuvio release is ever fetched under that skin:
     * the update banner and the idle install gate only add to this decision, never replace it.
     */
    suspend fun isNotflixActive(): Boolean {
        if (!AppearancePolicy.NOTFLIX_AVAILABLE) return false
        // The DataStore-backed state starts at defaults that can pass for profile 1; deciding during
        // that window could read another profile's appearance. The wait is bounded and a slow or
        // broken load fails closed to Notflix.
        val ready = withTimeoutOrNull(PROFILE_READY_TIMEOUT_MS) {
            profiles.activeProfileReady.first { it }
        }
        if (ready == null) {
            Log.w(TAG, "Profile state unavailable after ${PROFILE_READY_TIMEOUT_MS} ms; assuming Notflix")
            return true
        }
        val profileId = profiles.persistedAppearanceProfile.value?.profileId
            ?: profiles.activeProfileId.value
        val knownIds = profiles.profiles.value.mapTo(mutableSetOf()) { it.id }
        val context = store.contextFor(profileId, true, knownIds) ?: return true
        return try {
            val read = store.observe(context).first()
            // A read error arrives as failed=true (the store emits a fallback instead of throwing);
            // treating it as Nuvio would let the background job fetch NuvioTV under Notflix.
            if (read.failed) {
                Log.w(TAG, "Appearance read failed for profile $profileId; assuming Notflix")
                true
            } else {
                AppearancePolicy.effective(read.requested) == AppAppearance.NOTFLIX
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Appearance read failed for profile $profileId; assuming Notflix", error)
            true
        }
    }
}
