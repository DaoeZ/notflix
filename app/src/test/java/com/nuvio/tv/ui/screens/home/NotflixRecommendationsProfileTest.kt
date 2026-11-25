package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.ViewModelStore
import com.nuvio.tv.core.profile.*
import com.nuvio.tv.data.local.MoreLikeThisSourcePreference
import com.nuvio.tv.data.repository.TraktRelatedService
import com.nuvio.tv.domain.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotflixRecommendationsProfileTest {
    private fun preview(id: String, rating: String?) = MetaPreview(id, ContentType.MOVIE, name = id,
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = emptyList(), ageRating = rating)

    @Test fun recommendationsUseThePersistedChildLimitEvenBeforeTheObservableLimitCatchesUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val candidates = listOf(preview("safe", "G"), preview("hidden", "G"), preview("adult", "18"), preview("unknown", null))
            val gate = mockk<ActiveProfileAgeGate> {
                every { limit } returns MutableStateFlow(null)
                every { snapshot } returns MutableStateFlow(AgeGateSnapshot(null, emptyMap()))
                coEvery { currentLimitFromStore() } returns AgeLevel.TP
                coEvery { filterForLimit(any(), any()) } answers {
                    firstArg<List<MetaPreview>>().filter { isAllowed(secondArg(), ageLevelOf(it.ageRating)) }
                }
                every { knownRating(any<MetaPreview>()) } answers { ageLevelOf(firstArg<MetaPreview>().ageRating) }
            }
            val related = mockk<TraktRelatedService> {
                coEvery { getRelated(any(), any(), any(), any()) } returns candidates
            }
            val anchor = mockk<Meta>(relaxed = true) {
                every { id } returns "anchor"; every { name } returns "Anchor"
                every { type } returns ContentType.MOVIE; every { ageRating } returns "G"
            }
            val progress = WatchProgress("anchor", "movie", "Anchor", null, null, null, "anchor", null, null, null,
                position = 5, duration = 10, lastWatched = 1)
            val vm = NotflixPersonalRowsViewModel(
                libraryRepository = mockk(relaxed = true),
                watchProgressRepository = mockk {
                    every { allProgress } returns MutableStateFlow(listOf(progress))
                    every { observeWatchedMovieIds() } returns flowOf(emptySet())
                },
                metaRepository = mockk { every { getCachedMeta("movie", "anchor") } returns anchor },
                tmdbService = mockk(relaxed = true), tmdbMetadataService = mockk(relaxed = true),
                tmdbSettingsDataStore = mockk(relaxed = true), traktRelatedService = related,
                traktAuthDataStore = mockk { every { isAuthenticated } returns flowOf(true) },
                traktSettingsDataStore = mockk { every { moreLikeThisSource } returns flowOf(MoreLikeThisSourcePreference.TRAKT) },
                simklRelatedService = mockk(relaxed = true), simklAuthRepository = mockk(relaxed = true),
                layoutPreferenceDataStore = mockk { every { hideUnreleasedContent } returns flowOf(false) },
                watchedSeriesStateHolder = mockk { every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet()) },
                ageGate = gate,
                profileManager = mockk { every { activeProfileId } returns MutableStateFlow(2) },
                hiddenTitlesRepository = mockk { every { observe(2) } returns flowOf(setOf("movie:hidden")) })
            owner.put("recommendations", vm)
            val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.becauseYouWatched.collect() }
            runCurrent()
            assertEquals(2, vm.becauseYouWatched.value?.profileId)
            assertEquals(listOf("safe"), vm.becauseYouWatched.value?.items?.map { it.id })
            subscription.cancelAndJoin()
        } finally {
            owner.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    // M70 on the TV: hiding from this row reloaded it empty first, so focus fell to the next row.
    @Test fun hidingATitleFiltersTheLoadedRowWithoutReloadingOrEmptyingIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val gate = mockk<ActiveProfileAgeGate> {
                every { limit } returns MutableStateFlow(null)
                every { snapshot } returns MutableStateFlow(AgeGateSnapshot(null, emptyMap()))
                coEvery { currentLimitFromStore() } returns null
            }
            val related = mockk<TraktRelatedService> {
                coEvery { getRelated(any(), any(), any(), any()) } returns listOf(preview("a", "G"), preview("b", "G"), preview("c", "G"))
            }
            val anchor = mockk<Meta>(relaxed = true) {
                every { id } returns "anchor"; every { name } returns "Anchor"
                every { type } returns ContentType.MOVIE; every { ageRating } returns "G"
            }
            val progress = WatchProgress("anchor", "movie", "Anchor", null, null, null, "anchor", null, null, null,
                position = 5, duration = 10, lastWatched = 1)
            val hidden = MutableStateFlow(emptySet<String>())
            val vm = NotflixPersonalRowsViewModel(
                libraryRepository = mockk(relaxed = true),
                watchProgressRepository = mockk {
                    every { allProgress } returns MutableStateFlow(listOf(progress))
                    every { observeWatchedMovieIds() } returns flowOf(emptySet())
                },
                metaRepository = mockk { every { getCachedMeta("movie", "anchor") } returns anchor },
                tmdbService = mockk(relaxed = true), tmdbMetadataService = mockk(relaxed = true),
                tmdbSettingsDataStore = mockk(relaxed = true), traktRelatedService = related,
                traktAuthDataStore = mockk { every { isAuthenticated } returns flowOf(true) },
                traktSettingsDataStore = mockk { every { moreLikeThisSource } returns flowOf(MoreLikeThisSourcePreference.TRAKT) },
                simklRelatedService = mockk(relaxed = true), simklAuthRepository = mockk(relaxed = true),
                layoutPreferenceDataStore = mockk { every { hideUnreleasedContent } returns flowOf(false) },
                watchedSeriesStateHolder = mockk { every { fullyWatchedSeriesIds } returns MutableStateFlow(emptySet()) },
                ageGate = gate,
                profileManager = mockk { every { activeProfileId } returns MutableStateFlow(2) },
                hiddenTitlesRepository = mockk { every { observe(2) } returns hidden })
            owner.put("recommendations", vm)
            val seen = mutableListOf<List<String>?>()
            val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.becauseYouWatched.collect { seen += it?.items?.map { item -> item.id } }
            }
            runCurrent()
            assertEquals(listOf("a", "b", "c"), seen.last())
            val before = seen.size
            hidden.value = setOf("movie:b")
            runCurrent()
            assertEquals(listOf(listOf("a", "c")), seen.drop(before))
            hidden.value = setOf("movie:b", "movie:anchor")
            runCurrent()
            assertNull(vm.becauseYouWatched.value)
            coVerify(exactly = 1) { related.getRelated(any(), any(), any(), any()) }
            subscription.cancelAndJoin()
        } finally {
            owner.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
