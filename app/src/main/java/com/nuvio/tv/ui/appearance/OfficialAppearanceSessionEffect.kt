package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.NotflixAppearanceStore
import kotlinx.coroutines.flow.combine

// Only the official decision is restored; VM capabilities/requests are never saved.
val OfficialAppearanceDecisionSaver = Saver<OfficialAppearanceDecision?, List<Any>>(
    save = { it?.let { d -> listOf(d.serial, d.profileId, d.accountKey, d.storageRevision) } ?: emptyList() },
    restore = { if (it.isEmpty()) null else OfficialAppearanceDecision(it[0] as Long, it[1] as Int, it[2] as String, it[3] as Long) }
)

@Composable
fun OfficialAppearanceSessionEffect(
    decision: OfficialAppearanceDecision?,
    accountKey: String?,
    manager: ProfileManager,
    store: NotflixAppearanceStore,
    binding: OfficialAppearanceSessionBinding,
    onRetired: () -> Unit
) {
    val retire by rememberUpdatedState(onRetired)
    DisposableEffect(binding) {
        onDispose {
            binding.close()
        }
    }
    LaunchedEffect(binding, decision, accountKey) {
        combine(manager.persistedAppearanceProfile, manager.profiles, store.revision) { disk, profiles, revision ->
            val context = disk?.let { persisted ->
                store.contextFor(persisted.profileId, true, profiles.map { it.id }.toSet())
                    ?.let { AppearanceSessionContext(persisted, it) }
            }
            context to revision
        }.collect { (context, revision) ->
            if (binding.update(decision, context, revision, accountKey)) retire()
        }
    }
}
