package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.local.NotflixIntegrationConfig
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.domain.model.ContentType
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M35: without a user key the TMDB-dependent features are skipped, not called with an empty key. */
class TmdbMetadataServiceGatingTest {
    @Test
    fun `enrichment is skipped when no tmdb key is configured`() = runTest {
        val api = mockk<TmdbApi>(relaxed = true)
        val service = TmdbMetadataService(
            api,
            Dispatchers.Unconfined,
            NotflixIntegrationConfig { NotflixIntegrationCredentials() }
        )

        assertNull(service.fetchEnrichment("10", ContentType.MOVIE))
        assertTrue(service.fetchMoreLikeThis("10", ContentType.MOVIE).isEmpty())
        coVerify(exactly = 0) { api.getMovieDetails(any(), any(), any()) }
    }
}
