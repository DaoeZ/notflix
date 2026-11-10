package com.nuvio.tv.core.auth

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.sync.androidtv.AndroidTvChannelManager
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.data.local.ProfileLockStateDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val ACCOUNT_RESET_TAG = "AccountDataReset"

@Singleton
class AccountLocalDataResetService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileDataStoreFactory: ProfileDataStoreFactory,
    private val profileDataStore: ProfileDataStore,
    private val profileLockStateDataStore: ProfileLockStateDataStore,
    private val credentialStores: Set<@JvmSuppressWildcards ProfileScopedCredentialStore>,
    private val androidTvChannelManager: AndroidTvChannelManager
) {
    suspend fun clearAfterSignOut() = withContext(Dispatchers.IO) {
        try {
            // Begin can suspend after revocation; finally must cover that too.
            profileDataStoreFactory.beginAppearanceAccountReset()
            currentCoroutineContext().ensureActive()
            runCatching { androidTvChannelManager.clearAll() }
                .onFailure { Log.w(ACCOUNT_RESET_TAG, "Failed to clear Android TV channel data", it) }
            runCatching { profileDataStoreFactory.clearProfileScopedData() }
                .onFailure { Log.w(ACCOUNT_RESET_TAG, "Failed to clear profile-scoped data", it) }
            runCatching { profileDataStore.clearAll() }
                .onFailure { Log.w(ACCOUNT_RESET_TAG, "Failed to clear profile metadata", it) }
            runCatching { profileLockStateDataStore.clearAll() }
                .onFailure { Log.w(ACCOUNT_RESET_TAG, "Failed to clear profile lock states", it) }
            runCatching { credentialStores.forEach(ProfileScopedCredentialStore::clearAllProfiles) }
                .onFailure { Log.w(ACCOUNT_RESET_TAG, "Failed to clear profile credentials", it) }
            runCatching { clearAccountFiles() }
                .onFailure { Log.w(ACCOUNT_RESET_TAG, "Failed to clear account files", it) }
            currentCoroutineContext().ensureActive()
        } finally {
            // Only local appearance finalization is protected, not account work.
            withContext(NonCancellable) { profileDataStoreFactory.finishAppearanceAccountReset() }
        }
        // Cancellation may arrive in finally after the last check in try. Do
        // not rely on a dispatcher switch to reject a normal return here.
        currentCoroutineContext().ensureActive()
    }

    private fun clearAccountFiles() {
        val files = context.filesDir.listFiles() ?: return
        files.forEach { file ->
            if (isAccountFile(file)) {
                file.deleteRecursively()
            }
        }
    }

    private fun isAccountFile(file: File): Boolean {
        return file.name == "cw_enrichment" ||
            file.name == "plugin_code" ||
            file.name.startsWith("plugin_code_p")
    }
}
