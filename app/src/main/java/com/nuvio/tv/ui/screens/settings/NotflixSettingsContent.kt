package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.ui.appearance.NotflixWhatsNewPanel
import com.nuvio.tv.ui.screens.party.PartySettingsContent

@Composable
internal fun NotflixSettingsContent(
    primary: Boolean, partyAvailable: Boolean, integrationsRequested: Boolean, showBuiltInHeader: Boolean,
    experienceModeViewModel: ExperienceModeSettingsViewModel,
    onNavigateToAddons: () -> Unit, onNavigateToPlugins: () -> Unit,
    onNavigateToAuthQrSignIn: () -> Unit, onNavigateToManageProfiles: () -> Unit,
    onNavigateToSupportersContributors: () -> Unit, onNavigateToLicensesAttributions: () -> Unit,
    onNavigateToWatchParty: () -> Unit, onNavigateToCollections: () -> Unit, collectionManagerReturnToken: Int,
    requestedDestination: String? = null
) {
    var showNews by rememberSaveable { mutableStateOf(false) }
    var handledNewsDestination by rememberSaveable { mutableStateOf<String?>(null) }
    val news = stringArrayResource(R.array.notflix_whats_new_items).toList()
    val workspaceDestination = if (notflixRequestedPage(requestedDestination) == NotflixSettingsPage.NEWS)
        requestedDestination?.substringBefore(':') + ":" + NotflixSettingsPage.SYSTEM.name else requestedDestination
    LaunchedEffect(requestedDestination) {
        if (notflixRequestedPage(requestedDestination) == NotflixSettingsPage.NEWS && requestedDestination != handledNewsDestination) {
            handledNewsDestination = requestedDestination
            showNews = true
        }
    }
    NotflixSettingsWorkspace(primary, partyAvailable, integrationsRequested, showBuiltInHeader,
        requestedDestination = workspaceDestination) { page, navigate ->
        val open: (NotflixSettingsPage) -> Unit = { target ->
            when (target) {
                NotflixSettingsPage.ADDON_MANAGER -> onNavigateToAddons()
                NotflixSettingsPage.COLLECTION_MANAGER -> onNavigateToCollections()
                NotflixSettingsPage.PLUGINS -> if (AppFeaturePolicy.pluginsEnabled) onNavigateToPlugins()
                NotflixSettingsPage.NEWS -> showNews = true
                else -> navigate(target)
            }
        }
        val children = notflixSettingsChildren(page, primary, partyAvailable, BuildConfig.IS_DEBUG_BUILD, AppFeaturePolicy.pluginsEnabled)
        when (page) {
            NotflixSettingsPage.PROFILE,
            NotflixSettingsPage.HOME_ADVANCED, NotflixSettingsPage.ACCOUNTS,
            NotflixSettingsPage.ACCOUNTS_ADVANCED, NotflixSettingsPage.MDBLIST,
            NotflixSettingsPage.TMDB, NotflixSettingsPage.SKIP_SERVICES, NotflixSettingsPage.ADDONS ->
                NotflixSettingsLinks(children, open)

            NotflixSettingsPage.PLAYBACK_ADVANCED -> {
                val playback: PlaybackSettingsViewModel = hiltViewModel()
                val settings by playback.playerSettings.collectAsStateWithLifecycle(initialValue = com.nuvio.tv.data.local.PlayerSettings())
                NotflixSettingsLinks(children.filter { it != NotflixSettingsPage.BUFFER || settings.usesExoPlayerEngine }, open)
            }

            NotflixSettingsPage.ACCOUNT -> AccountSettingsInline(onNavigateToAuthQrSignIn, null)
            NotflixSettingsPage.PROFILES -> ProfileSettingsContent(onManageProfiles = onNavigateToManageProfiles, initialFocusRequester = null)
            NotflixSettingsPage.PARTY -> PartySettingsContent(onOpen = onNavigateToWatchParty, initialFocusRequester = null)

            NotflixSettingsPage.PLAYBACK, NotflixSettingsPage.SUBTITLES, NotflixSettingsPage.SUBTITLES_ADVANCED,
            NotflixSettingsPage.SOURCES, NotflixSettingsPage.AUDIO, NotflixSettingsPage.VIDEO,
            NotflixSettingsPage.BUFFER, NotflixSettingsPage.P2P, NotflixSettingsPage.PLAYER,
            NotflixSettingsPage.INTERFACE, NotflixSettingsPage.UP_NEXT, NotflixSettingsPage.SKIP ->
                PlaybackSettingsContent(notflixPage = page, openNotflixPage = open)

            NotflixSettingsPage.HOME -> NotflixProjection(page) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeSettingsContent(embedded = true, collectionManagerReturnToken = collectionManagerReturnToken,
                        onNavigateToCollections = onNavigateToCollections)
                    children.forEach { NotflixSettingsLink(it, open) }
                }
            }
            NotflixSettingsPage.INTRO, NotflixSettingsPage.THEME, NotflixSettingsPage.HOME_COLLECTION -> NotflixProjection(page) {
                ThemeSettingsContent(collectionManagerReturnToken = collectionManagerReturnToken,
                    onNavigateToCollections = onNavigateToCollections)
            }
            NotflixSettingsPage.TRAILERS -> NotflixProjection(page) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeSettingsContent(embedded = true)
                    LayoutSettingsContent(sectionOverride = LayoutSection.DETAIL_PAGE, embeddedDetailRows = true)
                }
            }
            NotflixSettingsPage.CONTINUE_WATCHING -> NotflixProjection(page) { LayoutSettingsContent(sectionOverride = LayoutSection.CONTINUE_WATCHING) }
            NotflixSettingsPage.HOME_CONTENT -> NotflixProjection(page) { LayoutSettingsContent(sectionOverride = LayoutSection.HOME_CONTENT) }
            NotflixSettingsPage.DETAIL -> NotflixProjection(page) { LayoutSettingsContent(sectionOverride = LayoutSection.DETAIL_PAGE) }
            NotflixSettingsPage.CUSTOM_POSTERS -> NotflixProjection(page) { LayoutSettingsContent(sectionOverride = LayoutSection.CUSTOM_POSTERS) }
            NotflixSettingsPage.STREAMS -> NotflixProjection(page) { LayoutSettingsContent(sectionOverride = LayoutSection.STREAMS) }

            NotflixSettingsPage.TRAKT, NotflixSettingsPage.SIMKL, NotflixSettingsPage.MDBLIST_ACCOUNT,
            NotflixSettingsPage.LIBRARY_PROGRESS -> NotflixProjection(page) { TrackingSettingsContent() }
            NotflixSettingsPage.MDBLIST_RATINGS -> MDBListSettingsContent()
            NotflixSettingsPage.TMDB_OPTIONS -> TmdbSettingsContent()
            NotflixSettingsPage.TMDB_KEY, NotflixSettingsPage.INTRO_DB, NotflixSettingsPage.PROVIDER_PHONE ->
                NotflixProjection(page) { NotflixProviderSettingsContent() }
            NotflixSettingsPage.ANIME_SKIP -> AnimeSkipSettingsContent()
            NotflixSettingsPage.DEBRID -> DebridSettingsContent()
            NotflixSettingsPage.ADDON_SERVICES -> ContentDiscoverySettingsContent(
                onNavigateToAddons, onNavigateToPlugins, AppFeaturePolicy.pluginsEnabled, null, startAtServers = true)

            NotflixSettingsPage.SYSTEM -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                children.forEach { child ->
                    if (child == NotflixSettingsPage.EXPERIENCE) NotflixExperienceModeRow(experienceModeViewModel)
                    else if (child != NotflixSettingsPage.UPDATES || AppFeaturePolicy.inAppUpdatesEnabled) NotflixSettingsLink(child, open)
                }
            }
            NotflixSettingsPage.EXPERIENCE -> NotflixExperienceModeRow(experienceModeViewModel)
            NotflixSettingsPage.UPDATES -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) { UpdateChannelSettings(initialFocusRequester = null) }
            NotflixSettingsPage.ABOUT -> AboutSettingsContent(onNavigateToSupportersContributors,
                onNavigateToLicensesAttributions, onOpenWhatsNew = { showNews = true })
            NotflixSettingsPage.SYSTEM_ADVANCED -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { AdvancedSettingsContent(experienceModeViewModel = experienceModeViewModel) }
                children.forEach { NotflixSettingsLink(it, open) }
            }
            NotflixSettingsPage.DEBUG -> if (BuildConfig.IS_DEBUG_BUILD) DebugSettingsContent()
            NotflixSettingsPage.NEWS, NotflixSettingsPage.ADDON_MANAGER,
            NotflixSettingsPage.COLLECTION_MANAGER, NotflixSettingsPage.PLUGINS -> Unit
        }
    }
    if (showNews && news.isNotEmpty()) NotflixWhatsNewPanel(BuildConfig.VERSION_NAME, news, onDismiss = { showNews = false })
}

@Composable
internal fun NotflixProjection(page: NotflixSettingsPage, content: @Composable () -> Unit) {
    val ids = remember(page) { NotflixSettingsCatalog.ids(page) }
    CompositionLocalProvider(LocalNotflixSettingsRows provides NotflixSettingsRowFilter(ids, hideLabels = true), content = content)
}

@Composable
private fun NotflixExperienceModeRow(viewModel: ExperienceModeSettingsViewModel) {
    val mode by viewModel.mode.collectAsStateWithLifecycle(initialValue = null)
    var pending by rememberSaveable { mutableStateOf<ExperienceMode?>(null) }
    val essential = stringResource(R.string.experience_mode_essential)
    val advanced = stringResource(R.string.experience_mode_advanced)
    SettingsSingleChoiceRow(title = stringResource(R.string.settings_experience),
        settingId = "experience_mode", selectedValue = mode,
        value = when (mode) { ExperienceMode.ESSENTIAL -> essential; ExperienceMode.ADVANCED -> advanced; null -> stringResource(R.string.sub_not_set) },
        options = listOf(SettingsPickerOption<ExperienceMode?>(ExperienceMode.ESSENTIAL, essential), SettingsPickerOption<ExperienceMode?>(ExperienceMode.ADVANCED, advanced)),
        onOptionSelected = { target -> if (target != mode) pending = target })
    pending?.let { target -> ExperienceModeConfirmationDialog(target,
        onConfirm = { viewModel.setMode(target) }, onDismiss = { pending = null }) }
}
