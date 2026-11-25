@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames

private val SettingsFocusMemorySaver = mapSaver<MutableMap<String, String>>(
    save = { it.mapValues { entry -> entry.value } },
    restore = { it.mapValues { entry -> entry.value as String }.toMutableMap() }
)

/** Dialog drafts, focus and pending UI coroutines belong to one existing profile. */
@Composable
internal fun NotflixSettingsProfileScope(profileId: Int, content: @Composable () -> Unit) {
    key(profileId) { content() }
}

/** M72 presentation shell inside SettingsScreen. Content remains owned by the original screens/VMs. */
@Composable
internal fun NotflixSettingsWorkspace(
    primary: Boolean,
    partyAvailable: Boolean,
    integrationsRequested: Boolean,
    showBuiltInHeader: Boolean,
    requestedDestination: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable (NotflixSettingsPage, (NotflixSettingsPage) -> Unit) -> Unit
) {
    val sections = remember(primary, partyAvailable) { visibleNotflixSettingsSections(primary, partyAvailable) }
    val entry = remember { initialNotflixSettingsPath(integrationsRequested, requestedDestination) }
    var entryHandled by rememberSaveable { mutableStateOf(integrationsRequested) }
    var handledDestination by rememberSaveable { mutableStateOf(requestedDestination) }
    var sectionName by rememberSaveable { mutableStateOf((entry.firstOrNull()?.section ?: sections.first()).name) }
    var path by rememberSaveable { mutableStateOf(entry.drop(1).map { it.name }) }
    var entered by rememberSaveable { mutableStateOf(entry.isNotEmpty()) }
    val memory = rememberSaveable(saver = SettingsFocusMemorySaver) { mutableMapOf<String, String>() }
    val holder = rememberSaveableStateHolder()
    val menuRequesters = remember { NotflixSettingsSection.entries.associateWith { FocusRequester() } }
    val selectedSection = NotflixSettingsSection.entries.firstOrNull { it.name == sectionName && it in sections } ?: sections.first()
    val requestedPage = path.lastOrNull()?.let { name -> NotflixSettingsPage.entries.firstOrNull { it.name == name } } ?: selectedSection.root()
    val page = requestedPage.takeIf { notflixSettingsPageAllowed(it, primary, partyAvailable) } ?: selectedSection.root()
    val route = selectedSection.name + ":" + page.name
    val host = remember(route) {
        NotflixSettingsFocus { id -> memory[route] = id }.apply { last = memory[route] }
    }
    val contentRequester = remember(route) { FocusRequester() }
    val open: (NotflixSettingsPage) -> Unit = { destination ->
        if (notflixSettingsPageAllowed(destination, primary, partyAvailable)) {
            path = notflixSettingsOpenPath(path, destination, selectedSection.root())
            entered = true
        }
    }
    LaunchedEffect(integrationsRequested) {
        if (integrationsRequested && !entryHandled) {
            entryHandled = true
            sectionName = NotflixSettingsSection.ACCOUNTS.name
            path = initialNotflixSettingsPath(true).drop(1).map { it.name }
            entered = true
        }
    }
    LaunchedEffect(requestedDestination) {
        if (requestedDestination != null && requestedDestination != handledDestination) {
            handledDestination = requestedDestination
            val destination = initialNotflixSettingsPath(false, requestedDestination)
            if (destination.isNotEmpty()) {
                sectionName = destination.first().section.name
                path = destination.drop(1).map { it.name }
                entered = true
            }
        }
    }
    LaunchedEffect(primary, partyAvailable, requestedPage, selectedSection) {
        if (sectionName != selectedSection.name || page != requestedPage) {
            sectionName = selectedSection.name
            path = emptyList()
        }
    }
    BackHandler(enabled = entered) {
        if (path.isNotEmpty()) {
            val parentPath = path.dropLast(1)
            val parent = parentPath.lastOrNull() ?: selectedSection.root().name
            // A direct entry has virtual ancestors which have never received focus themselves.
            memory["${selectedSection.name}:$parent"] = if (page == NotflixSettingsPage.EXPERIENCE)
                "experience_mode" else "navigate.${page.name}"
            path = parentPath
        }
        else { entered = false; menuRequesters[selectedSection]?.requestFocus() }
    }
    LaunchedEffect(route, entered) {
        if (!entered) {
            menuRequesters[selectedSection]?.requestFocusAfterFrames(2)
        } else {
            // Registration and lazy-list restoration happen after composition; cancel on route exit.
            repeat(60) {
                withFrameNanos { }
                if (runCatching { (host.target() ?: contentRequester).requestFocus() }.getOrDefault(false))
                    return@LaunchedEffect
            }
            path = emptyList()
            entered = false
            menuRequesters[selectedSection]?.requestFocus()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Spec.Background)
        .padding(start = 26.dp, end = 26.dp, top = if (showBuiltInHeader) 16.dp else 68.dp, bottom = 16.dp)
        .testTag("notflix-settings-workspace")) {
        val compact = maxHeight < 400.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)) {
            Column(Modifier.width(if (compact) 184.dp else 218.dp).fillMaxHeight().focusGroup()
                .testTag("notflix-settings-menu"), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
                if (!compact) Text(stringResource(R.string.nav_settings), color = Spec.Text,
                    fontFamily = Spec.BrandFont, fontSize = 24.sp, modifier = Modifier.padding(bottom = 10.dp))
                sections.forEach { section -> key(section) {
                    NotflixSettingsMenuRow(section, selectedSection == section, compact,
                        Modifier.weight(1f).heightIn(max = 52.dp).focusRequester(menuRequesters.getValue(section)),
                        onFocused = {},
                        onEnter = {
                            if (selectedSection != section) path = emptyList()
                            sectionName = section.name
                            entered = true
                        })
                } }
            }
            Column(Modifier.weight(1f).fillMaxHeight().testTag("notflix-settings-content"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Every section has an «Advanced options» page; name the section so they read apart.
                if (page.title == R.string.notflix_settings_advanced_options) {
                    Text(stringResource(page.section.title), color = Spec.Secondary, fontFamily = Spec.BodyFont,
                        fontSize = 15.sp, maxLines = 1)
                }
                Text(stringResource(page.title), color = Spec.Text, fontFamily = Spec.BrandFont,
                    fontSize = if (compact) 23.sp else 28.sp, maxLines = 2,
                    modifier = Modifier.testTag("notflix-settings-page-title"))
                Box(Modifier.weight(1f).fillMaxWidth().focusRequester(contentRequester).focusProperties {
                    onExit = {
                        if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down)
                            cancelFocusChange()
                    }
                }.focusRestorer().focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            entered = false
                            menuRequesters[selectedSection]?.requestFocus()
                            true
                        } else false
                    }) {
                    holder.SaveableStateProvider(route) {
                        CompositionLocalProvider(LocalNotflixSettingsFrame provides true,
                            LocalNotflixSettingsFocus provides host) { content(page, open) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotflixSettingsMenuRow(section: NotflixSettingsSection, selectedSection: Boolean, compact: Boolean,
    modifier: Modifier, onFocused: () -> Unit, onEnter: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                if (event.nativeKeyEvent.repeatCount == 0) onEnter()
                true
            } else false
        }.semantics { selected = selectedSection }
        .background(if (focused) Spec.Text else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(role = Role.Tab, onClick = onEnter)
        .padding(horizontal = 12.dp).testTag("notflix-settings-section-${section.name.lowercase()}"),
        contentAlignment = Alignment.CenterStart) {
        Text(stringResource(section.title), fontFamily = Spec.BodyFont,
            color = if (focused) Spec.Background else if (selectedSection) Spec.Text else Spec.Secondary,
            fontSize = if (compact) 15.sp else 18.sp, lineHeight = if (compact) 17.sp else 21.sp, maxLines = 2)
    }
}

@Composable
internal fun NotflixSettingsLinks(pages: List<NotflixSettingsPage>, open: (NotflixSettingsPage) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pages.forEach { NotflixSettingsLink(it, open) }
    }
}

@Composable
internal fun NotflixSettingsLink(page: NotflixSettingsPage, open: (NotflixSettingsPage) -> Unit) {
    SettingsActionRow(title = stringResource(page.title), subtitle = null,
        settingId = "navigate.${page.name}", modifier = Modifier.testTag("notflix-settings-open-${page.name.lowercase()}"),
        onClick = { open(page) })
}
