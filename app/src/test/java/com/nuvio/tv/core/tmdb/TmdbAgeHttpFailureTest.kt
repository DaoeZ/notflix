package com.nuvio.tv.core.tmdb

import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.data.local.NotflixIntegrationConfig
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.remote.api.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class TmdbAgeHttpFailureTest {
    private val service = mockk<TmdbService> { coEvery { ensureTmdbId(any(), any(), any()) } returns "123" }
    private val api = mockk<TmdbApi>()
    private val config = NotflixIntegrationConfig(builtInTmdbApiKey = "synthetic-build-key") { NotflixIntegrationCredentials() }
    private val source = TmdbAgeRatingSourceImpl(service, api, config)

    @Test fun anUnauthorizedMovieLookupIsNotCachedAndCanRecover() = runTest {
        coEvery { api.getMovieReleaseDates(any(), any()) } returnsMany listOf(
            Response.error(401, "{}".toResponseBody()),
            Response.success(TmdbMovieReleaseDatesResponse(listOf(
                TmdbMovieReleaseDateCountry("ES", listOf(TmdbMovieReleaseDateItem("TP")))
            )))
        )
        assertFalse(source.lookup("movie", "tmdb:123", null).cacheable)
        assertEquals(AgeLevel.TP, source.lookup("movie", "tmdb:123", null).level)
        coVerify(exactly = 2) { api.getMovieReleaseDates(123, "synthetic-build-key") }
    }

    @Test fun failedOrMissingTvResponsesCannotBecomeAThirtyDayNegative() = runTest {
        coEvery { api.getTvContentRatings(any(), any()) } returnsMany listOf(
            Response.error(503, "{}".toResponseBody()), Response.success(null)
        )
        repeat(2) {
            val result = source.lookup("series", "tmdb:123", null)
            assertNull(result.level)
            assertFalse(result.cacheable)
        }
    }

    @Test fun aSuccessfulUnratedAnswerStillHidesTheTitleAndCanBeCached() = runTest {
        coEvery { api.getMovieReleaseDates(any(), any()) } returns Response.success(TmdbMovieReleaseDatesResponse(emptyList()))
        val result = source.lookup("movie", "tmdb:123", null)
        assertNull(result.level)
        assertTrue(result.cacheable)
    }

    @Test fun cancellingAnAgeLookupPropagatesCancellation() = runTest {
        coEvery { api.getMovieReleaseDates(any(), any()) } throws CancellationException("retired")
        try {
            source.lookup("movie", "tmdb:123", null)
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
