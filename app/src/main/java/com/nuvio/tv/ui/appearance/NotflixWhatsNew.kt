package com.nuvio.tv.ui.appearance

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.screens.profile.NotflixPanelRow
import com.nuvio.tv.ui.screens.profile.NotflixProfilePanel
import kotlinx.coroutines.launch

/** M58: what to do with the device's last seen version; 0 means the device has none yet. */
internal enum class NotflixWhatsNewAction { NOTHING, MARK_SEEN, SHOW }

internal fun notflixWhatsNewAction(
    lastSeenVersionCode: Int,
    currentVersionCode: Int,
    items: List<String>,
    enabled: Boolean = true,
    canPresent: Boolean = true,
    handled: Boolean = false,
    /** The APK was updated over an older one (Android's first install time != last update time). */
    updatedInstall: Boolean = false
): NotflixWhatsNewAction {
    if (handled || currentVersionCode <= 0) return NotflixWhatsNewAction.NOTHING
    // A new install always stores its version, even when there is nothing to announce yet. Betas
    // before M58 never stored one, so an update over them has no version either: that is not a
    // new install and must still show the notes once.
    if (lastSeenVersionCode <= 0 && !updatedInstall) return NotflixWhatsNewAction.MARK_SEEN
    if (!enabled || !canPresent || items.none { it.isNotBlank() }) return NotflixWhatsNewAction.NOTHING
    return if (lastSeenVersionCode <= 0 || currentVersionCode > lastSeenVersionCode) NotflixWhatsNewAction.SHOW
        else NotflixWhatsNewAction.NOTHING
}

/**
 * M58: the same centred panel as the profile dialogs, with the version, the bullet list and one
 * focused «Entendido» row. Back is handled by the panel's dialog dismiss request.
 */
@Composable
internal fun NotflixWhatsNewPanel(
    versionName: String,
    items: List<String>,
    onDismiss: () -> Unit
) {
    val ackFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 60.dp.toPx() }
    LaunchedEffect(versionName, items) {
        repeat(2) { withFrameNanos { } }
        runCatching { ackFocus.requestFocus() }
    }
    NotflixProfilePanel(
        title = stringResource(R.string.notflix_whats_new_title),
        onDismiss = onDismiss,
        modifier = Modifier.onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val direction = when (key.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> 1
                AndroidKeyEvent.KEYCODE_DPAD_UP -> -1
                else -> 0
            }
            if (direction == 0 || scroll.maxValue == 0) false
            else {
                if (key.action == AndroidKeyEvent.ACTION_DOWN) {
                    scope.launch { scroll.scrollBy(direction * scrollStep) }
                }
                true
            }
        }.testTag("notflix-whats-new-panel")
    ) {
        Text(
            text = stringResource(R.string.notflix_whats_new_version, versionName),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 300.dp)
                .verticalScroll(scroll)
                .testTag("notflix-whats-new-items"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        color = Spec.Accent,
                        fontFamily = Spec.BodyFont,
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = item,
                        color = Spec.Text,
                        fontFamily = Spec.BodyFont,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
        NotflixPanelRow(
            label = stringResource(R.string.notflix_whats_new_ack),
            onClick = onDismiss,
            modifier = Modifier
                .padding(top = 12.dp)
                .focusRequester(ackFocus)
                .testTag("notflix-whats-new-ack")
        )
    }
}

/**
 * M58: automatic notice. Only Home passes [enabled] (profile resolved, intro finished, no player or
 * overlay). A missing device version is initialized even while presentation is disabled. Window
 * focus only gates opening: this dialog will itself take focus away from the Activity window.
 */
@Composable
internal fun NotflixWhatsNewHost(
    enabled: Boolean,
    lastSeenVersionCode: Int?,
    currentVersionCode: Int,
    versionName: String,
    items: List<String>,
    onSeen: (Int) -> Unit,
    canPresent: Boolean = true,
    updatedInstall: Boolean = false
) {
    // Save the version, not just a Boolean: restored state can belong to the previous APK.
    var visibleVersion by rememberSaveable { mutableStateOf<Int?>(null) }
    var handledVersion by rememberSaveable { mutableStateOf<Int?>(null) }
    val handled = handledVersion == currentVersionCode
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val ready = canPresent && windowFocused && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(enabled) {
        if (!enabled) visibleVersion = null
    }
    LaunchedEffect(enabled, ready, lastSeenVersionCode, currentVersionCode, items, handled) {
        if (lastSeenVersionCode == null) return@LaunchedEffect
        when (notflixWhatsNewAction(lastSeenVersionCode, currentVersionCode, items, enabled, ready, handled, updatedInstall)) {
            NotflixWhatsNewAction.SHOW -> {
                // Let existing dialogs claim their window before opening another one.
                repeat(2) { withFrameNanos { } }
                visibleVersion = currentVersionCode
                handledVersion = currentVersionCode
            }
            NotflixWhatsNewAction.MARK_SEEN -> handledVersion = currentVersionCode
            NotflixWhatsNewAction.NOTHING -> Unit
        }
    }
    // Independent of window focus/eligibility: opening our dialog must not cancel its write.
    LaunchedEffect(handled, currentVersionCode) {
        if (handled) onSeen(currentVersionCode)
    }
    if (visibleVersion == currentVersionCode && enabled) {
        NotflixWhatsNewPanel(versionName = versionName, items = items, onDismiss = { visibleVersion = null })
    }
}
