@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.addon.QrCodeOverlay
import com.nuvio.tv.ui.theme.NuvioTheme

private enum class ProviderField { Tmdb, IntroDb }

/** Optional third-party provider keys; the official Settings components follow the active appearance. */
@Composable
fun NotflixProviderSettingsContent(
    viewModel: NotflixProviderSettingsViewModel = hiltViewModel(),
    initialFocusRequester: FocusRequester? = null
) {
    val values by viewModel.values.collectAsStateWithLifecycle()
    val phoneEntry by viewModel.phoneEntry.collectAsStateWithLifecycle()
    val phoneError by viewModel.phoneError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderField?>(null) }
    val notSet = stringResource(R.string.notflix_provider_not_set)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.notflix_provider_title),
            subtitle = stringResource(R.string.notflix_provider_subtitle)
        )

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = NuvioTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "notflix_provider_phone") {
                        SettingsActionRow(
                            title = stringResource(R.string.notflix_provider_phone_title),
                            subtitle = phoneError ?: stringResource(R.string.notflix_provider_phone_subtitle),
                            onClick = { viewModel.startPhoneEntry() },
                            modifier = Modifier.padding(top = NuvioTheme.spacing.xxs)
                        )
                    }
                    item(key = "notflix_provider_tmdb") {
                        SettingsActionRow(
                            title = stringResource(R.string.notflix_provider_tmdb_title),
                            subtitle = stringResource(R.string.notflix_provider_tmdb_subtitle),
                            value = maskProviderValue(values.tmdbApiKey, notSet),
                            onClick = { editing = ProviderField.Tmdb },
                            modifier = Modifier
                                .padding(top = NuvioTheme.spacing.xxs)
                                .then(
                                    if (initialFocusRequester != null) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                    item(key = "notflix_provider_introdb") {
                        SettingsActionRow(
                            title = stringResource(R.string.notflix_provider_introdb_title),
                            subtitle = stringResource(R.string.notflix_provider_introdb_subtitle),
                            // Blank uses the public IntroDB service, so it is not "not configured".
                            value = values.introDbBaseUrl.ifBlank { stringResource(R.string.notflix_provider_introdb_default) },
                            onClick = { editing = ProviderField.IntroDb }
                        )
                    }
                }
                SettingsVerticalScrollIndicators(state = listState)
            }
        }
    }

    editing?.let { field ->
        ProviderValueDialog(
            field = field,
            currentValue = when (field) {
                ProviderField.Tmdb -> values.tmdbApiKey
                ProviderField.IntroDb -> values.introDbBaseUrl
            },
            onSave = { value ->
                when (field) {
                    ProviderField.Tmdb -> viewModel.saveTmdbApiKey(value)
                    ProviderField.IntroDb -> viewModel.saveIntroDbBaseUrl(value)
                }
                editing = null
            },
            onClear = {
                when (field) {
                    ProviderField.Tmdb -> viewModel.clearTmdbApiKey()
                    ProviderField.IntroDb -> viewModel.clearIntroDbBaseUrl()
                }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    phoneEntry?.let { entry ->
        QrCodeOverlay(
            qrBitmap = entry.qrBitmap,
            serverUrl = entry.url,
            instruction = stringResource(R.string.notflix_provider_phone_instruction),
            onClose = { viewModel.stopPhoneEntry() }
        )
    }
}

@Composable
private fun ProviderValueDialog(
    field: ProviderField,
    currentValue: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(field, currentValue) { mutableStateOf(currentValue) }
    var inputFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val title = stringResource(
        when (field) {
            ProviderField.Tmdb -> R.string.notflix_provider_dialog_tmdb_title
            ProviderField.IntroDb -> R.string.notflix_provider_dialog_introdb_title
        }
    )

    LaunchedEffect(Unit) { runCatching { inputFocusRequester.requestFocus() } }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = stringResource(R.string.notflix_provider_dialog_subtitle),
        width = 700.dp
    ) {
        Card(
            onClick = { inputFocusRequester.requestFocus() },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { inputFocused = it.isFocused || it.hasFocus },
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(10.dp)
                ),
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = RoundedCornerShape(10.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = NuvioTheme.spacing.md)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocusRequester)
                        .onKeyEvent { event ->
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
                                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = NuvioTheme.colors.TextPrimary,
                        textDirection = TextDirection.Content
                    ),
                    cursorBrush = SolidColor(
                        if (inputFocused) NuvioTheme.colors.Primary else Color.Transparent
                    ),
                    decorationBox = { innerTextField ->
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.notflix_provider_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.colors.TextTertiary
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) { Text(stringResource(R.string.action_cancel)) }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            Button(
                onClick = onClear,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) { Text(stringResource(R.string.action_clear)) }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            Button(
                onClick = { onSave(value) },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

private fun maskProviderValue(value: String, notSetLabel: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return notSetLabel
    return if (trimmed.length <= 4) "••••" else "••••••${trimmed.takeLast(4)}"
}
