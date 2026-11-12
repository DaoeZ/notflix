package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class NotflixSettingsEntryTest {
    @Test fun destinationIsBoundToItsOwnEntryAndSurvivesSavedStateRecreation() {
        val requested = SavedStateHandle()
        val unrelated = SavedStateHandle()
        assertFalse(NotflixSettingsEntry.isNotflixIntegrationsRequested(requested))
        NotflixSettingsEntry.requestNotflixIntegrations(requested)
        assertTrue(NotflixSettingsEntry.isNotflixIntegrationsRequested(requested))
        assertFalse(NotflixSettingsEntry.isNotflixIntegrationsRequested(unrelated))
        val restored = SavedStateHandle(requested.keys().associateWith { requested.get<Any>(it) })
        assertTrue(NotflixSettingsEntry.isNotflixIntegrationsRequested(restored))
        assertFalse(NotflixSettingsEntry.isNotflixIntegrationsRequested(SavedStateHandle()))
    }
}
