package com.nuvio.tv.core.tmdb

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.NotflixIntegrationConfig
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbDiscoverResponse
import com.nuvio.tv.data.remote.api.TmdbDiscoverResult
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.TmdbCollectionMediaType
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.TmdbSettings
import com.nuvio.tv.ui.appearance.NotflixKidsHomePreset
import com.nuvio.tv.ui.appearance.NotflixKidsHomeTexts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** M68: the four KIDS rows ask TMDB for exactly the agreed Discover queries. */
class NotflixKidsDiscoverRowsTest {
    private val context = mockk<android.content.Context>(relaxed = true)
    private val integrationConfig = NotflixIntegrationConfig {
        NotflixIntegrationCredentials(tmdbApiKey = "test-tmdb-key")
    }
    private val settings = mockk<TmdbSettingsDataStore> {
        every { this@mockk.settings } returns MutableStateFlow(TmdbSettings(language = "en"))
    }
    private val texts = NotflixKidsHomeTexts("Anim movies", "Family movies", "Anim series", "Family series")
    private val sources: List<TmdbCollectionSource> =
        NotflixKidsHomePreset.createDiscover(texts).folders.map { it.sources.single() as TmdbCollectionSource }

    private fun discover(items: List<TmdbDiscoverResult> = emptyList()) = TmdbDiscoverResponse(
        page = 1, results = items, totalPages = 1, totalResults = items.size
    )

    /** A full page, so a certified row does not ask TMDB for the US G completion. */
    private fun fullPage() = (1..20).map { index -> TmdbDiscoverResult(id = index, title = "Title $index") }

    private fun stubMovies(
        api: TmdbApi,
        answer: (Array<Any?>) -> Response<TmdbDiscoverResponse> = { Response.success(discover()) }
    ) {
        coEvery {
            api.discoverMovies(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())
        } answers { answer(invocation.args.toTypedArray()) }
    }

    private fun stubTv(
        api: TmdbApi,
        answer: (Array<Any?>) -> Response<TmdbDiscoverResponse> = { Response.success(discover()) }
    ) {
        coEvery {
            api.discoverTv(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers { answer(invocation.args.toTypedArray()) }
    }

    private suspend fun resolve(api: TmdbApi, source: TmdbCollectionSource): CatalogRow {
        val resolver = TmdbCollectionSourceResolver(context, api, settings, integrationConfig)
        return (resolver.resolve(source).first { it is NetworkResult.Success } as NetworkResult.Success).data
    }

    @Test
    fun `animated movies filter by Spain all ages popularity and votes`() = runTest {
        val api = mockk<TmdbApi>()
        val calls = mutableListOf<Array<Any?>>()
        stubMovies(api) { args -> calls += args; Response.success(discover(fullPage())) }

        resolve(api, sources[0])

        val args = calls.single()
        assertEquals("test-tmdb-key", args[0])
        assertEquals("en", args[1])
        assertEquals("popularity.desc", args[3])
        assertEquals(50, args[6])
        assertEquals("16", args[7])
        assertEquals("ES", args[22])
        assertEquals("A|Ai", args[23])
    }

    @Test
    fun `family movies reuse the certificate with the family genre`() = runTest {
        val api = mockk<TmdbApi>()
        val calls = mutableListOf<Array<Any?>>()
        stubMovies(api) { args -> calls += args; Response.success(discover(fullPage())) }

        resolve(api, sources[1])

        val args = calls.single()
        assertEquals("10751", args[7])
        assertEquals(50, args[6])
        assertEquals("ES", args[22])
        assertEquals("A|Ai", args[23])
    }

    @Test
    fun `animated series exclude crime drama and war and are not filtered by certificate`() = runTest {
        val api = mockk<TmdbApi>()
        var captured: Array<Any?>? = null
        stubTv(api) { args -> captured = args; Response.success(discover()) }

        resolve(api, sources[2])

        val args = requireNotNull(captured)
        assertEquals("16", args[8])
        assertEquals("80,18,10768", args[21])
        assertEquals("popularity.desc", args[3])
    }

    @Test
    fun `kids series use the TMDB kids genre`() = runTest {
        val api = mockk<TmdbApi>()
        var captured: Array<Any?>? = null
        stubTv(api) { args -> captured = args; Response.success(discover()) }

        resolve(api, sources[3])

        val args = requireNotNull(captured)
        assertEquals("10762", args[8])
        assertEquals("popularity.desc", args[3])
    }

    @Test
    fun `a short certified movie row is completed with US G`() = runTest {
        val api = mockk<TmdbApi>()
        val calls = mutableListOf<Array<Any?>>()
        stubMovies(api) { args ->
            calls += args
            if (args[22] == "US") {
                Response.success(discover((1..3).map { TmdbDiscoverResult(id = 100 + it, title = "Fill $it") }))
            } else {
                Response.success(discover(listOf(TmdbDiscoverResult(id = 1, title = "Only one"))))
            }
        }

        val row = resolve(api, sources[0])

        assertEquals(2, calls.size)
        val fill = calls[1]
        assertEquals("US", fill[22])
        assertEquals("G", fill[23])
        assertEquals("16", fill[7])
        assertEquals(50, fill[6])
        assertEquals(listOf("tmdb:1", "tmdb:101", "tmdb:102", "tmdb:103"), row.items.map { it.id })
        assertEquals(TmdbCollectionMediaType.MOVIE, sources[0].mediaType)
    }

    @Test
    fun `a full certified row is never completed twice`() = runTest {
        val api = mockk<TmdbApi>()
        var calls = 0
        stubMovies(api) {
            calls++
            Response.success(discover((1..20).map { index -> TmdbDiscoverResult(id = index, title = "Title $index") }))
        }

        val row = resolve(api, sources[0])

        assertEquals(1, calls)
        assertEquals(20, row.items.size)
    }

    @Test
    fun `a failed TMDB answer is an error so the caller can fall back to Cinemeta`() = runTest {
        val api = mockk<TmdbApi>()
        stubMovies(api) { Response.success(null) }
        val resolver = TmdbCollectionSourceResolver(context, api, settings, integrationConfig)

        val result = resolver.resolve(sources[0]).first { it !is NetworkResult.Loading }

        assertTrue(result is NetworkResult.Error)
    }
    @Test fun `US completion keeps exclusions and dates and adds only the missing unique titles`() = runTest {
        val api = mockk<TmdbApi>()
        val calls = mutableListOf<Array<Any?>>()
        val source = sources[0].copy(filters = sources[0].filters.copy(
            withoutGenres = "80", releaseDateGte = "2020-01-01", releaseDateLte = "2025-12-31",
            withOriginalLanguage = "es", voteAverageGte = 5.0
        ))
        stubMovies(api) { args ->
            calls += args
            val items = if (args[22] == "US") fullPage() + TmdbDiscoverResult(id = 21, title = "extra")
                else fullPage().take(19)
            Response.success(discover(items))
        }
        val result = resolve(api, source)
        assertEquals(20, result.items.size)
        val expected = calls.first().copyOf().apply { this[22] = "US"; this[23] = "G" }
        org.junit.Assert.assertArrayEquals(expected.take(24).toTypedArray(), calls.last().take(24).toTypedArray())
        assertEquals(20, result.items.map { it.id }.distinct().size)
    }

    @Test fun `page two uses page two and does not replay the first-page US fill`() = runTest {
        val api = mockk<TmdbApi>()
        val calls = mutableListOf<Array<Any?>>()
        stubMovies(api) { args -> calls += args; Response.success(discover(fullPage().take(2))) }
        TmdbCollectionSourceResolver(context, api, settings, integrationConfig).resolve(sources[0], page = 2)
            .first { it is NetworkResult.Success }
        assertEquals(1, calls.size)
        assertEquals(2, calls.single()[2])
        assertEquals("ES", calls.single()[22])
    }

    @Test fun `a failed fill preserves the Spanish titles`() = runTest {
        val api = mockk<TmdbApi>()
        stubMovies(api) { args ->
            if (args[22] == "US") Response.success(null)
            else Response.success(discover(fullPage().take(2)))
        }
        assertEquals(listOf("tmdb:1", "tmdb:2"), resolve(api, sources[0]).items.map { it.id })
    }

}
