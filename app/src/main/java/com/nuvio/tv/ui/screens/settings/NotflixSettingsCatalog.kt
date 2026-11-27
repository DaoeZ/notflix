package com.nuvio.tv.ui.screens.settings

import androidx.annotation.StringRes
import com.nuvio.tv.R

internal enum class NotflixSettingsSection(@param:StringRes val title: Int) {
    PROFILE(R.string.notflix_settings_profile_account),
    PLAYBACK(R.string.settings_playback),
    SUBTITLES(R.string.subtitle_dialog_title),
    HOME(R.string.notflix_settings_home),
    ACCOUNTS(R.string.notflix_settings_connected_accounts),
    ADDONS(R.string.addon_title),
    SYSTEM(R.string.notflix_settings_system)
}

/** Presentation destinations inside the existing Settings route, not additional NavHost screens. */
internal enum class NotflixSettingsPage(val section: NotflixSettingsSection, @param:StringRes val title: Int) {
    PROFILE(NotflixSettingsSection.PROFILE, R.string.notflix_settings_profile_account),
    ACCOUNT(NotflixSettingsSection.PROFILE, R.string.settings_account),
    PROFILES(NotflixSettingsSection.PROFILE, R.string.settings_profiles),
    PARTY(NotflixSettingsSection.PROFILE, R.string.party_title),
    PLAYBACK(NotflixSettingsSection.PLAYBACK, R.string.settings_playback),
    PLAYBACK_ADVANCED(NotflixSettingsSection.PLAYBACK, R.string.notflix_settings_advanced_options),
    SOURCES(NotflixSettingsSection.PLAYBACK, R.string.playback_section_stream_selection),
    AUDIO(NotflixSettingsSection.PLAYBACK, R.string.audio_section),
    VIDEO(NotflixSettingsSection.PLAYBACK, R.string.video_section),
    BUFFER(NotflixSettingsSection.PLAYBACK, R.string.playback_section_buffer_network),
    P2P(NotflixSettingsSection.PLAYBACK, R.string.settings_p2p_title),
    PLAYER(NotflixSettingsSection.PLAYBACK, R.string.playback_section_player_title),
    INTERFACE(NotflixSettingsSection.PLAYBACK, R.string.playback_section_interface),
    UP_NEXT(NotflixSettingsSection.PLAYBACK, R.string.playback_section_up_next),
    SKIP(NotflixSettingsSection.PLAYBACK, R.string.playback_section_skip),
    SUBTITLES(NotflixSettingsSection.SUBTITLES, R.string.subtitle_dialog_title),
    SUBTITLES_ADVANCED(NotflixSettingsSection.SUBTITLES, R.string.notflix_settings_advanced_options),
    HOME(NotflixSettingsSection.HOME, R.string.notflix_settings_home),
    INTRO(NotflixSettingsSection.HOME, R.string.notflix_intro_enabled),
    CONTINUE_WATCHING(NotflixSettingsSection.HOME, R.string.layout_section_continue_watching),
    TRAILERS(NotflixSettingsSection.HOME, R.string.notflix_settings_trailers),
    HOME_ADVANCED(NotflixSettingsSection.HOME, R.string.notflix_settings_advanced_options),
    THEME(NotflixSettingsSection.HOME, R.string.appearance_title),
    HOME_CONTENT(NotflixSettingsSection.HOME, R.string.layout_section_content),
    DETAIL(NotflixSettingsSection.HOME, R.string.layout_section_detail),
    CUSTOM_POSTERS(NotflixSettingsSection.HOME, R.string.layout_custom_poster_title),
    STREAMS(NotflixSettingsSection.PLAYBACK, R.string.layout_section_streams),
    ACCOUNTS(NotflixSettingsSection.ACCOUNTS, R.string.notflix_settings_connected_accounts),
    TRAKT(NotflixSettingsSection.ACCOUNTS, R.string.trakt_name),
    SIMKL(NotflixSettingsSection.ACCOUNTS, R.string.simkl_name),
    MDBLIST(NotflixSettingsSection.ACCOUNTS, R.string.mdblist_name),
    MDBLIST_ACCOUNT(NotflixSettingsSection.ACCOUNTS, R.string.settings_account),
    MDBLIST_RATINGS(NotflixSettingsSection.ACCOUNTS, R.string.notflix_settings_ratings),
    TMDB(NotflixSettingsSection.ACCOUNTS, R.string.tmdb_title),
    TMDB_OPTIONS(NotflixSettingsSection.ACCOUNTS, R.string.notflix_settings_metadata),
    TMDB_KEY(NotflixSettingsSection.ACCOUNTS, R.string.notflix_provider_tmdb_title),
    SKIP_SERVICES(NotflixSettingsSection.ACCOUNTS, R.string.playback_section_skip),
    ANIME_SKIP(NotflixSettingsSection.ACCOUNTS, R.string.notflix_settings_anime_skip),
    INTRO_DB(NotflixSettingsSection.ACCOUNTS, R.string.notflix_provider_introdb_title),
    DEBRID(NotflixSettingsSection.ACCOUNTS, R.string.debrid_title),
    ACCOUNTS_ADVANCED(NotflixSettingsSection.ACCOUNTS, R.string.notflix_settings_advanced_options),
    LIBRARY_PROGRESS(NotflixSettingsSection.ACCOUNTS, R.string.tracking_sources_title),
    PROVIDER_PHONE(NotflixSettingsSection.ACCOUNTS, R.string.notflix_provider_phone_title),
    ADDONS(NotflixSettingsSection.ADDONS, R.string.addon_title),
    ADDON_MANAGER(NotflixSettingsSection.ADDONS, R.string.addon_title),
    COLLECTION_MANAGER(NotflixSettingsSection.HOME, R.string.notflix_collection_manage),
    PLUGINS(NotflixSettingsSection.ADDONS, R.string.plugin_title),
    ADDON_SERVICES(NotflixSettingsSection.ADDONS, R.string.settings_media_servers),
    HOME_COLLECTION(NotflixSettingsSection.HOME, R.string.notflix_settings_home_collection),
    SYSTEM(NotflixSettingsSection.SYSTEM, R.string.notflix_settings_system),
    UPDATES(NotflixSettingsSection.SYSTEM, R.string.notflix_settings_updates),
    NEWS(NotflixSettingsSection.SYSTEM, R.string.notflix_whats_new_about_title),
    EXPERIENCE(NotflixSettingsSection.SYSTEM, R.string.settings_experience),
    ABOUT(NotflixSettingsSection.SYSTEM, R.string.about_title),
    SYSTEM_ADVANCED(NotflixSettingsSection.SYSTEM, R.string.notflix_settings_advanced_options),
    DEBUG(NotflixSettingsSection.SYSTEM, R.string.settings_debug)
}

internal fun notflixSettingsChildren(page: NotflixSettingsPage, primary: Boolean = true,
    party: Boolean = true, debug: Boolean = false, plugins: Boolean = true): List<NotflixSettingsPage> {
    val children = when (page) {
        NotflixSettingsPage.PROFILE -> listOf(NotflixSettingsPage.ACCOUNT, NotflixSettingsPage.PROFILES, NotflixSettingsPage.PARTY)
        NotflixSettingsPage.PLAYBACK -> listOf(NotflixSettingsPage.SKIP, NotflixSettingsPage.PLAYBACK_ADVANCED)
        NotflixSettingsPage.PLAYBACK_ADVANCED -> listOf(NotflixSettingsPage.SOURCES, NotflixSettingsPage.AUDIO,
            NotflixSettingsPage.VIDEO, NotflixSettingsPage.BUFFER, NotflixSettingsPage.P2P,
            NotflixSettingsPage.PLAYER, NotflixSettingsPage.UP_NEXT, NotflixSettingsPage.STREAMS)
        NotflixSettingsPage.PLAYER -> listOf(NotflixSettingsPage.INTERFACE)
        NotflixSettingsPage.SUBTITLES -> listOf(NotflixSettingsPage.SUBTITLES_ADVANCED)
        NotflixSettingsPage.HOME -> listOf(NotflixSettingsPage.COLLECTION_MANAGER, NotflixSettingsPage.HOME_COLLECTION,
            NotflixSettingsPage.CONTINUE_WATCHING, NotflixSettingsPage.TRAILERS,
            NotflixSettingsPage.INTRO, NotflixSettingsPage.HOME_ADVANCED)
        NotflixSettingsPage.HOME_ADVANCED -> listOf(NotflixSettingsPage.THEME, NotflixSettingsPage.HOME_CONTENT,
            NotflixSettingsPage.DETAIL, NotflixSettingsPage.CUSTOM_POSTERS)
        NotflixSettingsPage.ACCOUNTS -> listOf(NotflixSettingsPage.DEBRID, NotflixSettingsPage.TRAKT,
            NotflixSettingsPage.SIMKL, NotflixSettingsPage.MDBLIST, NotflixSettingsPage.TMDB,
            NotflixSettingsPage.SKIP_SERVICES, NotflixSettingsPage.ACCOUNTS_ADVANCED)
        NotflixSettingsPage.MDBLIST -> listOf(NotflixSettingsPage.MDBLIST_ACCOUNT, NotflixSettingsPage.MDBLIST_RATINGS)
        NotflixSettingsPage.TMDB -> listOf(NotflixSettingsPage.TMDB_OPTIONS)
        NotflixSettingsPage.SKIP_SERVICES -> listOf(NotflixSettingsPage.ANIME_SKIP)
        // Notflix ships its own TMDB/Trakt keys and IntroDB works keyless: own keys are an advanced option.
        NotflixSettingsPage.ACCOUNTS_ADVANCED -> listOf(NotflixSettingsPage.LIBRARY_PROGRESS, NotflixSettingsPage.TMDB_KEY,
            NotflixSettingsPage.INTRO_DB, NotflixSettingsPage.PROVIDER_PHONE)
        NotflixSettingsPage.ADDONS -> listOf(NotflixSettingsPage.ADDON_MANAGER, NotflixSettingsPage.ADDON_SERVICES) +
            if (plugins) listOf(NotflixSettingsPage.PLUGINS) else emptyList()
        NotflixSettingsPage.SYSTEM -> listOf(NotflixSettingsPage.UPDATES, NotflixSettingsPage.NEWS,
            NotflixSettingsPage.EXPERIENCE, NotflixSettingsPage.ABOUT, NotflixSettingsPage.SYSTEM_ADVANCED)
        NotflixSettingsPage.SYSTEM_ADVANCED -> if (debug) listOf(NotflixSettingsPage.DEBUG) else emptyList()
        else -> emptyList()
    }
    return children.filter { notflixSettingsPageAllowed(it, primary, party) }
}

internal fun NotflixSettingsSection.root(): NotflixSettingsPage = NotflixSettingsPage.valueOf(name)

internal fun notflixSettingsOpenPath(path: List<String>, destination: NotflixSettingsPage,
    root: NotflixSettingsPage): List<String> {
    if (destination == root) return emptyList()
    val ancestor = path.indexOf(destination.name)
    return if (ancestor >= 0) path.take(ancestor + 1) else path + destination.name
}

internal fun visibleNotflixSettingsSections(primary: Boolean, partyAvailable: Boolean): List<NotflixSettingsSection> =
    NotflixSettingsSection.entries.filter { it != NotflixSettingsSection.PROFILE || primary || partyAvailable }

/** Legacy entry is kept entry-scoped by NotflixSettingsEntry/SavedStateHandle. */
internal fun notflixRequestedPage(request: String?): NotflixSettingsPage? =
    request?.substringAfterLast(':')?.let { name -> NotflixSettingsPage.entries.firstOrNull { it.name == name } }

internal fun initialNotflixSettingsPath(integrationsRequested: Boolean, destination: String? = null): List<NotflixSettingsPage> {
    val target = notflixRequestedPage(destination) ?: if (integrationsRequested) NotflixSettingsPage.TMDB_KEY else return emptyList()
    val queue = java.util.ArrayDeque<List<NotflixSettingsPage>>()
    queue.add(listOf(target.section.root()))
    val visited = mutableSetOf<NotflixSettingsPage>()
    while (queue.isNotEmpty()) {
        val path = queue.removeFirst()
        val last = path.last()
        if (last == target) return path
        if (visited.add(last)) notflixSettingsChildren(last, debug = true).forEach { queue.add(path + it) }
    }
    return listOf(target.section.root())
}

internal data class NotflixSettingLocation(val id: String, val page: NotflixSettingsPage?,
    val hiddenReason: String? = null, val nested: Boolean = false)

internal object NotflixSettingsCatalog {
    val playbackBasic = setOf("PlaybackAudioSettings.audio_preferred_lang",
        "PlaybackAutoPlaySettings.autoplay_stream_selection", "PlaybackAutoPlaySettings.autoplay_priority_list_title",
        "PlaybackAutoPlaySettings.autoplay_next_episode", "PlaybackPlayerSettings.playback_default_player")
    val subtitleBasic = setOf("PlaybackSubtitleSettings.sub_preferred_lang", "PlaybackSubtitleSettings.sub_secondary_lang",
        "PlaybackSubtitleSettings.sub_use_forced_subtitles", "PlaybackSubtitleSettings.sub_strip_sdh",
        "PlaybackSubtitleSettings.sub_size", "PlaybackSubtitleSettings.sub_vertical_offset",
        "PlaybackSubtitleSettings.sub_text_color", "PlaybackSubtitleSettings.sub_outline")
    val homeBasic = setOf("AppearanceSelectionContent.notflix_home_motion_title", "AppearanceSelectionContent.notflix_card_open_title",
        "AppearanceSelectionContent.notflix_card_size_title", "AppearanceSelectionContent.notflix_ui_sounds",
        "AppearanceSelectionContent.notflix_wikipedia_enabled")

    val locations: List<NotflixSettingLocation> = notflixSettingsInventory()
    fun ids(page: NotflixSettingsPage): Set<String> = locations
        .filter { it.page == page && it.hiddenReason == null && !it.nested }.mapTo(linkedSetOf()) { it.id }
}

internal fun notflixSettingsPageAllowed(page: NotflixSettingsPage, primary: Boolean, party: Boolean): Boolean = when (page) {
    NotflixSettingsPage.ACCOUNT, NotflixSettingsPage.PROFILES -> primary
    NotflixSettingsPage.PARTY -> party
    else -> true
}
