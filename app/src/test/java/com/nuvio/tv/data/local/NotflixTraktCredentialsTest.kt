package com.nuvio.tv.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotflixTraktCredentialsTest {
    private fun config(user: NotflixIntegrationCredentials, builtInId: String = "built-in-id", builtInSecret: String = "built-in-secret") =
        NotflixIntegrationConfig(builtInId, builtInSecret) { user }

    @Test fun withoutUserKeyTheBuiltInAppIsUsed() {
        val c = config(NotflixIntegrationCredentials())
        assertEquals("built-in-id", c.traktClientId())
        assertEquals("built-in-secret", c.traktClientSecret())
        assertTrue(c.isTraktConfigured())
    }

    @Test fun userKeyWinsAndNeverMixesWithTheBuiltInSecret() {
        val withSecret = config(NotflixIntegrationCredentials(traktClientId = " user-id ", traktClientSecret = "user-secret"))
        assertEquals("user-id", withSecret.traktClientId())
        assertEquals("user-secret", withSecret.traktClientSecret())

        val withoutSecret = config(NotflixIntegrationCredentials(traktClientId = "user-id"))
        assertEquals("user-id", withoutSecret.traktClientId())
        assertEquals("", withoutSecret.traktClientSecret())
    }

    @Test fun sessionKeepsTheAppThatIssuedItWhenTheBuildKeyChanges() {
        // Connected with the development app; the build now carries the production key.
        val c = config(NotflixIntegrationCredentials(), builtInId = "prod-id", builtInSecret = "")
        assertEquals("dev-id", c.traktClientIdFor("dev-id"))
        assertEquals("", c.traktClientSecretFor("dev-id"))
        c.setTraktSessionClientId("dev-id")
        assertEquals("dev-id", c.traktApiClientId())
        // New connections and legacy sessions (no stored ID) use the current key.
        assertEquals("prod-id", c.traktClientIdFor(null))
        c.setTraktSessionClientId(null)
        assertEquals("prod-id", c.traktApiClientId())
    }

    @Test fun secretOnlyTravelsWithItsOwnClientId() {
        val c = config(NotflixIntegrationCredentials())
        assertEquals("built-in-secret", c.traktClientSecretFor("built-in-id"))
        assertEquals("built-in-secret", c.traktClientSecretFor(null))
        assertEquals("", c.traktClientSecretFor("another-app"))
    }

    @Test fun noKeyAnywhereMeansNotConfigured() {
        val c = config(NotflixIntegrationCredentials(), builtInId = "", builtInSecret = "")
        assertEquals("", c.traktClientId())
        assertFalse(c.isTraktConfigured())
    }
}
