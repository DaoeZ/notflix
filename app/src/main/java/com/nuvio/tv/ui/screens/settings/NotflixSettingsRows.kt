package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.R
import com.nuvio.tv.data.local.PlayerSettings

/** A presentation projection of existing rows; never a preference store or a writer. */
internal data class NotflixSettingsRowFilter(val ids: Set<String>, val hideLabels: Boolean = true) {
    fun includes(id: String?): Boolean = id == null || id.startsWith("navigate.") || id in ids
}

internal val LocalNotflixSettingsRows = staticCompositionLocalOf<NotflixSettingsRowFilter?> { null }
internal val LocalNotflixSettingsFrame = staticCompositionLocalOf { false }
internal val LocalNotflixSettingsFocus = staticCompositionLocalOf<NotflixSettingsFocus?> { null }
internal val NotflixSettingsRowId = SemanticsPropertyKey<String>("NotflixSettingsRowId")

/** Non-snapshot focus bookkeeping: moving the remote does not invalidate the whole settings pane. */
internal class NotflixSettingsFocus(val rememberRow: (String) -> Unit) {
    val requesters = linkedMapOf<String, FocusRequester>()
    val enabled = linkedSetOf<String>()
    var last: String? = null
    fun target(): FocusRequester? = last?.takeIf { it in enabled }?.let(requesters::get)
        ?: enabled.firstOrNull()?.let(requesters::get)
}

@Composable
internal fun notflixSettingsRowVisible(id: String?): Boolean =
    !LocalNotflixPresentation.current.active || LocalNotflixSettingsRows.current?.includes(id) != false

@Composable
internal fun notflixSettingsGroupVisible(vararg prefixes: String): Boolean =
    LocalNotflixSettingsRows.current?.ids?.any { id -> prefixes.any(id::startsWith) } ?: true

@Composable
internal fun Modifier.notflixSettingsRowFocus(id: String, enabled: Boolean, onEnter: () -> Unit): Modifier {
    val host = LocalNotflixSettingsFocus.current ?: return this
    val requester = remember(host, id) { host.requesters.getOrPut(id) { FocusRequester() } }
    SideEffect { if (enabled) host.enabled.add(id) else host.enabled.remove(id) }
    DisposableEffect(host, id) { onDispose { host.enabled.remove(id); host.requesters.remove(id) } }
    return this.focusRequester(requester).onFocusChanged {
        if (it.isFocused) { host.last = id; host.rememberRow(id) }
    }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
            if (enabled && event.nativeKeyEvent.repeatCount == 0) onEnter()
            true
        } else false
    }.semantics { this[NotflixSettingsRowId] = id }
}

@Composable
internal fun NotflixNumberSettingsRow(title: String, subtitle: String?, current: Int, values: List<Int>,
    valueText: String, enabled: Boolean, modifier: Modifier, id: String?, scale: Float, onSelect: (Int) -> Unit,
    valueLabel: ((Int) -> String)? = null) {
    val instant = stringResource(R.string.autoplay_timeout_instant)
    val unlimited = stringResource(R.string.autoplay_timeout_unlimited)
    val toleranceOff = stringResource(R.string.autosync_tolerance_off)
    val timeout = id?.endsWith(".autoplay_timeout_title") == true
    SettingsSingleChoiceRow(title = title, subtitle = subtitle, value = valueText, selectedValue = current,
        options = (values + current).distinct().map { value ->
            val label = if (valueLabel != null) valueLabel(value)
            else if (id?.endsWith(".playback_buffer_max") == true) {
                if (value == current) valueText else "${value}s"
            } else if (id?.endsWith(".autosync_tolerance_title") == true) {
                if (value <= 0) toleranceOff else stringResource(R.string.autosync_tolerance_value, value)
            } else if (timeout) when (value) {
                0 -> instant
                PlayerSettings.STREAM_AUTOPLAY_TIMEOUT_UNLIMITED -> unlimited
                else -> "${value}s"
            } else notflixNumberLabel(value, current, valueText, scale)
            SettingsPickerOption(value, label)
        }, onOptionSelected = onSelect, modifier = modifier, settingId = id, enabled = enabled)
}
