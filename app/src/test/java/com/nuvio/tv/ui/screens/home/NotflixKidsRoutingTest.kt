package com.nuvio.tv.ui.screens.home

import android.content.Context
import com.nuvio.tv.core.profile.*
import com.nuvio.tv.core.util.withAppLocale
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Actual Home method and configuration extension; only the downstream row adapter is substituted. */
class NotflixKidsRoutingTest {
    @Test fun allFourKidsKeysReachTheNotflixAdapterInsteadOfTheOfficialCatalogLoader() {
        val rowAdapter = mockk<NotflixCollectionRows>(relaxed = true)
        val context = mockk<Context> { every { getString(any()) } returns "Localized" }
        val gate = mockk<ActiveProfileAgeGate> { every { currentLimit() } returns AgeLevel.TP }
        val profiles = mockk<ProfileManager> { every { activeProfileId } returns MutableStateFlow(2) }
        val state = MutableStateFlow(HomeUiState(notflixPresentationActive = true, ageLimit = AgeLevel.TP))
        val model = mockk<HomeViewModel>(relaxed = true) {
            every { _uiState } returns state
            every { ageGate } returns gate
            every { profileManager } returns profiles
            every { notflixCollectionsProfile } returns 2
            every { tmdbAvailability } returns TmdbAvailability { true }
            every { appContext } returns context
            every { addonsCache } returns emptyList()
            every { notflixCollectionRows } returns rowAdapter
            every { requestLazyCatalogLoad(any()) } answers { callOriginal() }
        }
        // The member method reads its own Kotlin backing field, unlike the external extension.
        HomeViewModel::class.java.getDeclaredField("notflixCollectionRows").apply { isAccessible = true }
            .set(model, rowAdapter)
        mockkStatic("com.nuvio.tv.core.util.AppLocaleContextKt")
        try {
            every { context.withAppLocale() } returns context
            val keys = listOf(
                "kids-animacion", "kids-familia", "kids-series-animacion", "kids-series-familia",
                "kids-aventura", "kids-comedia", "kids-fantasia", "kids-estrenos", "kids-cortas-dormir"
            ).map { "collection_notflix-kids:$it" }
            keys.forEach(model::requestLazyCatalogLoad)
            keys.forEach { key -> verify(exactly = 1) { rowAdapter.request(key, false) } }
            val definitions = mutableListOf<List<NotflixCollectionSpec>>()
            verify(exactly = keys.size) { rowAdapter.configure(any(), capture(definitions)) }
            assertEquals(keys, definitions.last().map { it.key })
        } finally { unmockkStatic("com.nuvio.tv.core.util.AppLocaleContextKt") }
    }
}
