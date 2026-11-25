package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.CollectionCatalogRequest
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.ui.appearance.NotflixKidsHomePreset
import com.nuvio.tv.ui.appearance.NotflixKidsHomeTexts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M68: a failed TMDB kids row falls back to its Cinemeta row instead of leaving Home empty. */
class NotflixKidsRowFallbackTest {
    private fun item(id: String) = MetaPreview(id = id, type = ContentType.MOVIE, name = id, poster = null,
        posterShape = PosterShape.POSTER, background = null, logo = null, description = null, releaseInfo = null,
        imdbRating = null, genres = emptyList())

    private fun row(id: String) = CatalogRow("tmdb", "TMDB", "", "kids", "Kids", ContentType.MOVIE,
        items = listOf(item(id)))

    @Test
    fun primarySuccessNeverAsksForTheFallback() = runTest {
        val result = notflixRowWithFallback(
            flowOf(NetworkResult.Loading, NetworkResult.Success(row("primary")))
        ) { error("fallback must not run") }.toList()

        assertEquals(listOf(NetworkResult.Loading, NetworkResult.Success(row("primary"))), result)
    }

    @Test
    fun aPrimaryFailureUsesTheFallbackExactlyOnce() = runTest {
        var fallbacks = 0
        val fallback = flowOf(NetworkResult.Loading, NetworkResult.Success(row("cinemeta")))
        val primary: Flow<NetworkResult<CatalogRow>> = flowOf(
            NetworkResult.Loading,
            NetworkResult.Error("tmdb down"),
            NetworkResult.Error("tmdb down again")
        )

        val result = notflixRowWithFallback(primary) { fallbacks++; fallback }.toList()

        assertEquals(1, fallbacks)
        // The primary error is replaced by the fallback flow, which starts with its own Loading.
        assertEquals(NetworkResult.Loading, result[0])
        assertEquals(NetworkResult.Loading, result[1])
        assertEquals(NetworkResult.Success(row("cinemeta")), result[2])
        // A retired primary flow cannot overwrite the successful fallback.
        assertEquals(3, result.size)
    }

    @Test
    fun aFailureWithoutFallbackStaysAnError() = runTest {
        val result = notflixRowWithFallback(flowOf(NetworkResult.Error("tmdb down"))) { null }.toList()

        assertEquals(listOf<NetworkResult<CatalogRow>>(NetworkResult.Error("tmdb down")), result)
    }

    @Test
    fun aTmdbSpecLoadsThroughTheTmdbLoaderAndKeepsItsCinemetaFallbackRequest() = runTest {
        val texts = NotflixKidsHomeTexts("Anim movies", "Family movies", "Anim series", "Family series")
        val source = NotflixKidsHomePreset.createDiscover(texts).folders.first().sources.single() as TmdbCollectionSource
        val fallbackRequest = CollectionCatalogRequest(
            addon = com.nuvio.tv.domain.model.Addon("com.linvo.cinemeta", "Cinemeta", version = "1",
                description = null, logo = null, baseUrl = "https://cinemeta.invalid", catalogs = emptyList(),
                types = listOf(ContentType.MOVIE), resources = emptyList()),
            catalog = null, catalogId = "top", type = "movie", extraArgs = mapOf("genre" to "Animation")
        )
        val spec = NotflixCollectionSpec("kids", "Anim movies", "notflix-kids" to "kids-animacion", null, null,
            tmdb = source, fallbackRequest = fallbackRequest)
        val tmdbCalls = mutableListOf<Pair<TmdbCollectionSource, CollectionCatalogRequest?>>()
        var addonCalls = 0
        var filters = 0
        val loader = NotflixCollectionRows(this,
            { addonCalls++; flowOf(NetworkResult.Success(row("addon"))) },
            { true }, {},
            { current -> filters++; current },
            loadTmdb = { tmdb, fallback ->
                tmdbCalls += tmdb to fallback
                flowOf(NetworkResult.Success(row("tmdb")))
            })

        loader.configure(NotflixCollectionScope(2, 1, 1, false), listOf(spec))
        loader.request(spec.key)
        testScheduler.advanceUntilIdle()

        assertEquals(0, addonCalls)
        assertEquals(1, tmdbCalls.size)
        assertEquals(source, tmdbCalls.single().first)
        assertEquals(fallbackRequest, tmdbCalls.single().second)
        assertEquals(1, filters)
        val published = loader.snapshot().single()
        assertEquals(NotflixRowPhase.READY, published.phase)
        assertEquals("tmdb", published.catalog!!.items.single().id)
        assertNull(published.sourceIdentity)
        assertEquals("notflix-kids" to "kids-animacion", published.folderTarget)
    }

    @Test
    fun aTmdbSpecWithoutAnyLoaderWouldStillPublishItsFolderDestination() = runTest {
        val source = TmdbCollectionSource(sourceType = com.nuvio.tv.domain.model.TmdbCollectionSourceType.DISCOVER,
            title = "Kids", mediaType = com.nuvio.tv.domain.model.TmdbCollectionMediaType.MOVIE)
        val spec = NotflixCollectionSpec("kids", "Kids", "notflix-kids" to "kids-animacion", null, null, tmdb = source)
        val loader = NotflixCollectionRows(this, { flowOf(NetworkResult.Success(row("addon"))) }, { true }, {})

        loader.configure(NotflixCollectionScope(2, 1, 1, false), listOf(spec))
        testScheduler.advanceUntilIdle()

        val published = loader.snapshot().single()
        assertEquals(NotflixRowPhase.PENDING, published.phase)
        assertTrue(published.folderTarget != null)
    }
    @Test fun thrownFailureFallsBackButCancellationNeverDoes() = runTest {
        var calls = 0
        val fallback = flowOf(NetworkResult.Success(row("replacement")))
        val result = notflixRowWithFallback(kotlinx.coroutines.flow.flow<NetworkResult<CatalogRow>> {
            throw IllegalStateException("network down")
        }) { calls++; fallback }.toList()
        assertEquals(listOf(NetworkResult.Success(row("replacement"))), result)
        try {
            notflixRowWithFallback(kotlinx.coroutines.flow.flow<NetworkResult<CatalogRow>> {
                throw kotlinx.coroutines.CancellationException("retired")
            }) { calls++; fallback }.toList()
            org.junit.Assert.fail("cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun unavailableTmdbWithEmptyCinemetaRowsKeepsTheKidsRecoveryNotice() {
        val empty = row("old").copy(addonId = "com.linvo.cinemeta", items = emptyList())
        val state = HomeUiState(ageLimit = com.nuvio.tv.core.profile.AgeLevel.TP,
            ageTmdbKeyAvailable = true, notflixCatalog = NotflixCatalogSnapshot(configured = true, rows = listOf(
                NotflixCatalogRow("kids", "kids", "Kids", NotflixRowPhase.READY, catalog = empty,
                    folderTarget = "notflix-kids" to "kids-animacion")
            )))
        assertTrue(notflixKidsNeedsTmdbKey(state))
        val pending = state.notflixCatalog.rows.single().copy(phase = NotflixRowPhase.PENDING)
        org.junit.Assert.assertFalse(notflixKidsNeedsTmdbKey(state.copy(notflixCatalog = state.notflixCatalog.copy(rows = listOf(pending)))))
        val notLoaded = pending.copy(phase = NotflixRowPhase.READY, catalog = null)
        org.junit.Assert.assertFalse(notflixKidsNeedsTmdbKey(state.copy(notflixCatalog = state.notflixCatalog.copy(rows = listOf(notLoaded)))))
        org.junit.Assert.assertFalse(notflixKidsNeedsTmdbKey(state.copy(ageLimit = null)))
        val healthy = state.notflixCatalog.rows.single().copy(catalog = empty.copy(addonId = "tmdb"))
        org.junit.Assert.assertFalse(notflixKidsNeedsTmdbKey(state.copy(notflixCatalog = state.notflixCatalog.copy(rows = listOf(healthy)))))
    }

}
