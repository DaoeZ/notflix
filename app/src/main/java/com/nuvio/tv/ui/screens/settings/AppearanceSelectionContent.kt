package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.ui.appearance.AppearanceState
import com.nuvio.tv.ui.appearance.NotflixWordmark
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun AppearanceSelectionContent(
    state: AppearanceState,
    initialFocusRequester: FocusRequester? = null,
    onSelectHomeMotion: (Boolean) -> Unit = {},
    onSelectIntroEnabled: (Boolean) -> Unit = {},
    onSelectIntroSoundEnabled: (Boolean) -> Unit = {},
    onSelectPreviewEnabled: (Boolean) -> Unit = {},
    onSelectPreviewSoundEnabled: (Boolean) -> Unit = {},
    onSelectUiSoundsEnabled: (Boolean) -> Unit = {},
    onSelectWikipediaEnabled: (Boolean) -> Unit = {},
    collectionCreator: @Composable () -> Unit = {},
    onSelect: (AppAppearance) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.notflix_appearance_title))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppAppearance.entries.forEach { appearance ->
            var focused by remember { mutableStateOf(false) }
            Button(
                onClick = { onSelect(appearance) },
                enabled = state.context != null,
                colors = ButtonDefaults.colors(containerColor = NuvioTheme.colors.SurfaceVariant,
                    contentColor = NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = NuvioTheme.colors.Secondary,
                    focusedContentColor = NuvioTheme.colors.OnSecondary),
                border = selectedBorder(state.requested == appearance),
                modifier = Modifier.testTag("appearance-${appearance.storedValue}").onFocusChanged { focused = it.isFocused }
                    .then(if (appearance == AppAppearance.NUVIO && initialFocusRequester != null)
                        Modifier.focusRequester(initialFocusRequester) else Modifier)
                    .semantics { selected = state.requested == appearance }
            ) {
                // The brand wordmark (mirrored N), not plain text (promoter 09-11).
                if (appearance == AppAppearance.NUVIO) Text(marked(state.requested == appearance, stringResource(R.string.notflix_appearance_nuvio)))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.requested == appearance) Text("✓ ")
                    NotflixWordmark(16.dp, tint = if (focused) NuvioTheme.colors.OnSecondary else null) // red on the red focus fill would vanish
                }
            }
        }
        }
        Text(stringResource(R.string.notflix_appearance_active, stringResource(if (state.effective == AppAppearance.NUVIO)
            R.string.notflix_appearance_nuvio else R.string.notflix_appearance_notflix)), Modifier.testTag("appearance-effective"))
        if (state.context != null && state.effective == AppAppearance.NOTFLIX) {
            Text(stringResource(R.string.notflix_home_motion_title, stringResource(if (state.reduceHomeMotion)
                R.string.notflix_home_motion_reduced else R.string.notflix_home_motion_normal)))
            Text(stringResource(R.string.notflix_home_motion_help))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false, true).forEach { reduced ->
                    Button(onClick = { onSelectHomeMotion(reduced) },
                        colors = ButtonDefaults.colors(containerColor = NuvioTheme.colors.SurfaceVariant,
                            contentColor = NuvioTheme.colors.TextPrimary,
                            focusedContainerColor = NuvioTheme.colors.Secondary,
                            focusedContentColor = NuvioTheme.colors.OnSecondary),
                        border = selectedBorder(state.reduceHomeMotion == reduced),
                        modifier = Modifier.testTag(if (reduced) "home-motion-reduced" else "home-motion-normal")
                            .semantics { selected = state.reduceHomeMotion == reduced }
                    ) { Text(marked(state.reduceHomeMotion == reduced, stringResource(if (reduced) R.string.notflix_home_motion_reduced else R.string.notflix_home_motion_normal))) }
                }
            }
            Text(stringResource(R.string.notflix_intro_enabled))
            IntroOptionRow(state.introEnabled, "intro-enabled", onSelectIntroEnabled)
            Text(stringResource(R.string.notflix_intro_sound))
            IntroOptionRow(state.introSoundEnabled, "intro-sound", onSelectIntroSoundEnabled)
            Text(stringResource(R.string.notflix_intro_help))
            Text(stringResource(R.string.notflix_preview_enabled))
            IntroOptionRow(state.previewEnabled, "preview-enabled", onSelectPreviewEnabled)
            Text(stringResource(R.string.notflix_preview_sound))
            IntroOptionRow(state.previewSoundEnabled, "preview-sound", onSelectPreviewSoundEnabled)
            Text(stringResource(R.string.notflix_preview_help))
            Text(stringResource(R.string.notflix_ui_sounds))
            IntroOptionRow(state.uiSoundsEnabled, "ui-sounds", onSelectUiSoundsEnabled)
            if (com.nuvio.tv.ui.appearance.AppearancePolicy.NOTFLIX_AVAILABLE) {
                Text(stringResource(R.string.notflix_wikipedia_enabled))
                IntroOptionRow(state.wikipediaEnabled, "wikipedia-enabled", onSelectWikipediaEnabled)
                Text(stringResource(R.string.notflix_wikipedia_help))
                collectionCreator()
            }
        }
        if (state.saveFailed) Text(stringResource(R.string.notflix_save_failed))
    }
}

@Composable
private fun IntroOptionRow(enabled: Boolean, tag: String, onSelect: (Boolean) -> Unit) {
    if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active) {
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            listOf(true, false).forEach { value ->
                com.nuvio.tv.ui.appearance.notflix.NotflixSegment(
                    label = stringResource(if (value) R.string.notflix_intro_on else R.string.notflix_intro_off),
                    chosen = enabled == value,
                    onClick = { onSelect(value) },
                    testTag = "$tag-$value"
                )
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true, false).forEach { value ->
            Button(onClick = { onSelect(value) },
                colors = ButtonDefaults.colors(containerColor = NuvioTheme.colors.SurfaceVariant,
                    contentColor = NuvioTheme.colors.TextPrimary, focusedContainerColor = NuvioTheme.colors.Secondary,
                    focusedContentColor = NuvioTheme.colors.OnSecondary),
                border = selectedBorder(enabled == value),
                modifier = Modifier.testTag("$tag-$value").semantics { selected = enabled == value }) {
                Text(marked(enabled == value, stringResource(if (value) R.string.notflix_intro_on else R.string.notflix_intro_off)))
            }
        }
    }
}

// M29 (promoter): the chosen option must be visible, not only announced: accent border plus a check mark.
@Composable
private fun selectedBorder(selected: Boolean) = ButtonDefaults.border(
    border = if (selected) Border(BorderStroke(2.dp, if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active)
        com.nuvio.tv.ui.appearance.NotflixVisualSpec.Accent else NuvioTheme.colors.Secondary)) else Border.None)

private fun marked(selected: Boolean, label: String) = if (selected) "✓ $label" else label
