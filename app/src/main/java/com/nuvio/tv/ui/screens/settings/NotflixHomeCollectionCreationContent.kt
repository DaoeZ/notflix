package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.ui.appearance.AppearancePolicy
import com.nuvio.tv.ui.appearance.AppearanceState
import com.nuvio.tv.ui.appearance.NotflixCollectionCreationState
import com.nuvio.tv.ui.appearance.NotflixCollectionResult
import com.nuvio.tv.ui.appearance.NotflixCollectionAccess

@Composable
internal fun NotflixHomeCollectionCreationContent(
    appearance: AppearanceState,
    creation: NotflixCollectionCreationState,
    homeName: String,
    onCreate: () -> Unit,
    onManage: () -> Unit,
    manageFocusRequester: FocusRequester? = null,
    restoreFocusToken: Int = 0,
    onFocusRestored: (Int) -> Unit = {},
    access: NotflixCollectionAccess = NotflixCollectionAccess.CHECKING
) {
    if (!AppearancePolicy.NOTFLIX_AVAILABLE || appearance.context == null || appearance.effective != AppAppearance.NOTFLIX) return
    val ownManageFocus = remember { FocusRequester() }
    val manageFocus = manageFocusRequester ?: ownManageFocus
    val bringIntoView = remember { BringIntoViewRequester() }
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(restoreFocusToken, lifecycle) {
        if (restoreFocusToken != 0 && lifecycle == androidx.lifecycle.Lifecycle.State.RESUMED) {
            repeat(2) { withFrameNanos { } }
            if (manageFocus.requestFocus()) {
                bringIntoView.bringIntoView()
                onFocusRestored(restoreFocusToken)
            }
        }
    }
    val help = stringResource(R.string.notflix_collection_help, homeName)
    val account = stringResource(when (access) {
        NotflixCollectionAccess.CHECKING -> R.string.notflix_collection_account_checking
        NotflixCollectionAccess.LOCAL -> R.string.notflix_collection_account_local
        NotflixCollectionAccess.ACCOUNT_SYNC_PENDING -> R.string.notflix_collection_account_sync
    })
    val result = creation.result?.let {
        stringResource(when (it) {
            NotflixCollectionResult.CREATED_LOCAL -> R.string.notflix_collection_created_local
            NotflixCollectionResult.CREATED_ACCOUNT -> R.string.notflix_collection_created_account
            NotflixCollectionResult.EXISTS -> R.string.notflix_collection_exists
            NotflixCollectionResult.CONTEXT_CHANGED -> R.string.notflix_collection_context_changed
            NotflixCollectionResult.ACCOUNT_SYNC_PENDING -> R.string.notflix_collection_account_pending
            NotflixCollectionResult.FAILED -> R.string.notflix_collection_failed
        })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(account, Modifier.testTag("notflix-collection-account"))
        // Keep this focused row attached while saving. The VM and atomic store also reject duplicate requests.
        SettingsActionRow(
            title = stringResource(if (creation.busy) R.string.notflix_collection_creating
                else R.string.notflix_collection_create, homeName),
            subtitle = result ?: help,
            onClick = { if (!creation.busy) onCreate() },
            enabled = !appearance.readFailed && access != NotflixCollectionAccess.CHECKING,
            modifier = Modifier.testTag("notflix-create-home"),
            subtitleModifier = if (result != null) Modifier.testTag("notflix-collection-result") else Modifier
        )
        SettingsActionRow(
            title = stringResource(R.string.notflix_collection_manage),
            subtitle = null,
            onClick = { if (!creation.busy) onManage() },
            enabled = !creation.busy,
            modifier = Modifier.testTag("notflix-manage-collections")
                .focusRequester(manageFocus).bringIntoViewRequester(bringIntoView)
        )
    }
}
