package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.data.local.AuthSessionNoticeDataStore
import com.nuvio.tv.data.local.NotflixIntegrationConfig
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.TraktAuthState
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktRefreshTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktTokenResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** M35.1: Trakt credentials are per-user and come from the injected store, never BuildConfig. */
class TraktAuthServiceCredentialsTest {

    private fun service(
        api: TraktApi,
        credentials: NotflixIntegrationCredentials,
        authStore: TraktAuthDataStore = mockk(relaxed = true)
    ): TraktAuthService {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "missing credentials"
        return TraktAuthService(
            context = context,
            traktApi = api,
            traktAuthDataStore = authStore,
            authSessionNoticeDataStore = mockk<AuthSessionNoticeDataStore>(relaxed = true),
            integrationConfig = NotflixIntegrationConfig { credentials }
        )
    }

    @Test
    fun `without client id the device flow is not started`() = runTest {
        val api = mockk<TraktApi>(relaxed = true)
        val service = service(api, NotflixIntegrationCredentials())

        assertFalse(service.hasRequiredCredentials())
        assertTrue(service.startDeviceAuth().isFailure)
        coVerify(exactly = 0) { api.requestDeviceCode(any()) }
    }

    @Test
    fun `client secret and id are read from the credential store when refreshing`() = runTest {
        val api = mockk<TraktApi>()
        val authStore = mockk<TraktAuthDataStore>(relaxed = true)
        coEvery { authStore.getCurrentState() } returns TraktAuthState(
            refreshToken = "stored-refresh",
            createdAt = 1L,
            expiresIn = 1
        )
        val request = slot<TraktRefreshTokenRequestDto>()
        coEvery { api.refreshToken(capture(request)) } returns Response.success(
            TraktTokenResponseDto(
                accessToken = "new-access",
                tokenType = "bearer",
                expiresIn = 3600,
                refreshToken = "new-refresh",
                createdAt = 2L
            )
        )

        val service = service(
            api,
            NotflixIntegrationCredentials(
                traktClientId = "stored-id",
                traktClientSecret = "stored-secret"
            ),
            authStore
        )

        assertTrue(service.hasRequiredCredentials())
        assertTrue(service.refreshTokenIfNeeded(force = true))
        assertEquals("stored-id", request.captured.clientId)
        assertEquals("stored-secret", request.captured.clientSecret)
    }

    @Test
    fun `a client id without secret is enough and the refresh omits the secret`() = runTest {
        val api = mockk<TraktApi>()
        val authStore = mockk<TraktAuthDataStore>(relaxed = true)
        coEvery { authStore.getCurrentState() } returns TraktAuthState(
            refreshToken = "stored-refresh",
            createdAt = 1L,
            expiresIn = 1
        )
        val request = slot<TraktRefreshTokenRequestDto>()
        coEvery { api.refreshToken(capture(request)) } returns Response.success(
            TraktTokenResponseDto(
                accessToken = "new-access",
                tokenType = "bearer",
                expiresIn = 3600,
                refreshToken = "new-refresh",
                createdAt = 2L
            )
        )

        val service = service(api, NotflixIntegrationCredentials(traktClientId = "stored-id"), authStore)

        assertTrue(service.hasRequiredCredentials())
        assertTrue(service.refreshTokenIfNeeded(force = true))
        assertEquals("stored-id", request.captured.clientId)
        assertNull(request.captured.clientSecret)
    }
}
