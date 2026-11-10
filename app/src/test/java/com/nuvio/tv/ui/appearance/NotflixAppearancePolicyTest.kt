package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AppAppearance
import org.junit.Assert.*
import org.junit.Test

class NotflixAppearancePolicyTest {
    @Test fun onlyCompletedFullDebugLaboratoryCanExposeNotflix() {
        assertTrue(AppearancePolicy.laboratoryAvailable("full", "debug", "com.nuviodebug.com", true))
        for (flavor in listOf("full", "playstore")) for (type in listOf("debug", "release", "benchmark")) {
            if (flavor == "full" && type == "debug") continue
            assertFalse(AppearancePolicy.laboratoryAvailable(flavor, type, "com.nuviodebug.com", true))
        }
        assertFalse(AppearancePolicy.laboratoryAvailable("full", "debug", "com.nuvio.appdebug", true))
        assertFalse(AppearancePolicy.laboratoryAvailable("full", "debug", "com.nuviodebug.com", false))
    }
    @Test fun publicNotflixReleaseExposesNotflixButNuvioPackagesNever() {
        assertTrue(AppearancePolicy.laboratoryAvailable("full", "release", "com.notflix.tv", true))
        assertFalse(AppearancePolicy.laboratoryAvailable("full", "release", "com.nuvio.tv", true))
        assertFalse(AppearancePolicy.laboratoryAvailable("playstore", "release", "com.notflix.tv", true))
        assertFalse(AppearancePolicy.laboratoryAvailable("full", "benchmark", "com.notflix.tv.debug", true))
        assertFalse(AppearancePolicy.laboratoryAvailable("full", "release", "com.notflix.tv", false))
    }
    @Test fun unavailableRendererKeepsRequestedChoiceButUsesNuvio() {
        assertEquals(AppAppearance.NUVIO, AppearancePolicy.effective(AppAppearance.NOTFLIX, false))
        assertEquals(AppAppearance.NOTFLIX, AppearancePolicy.effective(AppAppearance.NOTFLIX, true))
    }
}
