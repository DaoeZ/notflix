package com.nuvio.tv.data.repository

import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TraktAuthState
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktCalendarShowDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class CalendarRepositoryTest {

    private val traktApi = mockk<TraktApi>()
    private val authService = mockk<TraktAuthService>()
    private val tmdbService = mockk<TmdbService>(relaxed = true)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun repository() = CalendarRepository(
        traktApi = traktApi,
        traktAuthService = authService,
        tmdbService = tmdbService
    )

    @Test
    fun `trakt connected and successful uses TRAKT and maps the dto`() = runTest {
        stubAuthorizedTrakt(Response.success(parseCalendar(SAMPLE_JSON)))

        val result = repository().upcoming(
            nuvioUpcoming = listOf(
                nuvioShow(contentId = "tt1234567", seriesName = "Sample Show", poster = "https://nuvio/p.jpg")
            ),
            now = Instant.parse("2026-11-14T10:00:00Z"),
            zone = ZoneId.of("Europe/Madrid")
        )

        assertEquals(CalendarSource.TRAKT, result.source)
        assertFalse(result.traktFailed)
        assertEquals(2, result.entries.size)

        val entry = result.entries.first { it.contentId == "tt1234567" }
        assertEquals("Sample Show", entry.seriesName)
        assertEquals("https://nuvio/p.jpg", entry.poster)
        assertEquals(2, entry.season)
        assertEquals(5, entry.episode)
        assertEquals("The Test", entry.episodeTitle)
        assertTrue(entry.hasTime)
        assertEquals(Instant.parse("2026-11-15T21:00:00Z"), entry.airInstant)

        val tmdbOnly = result.entries.first { it.contentId == "777" }
        assertEquals(777, tmdbOnly.tmdbId)
        assertEquals(null, tmdbOnly.poster)

        coVerify(exactly = 1) {
            traktApi.getMyShowsCalendar("Bearer test", "2026-11-14", 30, "full")
        }
    }

    @Test
    fun `trakt error falls back to NUVIO and marks the failure`() = runTest {
        stubAuthorizedTrakt(
            Response.error(500, "boom".toResponseBody("application/json".toMediaType()))
        )

        val result = repository().upcoming(
            nuvioUpcoming = listOf(
                nuvioShow(
                    contentId = "tt1234567",
                    seriesName = "Sample Show",
                    released = "2026-11-20"
                )
            ),
            now = Instant.parse("2026-11-14T10:00:00Z"),
            zone = ZoneId.of("Europe/Madrid")
        )

        assertEquals(CalendarSource.NUVIO, result.source)
        assertTrue(result.traktFailed)
        assertEquals(listOf("tt1234567"), result.entries.map { it.contentId })
    }

    @Test
    fun `without account it uses NUVIO and never calls Trakt`() = runTest {
        every { authService.hasRequiredCredentials() } returns false
        coEvery { authService.getCurrentAuthState() } returns TraktAuthState()

        val result = repository().upcoming(
            nuvioUpcoming = listOf(nuvioShow(contentId = "tt1234567", seriesName = "Sample Show")),
            now = Instant.parse("2026-11-14T10:00:00Z"),
            zone = ZoneId.of("Europe/Madrid")
        )

        assertEquals(CalendarSource.NUVIO, result.source)
        assertFalse(result.traktFailed)
        coVerify(exactly = 0) {
            authService.executeAuthorizedRequest<List<TraktCalendarShowDto>>(any())
        }
        coVerify(exactly = 0) {
            traktApi.getMyShowsCalendar(any(), any(), any(), any())
        }
    }

    @Test
    fun `configured but without a valid token also stays on NUVIO`() = runTest {
        every { authService.hasRequiredCredentials() } returns true
        coEvery { authService.getCurrentAuthState() } returns TraktAuthState(accessToken = "access")

        val result = repository().upcoming(
            nuvioUpcoming = listOf(nuvioShow(contentId = "tt1234567")),
            now = Instant.parse("2026-11-14T10:00:00Z"),
            zone = ZoneId.of("Europe/Madrid")
        )

        assertEquals(CalendarSource.NUVIO, result.source)
        assertFalse(result.traktFailed)
        coVerify(exactly = 0) {
            traktApi.getMyShowsCalendar(any(), any(), any(), any())
        }
    }

    @Test
    fun `nuvio entries without a date are excluded and dates respect the local zone`() = runTest {
        every { authService.hasRequiredCredentials() } returns false
        coEvery { authService.getCurrentAuthState() } returns TraktAuthState()
        val zone = ZoneId.of("America/New_York")

        val result = repository().upcoming(
            nuvioUpcoming = listOf(
                nuvioShow(contentId = "tt1", released = null, releaseTimestamp = null),
                nuvioShow(contentId = "tt2", released = "2026-11-20", releaseTimestamp = null),
                nuvioShow(
                    contentId = "tt3",
                    released = null,
                    releaseTimestamp = Instant.parse("2026-11-21T02:00:00Z").toEpochMilli()
                )
            ),
            now = Instant.parse("2026-11-14T10:00:00Z"),
            zone = zone
        )

        assertEquals(setOf("tt2", "tt3"), result.entries.map { it.contentId }.toSet())

        val dated = result.entries.first { it.contentId == "tt2" }
        assertFalse(dated.hasTime)
        assertEquals(LocalDate.of(2026, 11, 20), dated.airInstant.atZone(zone).toLocalDate())

        val instant = result.entries.first { it.contentId == "tt3" }
        assertTrue(instant.hasTime)
        assertEquals(LocalDate.of(2026, 11, 20), instant.airInstant.atZone(zone).toLocalDate())
    }

    @Test
    fun `a date-only release with a midnight timestamp keeps its local day`() = runTest {
        every { authService.hasRequiredCredentials() } returns false
        coEvery { authService.getCurrentAuthState() } returns TraktAuthState()
        val zone = ZoneId.of("America/New_York")

        // Caso real del pipeline: HomeViewModel rellena releaseTimestamp con
        // parseEpisodeReleaseInstant, que convierte la fecha sin hora en medianoche UTC.
        val result = repository().upcoming(
            nuvioUpcoming = listOf(
                nuvioShow(
                    contentId = "tt2",
                    released = "2026-11-20",
                    releaseTimestamp = Instant.parse("2026-11-20T00:00:00Z").toEpochMilli()
                )
            ),
            now = Instant.parse("2026-11-14T10:00:00Z"),
            zone = zone
        )

        val dated = result.entries.single { it.contentId == "tt2" }
        assertFalse(dated.hasTime)
        assertEquals(LocalDate.of(2026, 11, 20), dated.airInstant.atZone(zone).toLocalDate())
    }

    private fun stubAuthorizedTrakt(response: Response<List<TraktCalendarShowDto>>) {
        every { authService.hasRequiredCredentials() } returns true
        coEvery { authService.getCurrentAuthState() } returns TraktAuthState(
            accessToken = "access",
            refreshToken = "refresh",
            createdAt = 1L,
            expiresIn = 3600
        )
        coEvery { authService.executeAuthorizedRequest<List<TraktCalendarShowDto>>(any()) } coAnswers {
            val call = firstArg<suspend (String) -> Response<List<TraktCalendarShowDto>>>()
            call("Bearer test")
        }
        coEvery { traktApi.getMyShowsCalendar(any(), any(), any(), any()) } returns response
    }

    private fun parseCalendar(json: String): List<TraktCalendarShowDto> {
        val type = Types.newParameterizedType(List::class.java, TraktCalendarShowDto::class.java)
        return checkNotNull(moshi.adapter<List<TraktCalendarShowDto>>(type).fromJson(json))
    }

    private fun nuvioShow(
        contentId: String,
        seriesName: String = "Nuvio Show",
        poster: String? = null,
        released: String? = null,
        releaseTimestamp: Long? = null
    ) = CalendarNuvioShow(
        contentId = contentId,
        seriesName = seriesName,
        poster = poster,
        season = 1,
        episode = 1,
        episodeTitle = "Nuvio episode",
        released = released,
        releaseTimestamp = releaseTimestamp
    )

    private companion object {
        val SAMPLE_JSON = """
            [
              {
                "first_aired": "2026-11-15T21:00:00.000Z",
                "episode": {
                  "season": 2,
                  "number": 5,
                  "title": "The Test",
                  "ids": { "trakt": 42, "tvdb": 7, "imdb": "tt999", "tmdb": 99 }
                },
                "show": {
                  "title": "Sample Show",
                  "year": 2025,
                  "ids": { "trakt": 1, "slug": "sample-show", "tvdb": 2, "imdb": "tt1234567", "tmdb": 555 }
                }
              },
              {
                "first_aired": "2026-11-16T03:00:00.000Z",
                "episode": { "season": 1, "number": 1, "title": "Pilot" },
                "show": {
                  "title": "TMDB Only",
                  "year": 2024,
                  "ids": { "trakt": 9, "slug": "tmdb-only", "tvdb": null, "imdb": null, "tmdb": 777 }
                }
              }
            ]
        """.trimIndent()
    }
}
