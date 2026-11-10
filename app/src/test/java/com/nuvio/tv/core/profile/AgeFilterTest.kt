package com.nuvio.tv.core.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeFilterTest {
    @Test
    fun `no limit lets everything through`() {
        AgeLevel.entries.forEach { level ->
            assertTrue(isAllowed(null, level))
        }
        assertTrue(isAllowed(null, null))
    }

    @Test
    fun `plus twelve allows TP seven and twelve`() {
        assertTrue(isAllowed(AgeLevel.A12, AgeLevel.TP))
        assertTrue(isAllowed(AgeLevel.A12, AgeLevel.A7))
        assertTrue(isAllowed(AgeLevel.A12, AgeLevel.A12))
        assertFalse(isAllowed(AgeLevel.A12, AgeLevel.A16))
        assertFalse(isAllowed(AgeLevel.A12, AgeLevel.A18))
        assertFalse(isAllowed(AgeLevel.A12, AgeLevel.X))
    }

    @Test
    fun `missing classification is hidden with a limit`() {
        AgeLevel.entries.forEach { limit ->
            assertFalse(isAllowed(limit, null))
        }
    }

    @Test
    fun `tp only allows tp`() {
        assertTrue(isAllowed(AgeLevel.TP, AgeLevel.TP))
        assertFalse(isAllowed(AgeLevel.TP, AgeLevel.A7))
    }

    @Test
    fun `pin is only requested when the primary has one and the action changes the limit or leaves a limited profile`() {
        assertFalse(requiresPrimaryPin(primaryHasPin = false, changingAgeLimit = true, leavingLimitedProfile = false))
        assertFalse(requiresPrimaryPin(primaryHasPin = false, changingAgeLimit = false, leavingLimitedProfile = true))
        assertFalse(requiresPrimaryPin(primaryHasPin = false, changingAgeLimit = false, leavingLimitedProfile = false))
        assertTrue(requiresPrimaryPin(primaryHasPin = true, changingAgeLimit = true, leavingLimitedProfile = false))
        assertTrue(requiresPrimaryPin(primaryHasPin = true, changingAgeLimit = false, leavingLimitedProfile = true))
        assertFalse(requiresPrimaryPin(primaryHasPin = true, changingAgeLimit = false, leavingLimitedProfile = false))
    }
}
