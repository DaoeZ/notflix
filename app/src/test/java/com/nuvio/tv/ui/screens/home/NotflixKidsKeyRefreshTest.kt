package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.core.profile.ActiveProfileAgeGate
import com.nuvio.tv.core.profile.TmdbAvailability
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class NotflixKidsKeyRefreshTest {
    private data class Fixture(val model: HomeViewModel, val state: MutableStateFlow<HomeUiState>,
        val rows: NotflixCollectionRows)

    private fun fixture(limit: AgeLevel?): Fixture {
        val state = MutableStateFlow(HomeUiState(notflixPresentationActive = true, ageLimit = limit,
            ageTmdbKeyAvailable = false))
        val gate = mockk<ActiveProfileAgeGate> { every { currentLimit() } returns limit }
        val availability = TmdbAvailability { true }
        val rows = mockk<NotflixCollectionRows>(relaxed = true)
        val model = mockk<HomeViewModel> {
            every { _uiState } returns state
            every { ageGate } returns gate
            every { tmdbAvailability } returns availability
            every { notflixCollectionRows } returns rows
            every { addonsCache } returns emptyList()
            every { scheduleUpdateCatalogRows() } just Runs
        }
        return Fixture(model, state, rows)
    }

    @Test fun returningFromKeyEntryRefreshesKidsBeforeTheCatalogEarlyReturns() {
        val (model, state, rows) = fixture(AgeLevel.TP)
        model.refreshNotflixKidsTmdbAvailability()
        assertTrue(state.value.ageTmdbKeyAvailable)
        verify(exactly = 1) { rows.clear() }
        verify(exactly = 1) { model.scheduleUpdateCatalogRows() }
    }

    @Test fun normalProfilesKeepTheExistingRefreshBehaviour() {
        val (model, state, rows) = fixture(null)
        model.refreshNotflixKidsTmdbAvailability()
        assertFalse(state.value.ageTmdbKeyAvailable)
        verify(exactly = 0) { rows.clear() }
    }
}
