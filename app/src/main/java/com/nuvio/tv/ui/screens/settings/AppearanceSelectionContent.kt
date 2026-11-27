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
import com.nuvio.tv.domain.model.NotflixCardOpening
import com.nuvio.tv.domain.model.NotflixCardSize
import com.nuvio.tv.ui.appearance.AppearanceState
import com.nuvio.tv.ui.appearance.NotflixWordmark
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun AppearanceSelectionContent(
    state: AppearanceState,
    initialFocusRequester: FocusRequester? = null,
    onSelectHomeMotion: (Boolean) -> Unit = {},
    onSelectCardOpening: (NotflixCardOpening) -> Unit = {},
    onSelectCardSize: (NotflixCardSize) -> Unit = {},
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
        if (notflixSettingsRowVisible("AppearanceSelectionContent.appearance")) {
        if (LocalNotflixSettingsFrame.current) {
            SettingsSingleChoiceRow(title = stringResource(R.string.notflix_appearance_title),
                settingId = "AppearanceSelectionContent.appearance",
                value = stringResource(if (state.effective == AppAppearance.NUVIO) R.string.notflix_appearance_nuvio else R.string.notflix_appearance_notflix),
                options = AppAppearance.entries.map { SettingsPickerOption(it, stringResource(
                    if (it == AppAppearance.NUVIO) R.string.notflix_appearance_nuvio else R.string.notflix_appearance_notflix)) },
                selectedValue = state.requested, onOptionSelected = onSelect, stateKey = state.context,
                focusRequester = initialFocusRequester, enabled = state.context != null)
        } else {
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
        }
        }
        if (state.context != null && state.effective == AppAppearance.NOTFLIX) {
            // 1. Visual look (Theme colour, UI scale, card size/opening, motion)
            if (com.nuvio.tv.ui.appearance.AppearancePolicy.NOTFLIX_AVAILABLE &&
                notflixSettingsRowVisible("AppearanceSelectionContent.notflix_wikipedia_enabled")
            ) {
                val accentNames = mapOf(
                    com.nuvio.tv.ui.appearance.NotflixAccentColor.RED to stringResource(R.string.extras_accent_red),
                    com.nuvio.tv.ui.appearance.NotflixAccentColor.BLUE to stringResource(R.string.extras_accent_blue),
                    com.nuvio.tv.ui.appearance.NotflixAccentColor.GREEN to stringResource(R.string.extras_accent_green),
                    com.nuvio.tv.ui.appearance.NotflixAccentColor.PURPLE to stringResource(R.string.extras_accent_purple),
                    com.nuvio.tv.ui.appearance.NotflixAccentColor.ORANGE to stringResource(R.string.extras_accent_orange),
                    com.nuvio.tv.ui.appearance.NotflixAccentColor.PINK to stringResource(R.string.extras_accent_pink),
                )
                val accent = com.nuvio.tv.ui.appearance.NotflixAccent.selected
                SettingsSingleChoiceRow(
                    title = stringResource(R.string.extras_accent_title),
                    value = accentNames.getValue(accent),
                    options = accentNames.map { (choice, name) -> SettingsPickerOption(choice, name) },
                    selectedValue = accent,
                    onOptionSelected = { com.nuvio.tv.ui.appearance.NotflixAccent.select(it) },
                    stateKey = state.context,
                    modifier = Modifier.testTag("accent-color-selector")
                )
                val uiScalePercent = com.nuvio.tv.ui.appearance.NotflixUiScale.scalePercent
                SettingsSingleChoiceRow(
                    title = stringResource(R.string.extras_ui_scale_title),
                    subtitle = stringResource(R.string.extras_ui_scale_desc),
                    value = "${uiScalePercent}%",
                    options = com.nuvio.tv.ui.appearance.NotflixUiScale.STEPS.map { pct ->
                        SettingsPickerOption(pct, "${pct}%")
                    },
                    selectedValue = uiScalePercent,
                    onOptionSelected = { com.nuvio.tv.ui.appearance.NotflixUiScale.select(it) },
                    stateKey = state.context,
                    modifier = Modifier.testTag("ui-scale-selector")
                )
            }
            SettingsSingleChoiceRow(
                settingId = "AppearanceSelectionContent.notflix_card_size_title",
                title = stringResource(R.string.notflix_card_size_title),
                subtitle = stringResource(R.string.notflix_card_size_help),
                value = stringResource(when (state.cardSize) {
                    NotflixCardSize.SMALL -> R.string.notflix_card_size_small
                    NotflixCardSize.MEDIUM -> R.string.notflix_card_size_medium
                    NotflixCardSize.LARGE -> R.string.notflix_card_size_large
                }),
                options = listOf(
                    SettingsPickerOption(NotflixCardSize.SMALL, stringResource(R.string.notflix_card_size_small)),
                    SettingsPickerOption(NotflixCardSize.MEDIUM, stringResource(R.string.notflix_card_size_medium)),
                    SettingsPickerOption(NotflixCardSize.LARGE, stringResource(R.string.notflix_card_size_large))
                ),
                selectedValue = state.cardSize,
                onOptionSelected = onSelectCardSize,
                stateKey = state.context,
                optionTestTag = { "card-size-option-${it.name.lowercase()}" },
                modifier = Modifier.testTag("card-size-${state.cardSize.name.lowercase()}")
            )
            SettingsSingleChoiceRow(
                settingId = "AppearanceSelectionContent.notflix_card_open_title",
                title = stringResource(R.string.notflix_card_open_title),
                subtitle = stringResource(R.string.notflix_card_open_help),
                value = stringResource(when (state.cardOpening) {
                    NotflixCardOpening.INSTANT -> R.string.notflix_card_open_instant
                    NotflixCardOpening.NORMAL -> R.string.notflix_card_open_normal
                    NotflixCardOpening.SLOW -> R.string.notflix_card_open_slow
                }),
                options = listOf(
                    SettingsPickerOption(NotflixCardOpening.INSTANT, stringResource(R.string.notflix_card_open_instant)),
                    SettingsPickerOption(NotflixCardOpening.NORMAL, stringResource(R.string.notflix_card_open_normal)),
                    SettingsPickerOption(NotflixCardOpening.SLOW, stringResource(R.string.notflix_card_open_slow))
                ),
                selectedValue = state.cardOpening,
                onOptionSelected = onSelectCardOpening,
                stateKey = state.context,
                optionTestTag = { "card-opening-option-${it.name.lowercase()}" },
                modifier = Modifier.testTag("card-opening-${state.cardOpening.name.lowercase()}")
            )
            // M60 PARTE 3: a choice is one row with the current value that opens a list, never a
            // row of repeated buttons or an OK cycle.
            SettingsSingleChoiceRow(
                settingId = "AppearanceSelectionContent.notflix_home_motion_title",
                title = stringResource(R.string.notflix_home_motion_title),
                subtitle = stringResource(R.string.notflix_home_motion_help),
                value = stringResource(if (state.reduceHomeMotion) R.string.notflix_home_motion_reduced
                    else R.string.notflix_home_motion_normal),
                options = listOf(
                    SettingsPickerOption(false, stringResource(R.string.notflix_home_motion_normal)),
                    SettingsPickerOption(true, stringResource(R.string.notflix_home_motion_reduced))
                ),
                selectedValue = state.reduceHomeMotion,
                onOptionSelected = onSelectHomeMotion,
                stateKey = state.context,
                optionTestTag = { if (it) "home-motion-option-reduced" else "home-motion-option-normal" },
                modifier = Modifier.testTag(if (state.reduceHomeMotion) "home-motion-reduced" else "home-motion-normal")
            )
            // 2. Top 10 rows & country
            if (com.nuvio.tv.ui.appearance.AppearancePolicy.NOTFLIX_AVAILABLE &&
                notflixSettingsRowVisible("AppearanceSelectionContent.notflix_wikipedia_enabled")
            ) {
                val showTop10 by com.nuvio.tv.ui.screens.home.NotflixCountryPreference.showTop10.collectAsState()
                SettingsToggleRow(
                    title = stringResource(R.string.extras_show_top10_title),
                    subtitle = stringResource(R.string.extras_show_top10_desc),
                    checked = showTop10,
                    onToggle = { com.nuvio.tv.ui.screens.home.NotflixCountryPreference.setShowTop10(!showTop10) },
                    modifier = Modifier.testTag("top10-show-toggle"),
                    expandSubtitleOnFocus = true
                )
                val countryOverride by com.nuvio.tv.ui.screens.home.NotflixCountryPreference.selectedCountry.collectAsState()
                val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                val autoCode = remember(locale) { com.nuvio.tv.ui.screens.home.notflixSystemCountryCode(locale) }
                val autoName = remember(autoCode, locale) {
                    com.nuvio.tv.ui.screens.home.NotflixCountryPreference.displayCountryName(autoCode, locale)
                }
                val autoLabel = stringResource(R.string.extras_country_auto, autoName)
                val countryOptions = remember(locale, autoLabel) {
                    val sorted = com.nuvio.tv.ui.screens.home.NotflixCountryPreference.SUPPORTED_COUNTRIES
                        .map { code -> code to com.nuvio.tv.ui.screens.home.NotflixCountryPreference.displayCountryName(code, locale) }
                        .sortedBy { it.second.lowercase(locale) }
                        .map { SettingsPickerOption(it.first, it.second) }
                    listOf(SettingsPickerOption(com.nuvio.tv.ui.screens.home.NotflixCountryPreference.AUTO, autoLabel)) + sorted
                }
                val currentCountryLabel = if (countryOverride.isEmpty()) autoLabel else remember(countryOverride, locale) {
                    com.nuvio.tv.ui.screens.home.NotflixCountryPreference.displayCountryName(countryOverride, locale)
                }
                SettingsSingleChoiceRow(
                    title = stringResource(R.string.extras_country_title),
                    subtitle = stringResource(R.string.extras_country_subtitle),
                    value = currentCountryLabel,
                    options = countryOptions,
                    selectedValue = countryOverride,
                    onOptionSelected = { code -> com.nuvio.tv.ui.screens.home.NotflixCountryPreference.select(code) },
                    stateKey = state.context,
                    modifier = Modifier.testTag("top10-country-selector")
                )
            }
            // 3. Intro & preview rows (projected into INTRO / TRAILERS pages)
            SettingsToggleRow(
                settingId = "AppearanceSelectionContent.notflix_intro_enabled",
                title = stringResource(R.string.notflix_intro_enabled),
                subtitle = stringResource(R.string.notflix_intro_help),
                checked = state.introEnabled,
                onToggle = { onSelectIntroEnabled(!state.introEnabled) },
                modifier = Modifier.testTag("intro-enabled"),
                expandSubtitleOnFocus = true
            )
            SettingsToggleRow(
                settingId = "AppearanceSelectionContent.notflix_intro_sound",
                title = stringResource(R.string.notflix_intro_sound),
                subtitle = null,
                checked = state.introSoundEnabled,
                onToggle = { onSelectIntroSoundEnabled(!state.introSoundEnabled) },
                modifier = Modifier.testTag("intro-sound"),
                expandSubtitleOnFocus = true
            )
            SettingsToggleRow(
                settingId = "AppearanceSelectionContent.notflix_preview_enabled",
                title = stringResource(R.string.notflix_preview_enabled),
                subtitle = stringResource(R.string.notflix_preview_help),
                checked = state.previewEnabled,
                onToggle = { onSelectPreviewEnabled(!state.previewEnabled) },
                modifier = Modifier.testTag("preview-enabled"),
                expandSubtitleOnFocus = true
            )
            SettingsToggleRow(
                settingId = "AppearanceSelectionContent.notflix_preview_sound",
                title = stringResource(R.string.notflix_preview_sound),
                subtitle = null,
                checked = state.previewSoundEnabled,
                onToggle = { onSelectPreviewSoundEnabled(!state.previewSoundEnabled) },
                modifier = Modifier.testTag("preview-sound"),
                expandSubtitleOnFocus = true
            )
            // 4. Extras (search keyboard, UI sounds, Wikipedia)
            if (com.nuvio.tv.ui.appearance.AppearancePolicy.NOTFLIX_AVAILABLE &&
                notflixSettingsRowVisible("AppearanceSelectionContent.notflix_wikipedia_enabled")
            ) {
                val systemSearchKeyboard by com.nuvio.tv.ui.screens.search.NotflixSearchModePreference.systemKeyboard.collectAsState()
                val notflixKeyboardLabel = stringResource(R.string.extras_search_keyboard_notflix)
                val systemKeyboardLabel = stringResource(R.string.extras_search_keyboard_system)
                SettingsSingleChoiceRow(
                    title = stringResource(R.string.extras_search_keyboard_title),
                    subtitle = stringResource(R.string.extras_search_keyboard_desc),
                    value = if (systemSearchKeyboard) systemKeyboardLabel else notflixKeyboardLabel,
                    options = listOf(
                        SettingsPickerOption(false, notflixKeyboardLabel),
                        SettingsPickerOption(true, systemKeyboardLabel)
                    ),
                    selectedValue = systemSearchKeyboard,
                    onOptionSelected = { com.nuvio.tv.ui.screens.search.NotflixSearchModePreference.setSystemKeyboard(it) },
                    stateKey = state.context,
                    modifier = Modifier.testTag("search-keyboard-selector")
                )
            }
            SettingsToggleRow(
                settingId = "AppearanceSelectionContent.notflix_ui_sounds",
                title = stringResource(R.string.notflix_ui_sounds),
                subtitle = null,
                checked = state.uiSoundsEnabled,
                onToggle = { onSelectUiSoundsEnabled(!state.uiSoundsEnabled) },
                modifier = Modifier.testTag("ui-sounds"),
                expandSubtitleOnFocus = true
            )
            if (com.nuvio.tv.ui.appearance.AppearancePolicy.NOTFLIX_AVAILABLE) {
                SettingsToggleRow(
                    settingId = "AppearanceSelectionContent.notflix_wikipedia_enabled",
                    title = stringResource(R.string.notflix_wikipedia_enabled),
                    subtitle = stringResource(R.string.notflix_wikipedia_help),
                    checked = state.wikipediaEnabled,
                    onToggle = { onSelectWikipediaEnabled(!state.wikipediaEnabled) },
                    modifier = Modifier.testTag("wikipedia-enabled"),
                    expandSubtitleOnFocus = true
                )
                if (notflixSettingsRowVisible("AppearanceSelectionContent.collection")) collectionCreator()
            }
        }
        if (state.saveFailed) Text(stringResource(R.string.notflix_save_failed))
    }

}

// M29 (promoter): the chosen option must be visible, not only announced: accent border plus a check mark.
@Composable
private fun selectedBorder(selected: Boolean) = ButtonDefaults.border(
    border = if (selected) Border(BorderStroke(2.dp, if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active)
        com.nuvio.tv.ui.appearance.NotflixVisualSpec.Accent else NuvioTheme.colors.Secondary)) else Border.None)

private fun marked(selected: Boolean, label: String) = if (selected) "✓ $label" else label
