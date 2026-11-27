package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class NotflixSettingsCatalogTest {
    @Test fun returningToAnAncestorDoesNotCreateARepeatedPageOrABackLoop() {
        val advanced = NotflixSettingsPage.PLAYBACK_ADVANCED
        val buffer = NotflixSettingsPage.BUFFER
        val root = NotflixSettingsPage.PLAYBACK
        assertEquals(listOf(advanced.name), notflixSettingsOpenPath(listOf(advanced.name, buffer.name), advanced, root))
        assertEquals(emptyList<String>(), notflixSettingsOpenPath(listOf(advanced.name, buffer.name), root, root))
        assertEquals(listOf(advanced.name, buffer.name), notflixSettingsOpenPath(listOf(advanced.name), buffer, root))
    }
    @Test fun everyBaselineSettingHasOneDestinationOrAnExplicitReason() {
        val baseline = requireNotNull(javaClass.getResourceAsStream("/notflix-m72-settings-inventory.tsv"))
            .bufferedReader().useLines { lines -> lines.map { it.substringBefore('\t') }.toList() }
        val locations = NotflixSettingsCatalog.locations
        assertTrue(baseline.size >= 252)
        assertEquals(baseline.size, baseline.toSet().size)
        assertEquals(locations.size, locations.map { it.id }.toSet().size)
        assertEquals(baseline.toSet(), locations.map { it.id }.toSet())
        locations.forEach { row ->
            assertTrue(row.id, row.page != null || !row.hiddenReason.isNullOrBlank())
            if (row.hiddenReason != null) assertTrue(row.hiddenReason.length > 30)
        }
        val reachable = NotflixSettingsSection.entries.flatMap { section ->
            val queue = java.util.ArrayDeque<NotflixSettingsPage>()
            val found = linkedSetOf<NotflixSettingsPage>()
            queue.add(section.root())
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (found.add(next)) queue.addAll(notflixSettingsChildren(next, debug = true))
            }
            found
        }.toSet()
        locations.filter { it.hiddenReason == null }.forEach { assertTrue(it.id, it.page in reachable) }
    }

    @Test fun sevenSectionsAndBasicPagesFitTheAgreedLimits() {
        assertEquals(listOf("PROFILE", "PLAYBACK", "SUBTITLES", "HOME", "ACCOUNTS", "ADDONS", "SYSTEM"),
            visibleNotflixSettingsSections(true, true).map { it.name })
        val extras = mapOf(NotflixSettingsPage.PLAYBACK to 1) // existing M71 editor row
        NotflixSettingsSection.entries.forEach { section ->
            val root = section.root()
            val links = notflixSettingsChildren(root)
            val advanced = links.indexOfFirst { it.name.endsWith("_ADVANCED") }
            if (advanced >= 0) assertEquals(links.lastIndex, advanced)
            val basic = when (root) {
                NotflixSettingsPage.PLAYBACK -> NotflixSettingsCatalog.playbackBasic.size
                NotflixSettingsPage.SUBTITLES -> NotflixSettingsCatalog.subtitleBasic.size
                NotflixSettingsPage.HOME -> NotflixSettingsCatalog.homeBasic.size
                else -> 0
            }
            val limit = if (root == NotflixSettingsPage.HOME) 10 else 8
            assertTrue(root.name, basic + (extras[root] ?: 0) + links.count { !it.name.endsWith("_ADVANCED") } <= limit)
        }
        assertEquals(NotflixSettingsCatalog.subtitleBasic, NotflixSettingsCatalog.ids(NotflixSettingsPage.SUBTITLES))
        assertTrue(NotflixSettingsCatalog.ids(NotflixSettingsPage.SUBTITLES_ADVANCED)
            .intersect(NotflixSettingsCatalog.subtitleBasic).isEmpty())
    }

    @Test fun allTrailerRowsShareOnePageAndTheInverseMutedControlIsMerged() {
        val trailerIds = listOf("AppearanceSelectionContent.notflix_preview_enabled", "AppearanceSelectionContent.notflix_preview_sound",
            "LayoutDetailSettings.audio_autoplay_trailers", "LayoutDetailSettings.audio_trailer_delay",
            "LayoutDetailSettings.layout_trailer_4k", "LayoutDetailSettings.layout_trailer_button",
            "LayoutDetailSettings.layout_trailer_play_in_background", "LayoutDetailSettings.layout_trailer_pause_on_scroll")
        trailerIds.forEach { assertTrue(it, it in NotflixSettingsCatalog.ids(NotflixSettingsPage.TRAILERS)) }
        val inverse = NotflixSettingsCatalog.locations.single { it.id == "LayoutCardSettings.layout_trailer_muted" }
        assertEquals(NotflixSettingsPage.TRAILERS, inverse.page)
        assertNotNull(inverse.hiddenReason)
        assertFalse(inverse.id in NotflixSettingsCatalog.ids(NotflixSettingsPage.TRAILERS))
    }

    @Test fun kidsAndLegacyDestinationsResolveToTheirNewSectionWithoutChangingTheRequest() {
        assertEquals(listOf(NotflixSettingsPage.ACCOUNTS, NotflixSettingsPage.ACCOUNTS_ADVANCED, NotflixSettingsPage.TMDB_KEY), initialNotflixSettingsPath(true))
        listOf("ACCOUNTS", "TMDB_OPTIONS", "HOME", "PLAYBACK", "ABOUT", "HOME_ADVANCED", "NEWS").forEach { target ->
            val path = initialNotflixSettingsPath(false, "11:$target")
            assertEquals(target, path.last().name)
            assertEquals(path.first().section, path.last().section)
        }
        assertTrue(initialNotflixSettingsPath(false, "invalid").isEmpty())
        val state = SavedStateHandle()
        NotflixSettingsEntry.requestNotflixIntegrations(state)
        NotflixSettingsEntry.requestDestination(state, "ABOUT")
        val first = state.get<String>(NotflixSettingsEntry.DESTINATION)
        NotflixSettingsEntry.requestDestination(state, "ABOUT")
        assertNotEquals(first, state.get<String>(NotflixSettingsEntry.DESTINATION))
        assertTrue(NotflixSettingsEntry.isNotflixIntegrationsRequested(state))
        NotflixSettingsEntry.requestDestination(state, "invalid")
        assertEquals("2:ABOUT", state.get<String>(NotflixSettingsEntry.DESTINATION))
    }

    @Test fun secondaryProfilesKeepTheOriginalAccountAndPartyRestrictions() {
        assertFalse(NotflixSettingsSection.PROFILE in visibleNotflixSettingsSections(false, false))
        assertEquals(listOf(NotflixSettingsPage.PARTY), notflixSettingsChildren(NotflixSettingsPage.PROFILE, false, true))
        assertFalse(notflixSettingsPageAllowed(NotflixSettingsPage.PROFILES, false, true))
        assertFalse(notflixSettingsPageAllowed(NotflixSettingsPage.ACCOUNT, false, true))
        assertTrue(notflixSettingsChildren(NotflixSettingsPage.ADDONS, plugins = false).none { it == NotflixSettingsPage.PLUGINS })
        assertTrue(notflixSettingsChildren(NotflixSettingsPage.SYSTEM_ADVANCED, debug = false).isEmpty())
    }
}
