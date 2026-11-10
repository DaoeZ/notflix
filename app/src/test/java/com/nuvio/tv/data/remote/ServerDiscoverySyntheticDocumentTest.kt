package com.nuvio.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** M33: discovery parsing checked with a synthetic document; never the real api document. */
class ServerDiscoverySyntheticDocumentTest {
    private val discoveryUrl = "https://example.invalid/.well-known/nuvio"

    private fun document(publishableKey: String = "fixture-key") = """
        {
          "version": 1,
          "service": "nuvio",
          "self_hosted": true,
          "backend_url": "https://example.invalid",
          "publishable_key": "$publishableKey",
          "capabilities": {
            "email_password_auth": false,
            "tv_login": true,
            "watch_party": true
          }
        }
    """.trimIndent()

    @Test
    fun parseReturnsSyntheticDiscoveryValues() {
        val configuration = ServerDiscoveryPolicy.parse(discoveryUrl, document())

        assertEquals("https://example.invalid", configuration.backendUrl)
        assertEquals("fixture-key", configuration.publishableKey)
        assertTrue(configuration.isCustom)
        assertEquals(discoveryUrl, configuration.discoveryUrl)
        assertFalse(configuration.capabilities.emailPasswordAuth)
        assertTrue(configuration.capabilities.tvLogin)
        assertTrue(configuration.capabilities.watchParty)
        assertEquals("https://example.invalid/tv-login", configuration.tvLoginWebBaseUrl)
        assertEquals("https://example.invalid/link", configuration.deviceLoginWebBaseUrl)
        assertEquals("https://example.invalid/storage/v1/object/public/avatars", configuration.avatarPublicBaseUrl)
    }

    @Test
    fun parseRejectsBlankPublishableKey() {
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(discoveryUrl, document(publishableKey = "   "))
        }

        assertEquals(ServerDiscoveryFailure.MISSING_CONFIGURATION, error.failure)
    }
}
