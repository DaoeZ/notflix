package com.nuvio.tv.core.offline

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.data.local.ProfileAgeLimitStore
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.ui.screens.stream.StreamPlaybackInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class NotflixOfflineViewModel @Inject constructor(
    private val auth: AuthManager,
    private val profiles: ProfileManager,
    private val limits: ProfileAgeLimitStore,
    private val videos: NotflixOfflineVideos,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val owner = combine(auth.authState, profiles.activeProfileId, profiles.activeProfileReady) { state, profile, ready ->
        if (ready) accountKey(state)?.let { it to profile } else null
    }.transformLatest { scope ->
        emit(null)
        if (scope != null) {
            limits.observe(scope.second).collect { limit ->
                emit(if (limit == null) offlineOwnerKey(scope.first, scope.second) else null)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun list(owner: String) = videos.list(owner).takeIf { owner == currentOwner() }.orEmpty()

    suspend fun play(owner: String, key: String): OfflineVideoState? =
        list(owner).firstOrNull { it.video.key == key && it.playableUri != null }

    suspend fun enqueue(owner: String, info: StreamPlaybackInfo) {
        check(profiles.activeProfileId.value == info.profileId && owner == currentOwner())
        require(!info.isExternal && !info.isTorrent && info.ytId == null)
        videos.enqueue(owner, info.title, Uri.parse(requireNotNull(info.url)), info.filename, info.headers.orEmpty()) {
            currentIdentity() == owner
        }
    }

    suspend fun openReceiver(owner: String): Pair<String, NotflixOfflineReceiver> {
        check(owner == currentOwner())
        val address = checkNotNull(DeviceIpAddress.get(context))
        val receiver = NotflixOfflineReceiver(address, videos.transferDirectory()) { partial, name ->
            runBlocking {
                check(owner == currentOwner())
                videos.acceptTransfer(owner, partial, name) { currentIdentity() == owner }
            }
        }
        try {
            receiver.start(20_000, true)
        } catch (error: Exception) {
            receiver.close()
            throw error
        }
        return "$address:${receiver.listeningPort}" to receiver
    }

    suspend fun remove(owner: String, key: String) {
        check(owner == currentOwner())
        videos.remove(owner, key)
    }

    private suspend fun currentOwner(): String? {
        val account = accountKey(auth.authState.value) ?: return null
        val profile = profiles.activeProfileId.value
        if (!profiles.activeProfileReady.value || limits.observe(profile).first() != null) return null
        return offlineOwnerKey(account, profile).takeIf {
            account == accountKey(auth.authState.value) && profile == profiles.activeProfileId.value
        }
    }

    private fun currentIdentity(): String? =
        accountKey(auth.authState.value)?.let { offlineOwnerKey(it, profiles.activeProfileId.value) }

    private fun accountKey(state: AuthState): String? = when (state) {
        is AuthState.FullAccount -> state.userId
        AuthState.SignedOut -> "local"
        AuthState.Loading -> null
    }
}
