package com.nuvio.tv.ui.screens.profile

import com.nuvio.tv.core.profile.AgeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M52: the pure parts of the Notflix profile menus (no Compose runtime needed). */
class NotflixProfileDialogsTest {

    @Test
    fun menuAlwaysStartsWithEdit() {
        val actions = notflixProfileMenuActions(
            isPrimary = false,
            pinEnabled = false,
            canCopySettings = true
        )
        assertEquals(NotflixProfileMenuAction.Edit, actions.first())
    }

    @Test
    fun primaryProfileCannotBeDeleted() {
        val actions = notflixProfileMenuActions(
            isPrimary = true,
            pinEnabled = true,
            canCopySettings = true
        )
        assertFalse(actions.contains(NotflixProfileMenuAction.Delete))
    }

    @Test
    fun deleteIsAlwaysTheLastRowForSecondaryProfiles() {
        val actions = notflixProfileMenuActions(
            isPrimary = false,
            pinEnabled = true,
            canCopySettings = true
        )
        assertEquals(NotflixProfileMenuAction.Delete, actions.last())
        assertEquals(
            listOf(
                NotflixProfileMenuAction.Edit,
                NotflixProfileMenuAction.CopySettings,
                NotflixProfileMenuAction.ChangePin,
                NotflixProfileMenuAction.RemovePin,
                NotflixProfileMenuAction.Delete
            ),
            actions
        )
    }

    @Test
    fun pinRowsFollowTheCurrentPinState() {
        val withoutPin = notflixProfileMenuActions(
            isPrimary = false,
            pinEnabled = false,
            canCopySettings = false
        )
        assertTrue(withoutPin.contains(NotflixProfileMenuAction.SetPin))
        assertFalse(withoutPin.contains(NotflixProfileMenuAction.ChangePin))
        assertFalse(withoutPin.contains(NotflixProfileMenuAction.RemovePin))

        val withPin = notflixProfileMenuActions(
            isPrimary = false,
            pinEnabled = true,
            canCopySettings = false
        )
        assertFalse(withPin.contains(NotflixProfileMenuAction.SetPin))
        assertTrue(withPin.contains(NotflixProfileMenuAction.ChangePin))
        assertTrue(withPin.contains(NotflixProfileMenuAction.RemovePin))
    }

    @Test
    fun copySettingsOnlyAppearsWhenThereIsAnotherProfile() {
        val single = notflixProfileMenuActions(
            isPrimary = false,
            pinEnabled = false,
            canCopySettings = false
        )
        assertFalse(single.contains(NotflixProfileMenuAction.CopySettings))

        val multiple = notflixProfileMenuActions(
            isPrimary = false,
            pinEnabled = false,
            canCopySettings = true
        )
        assertTrue(multiple.contains(NotflixProfileMenuAction.CopySettings))
    }

    @Test
    fun deleteConfirmationOffersDeleteThenCancel() {
        assertEquals(
            listOf(NotflixDeleteConfirmAction.Delete, NotflixDeleteConfirmAction.Cancel),
            notflixDeleteConfirmActions()
        )
    }

    @Test
    fun deleteConfirmationStartsOnCancel() {
        assertEquals(
            NotflixDeleteConfirmAction.Cancel,
            notflixDeleteConfirmActions()[notflixDeleteConfirmInitialFocusIndex()]
        )
        assertTrue(notflixDeleteConfirmInitialFocusIndex() >= 0)
    }

    @Test
    fun kidsToggleSetsAndClearsTheAllAgesPreset() {
        assertEquals(AgeLevel.TP, NOTFLIX_KIDS_AGE_LEVEL)
        assertEquals(NOTFLIX_KIDS_AGE_LEVEL, notflixAgeLimitAfterKidsToggle(kids = true))
        assertNull(notflixAgeLimitAfterKidsToggle(kids = false))
    }

    @Test
    fun onlyTheKidsPresetCountsAsKids() {
        assertTrue(notflixIsKidsAgeLimit(NOTFLIX_KIDS_AGE_LEVEL))
        assertFalse(notflixIsKidsAgeLimit(AgeLevel.A7))
        assertFalse(notflixIsKidsAgeLimit(AgeLevel.A18))
        assertFalse(notflixIsKidsAgeLimit(null))
    }

    // Corrección obligatoria 09-11: un límite no se relaja ni se cambia sin el PIN del principal.

    @Test
    fun relaxingAKidsLimitNeedsThePrimaryPinWhenThereIsOne() {
        assertTrue(
            ageLimitChangeNeedsPrimaryPin(
                primaryHasPin = true,
                currentLevel = NOTFLIX_KIDS_AGE_LEVEL,
                newLevel = null
            )
        )
    }

    @Test
    fun everyRealAgeLimitChangeNeedsThePrimaryPin() {
        assertTrue(ageLimitChangeNeedsPrimaryPin(primaryHasPin = true, currentLevel = AgeLevel.A18, newLevel = AgeLevel.A7))
        assertTrue(ageLimitChangeNeedsPrimaryPin(primaryHasPin = true, currentLevel = null, newLevel = AgeLevel.TP))
        assertTrue(ageLimitChangeNeedsPrimaryPin(primaryHasPin = true, currentLevel = AgeLevel.A7, newLevel = null))
    }

    @Test
    fun withoutPrimaryPinTheDecisionAsksForNothing() {
        assertFalse(ageLimitChangeNeedsPrimaryPin(primaryHasPin = false, currentLevel = AgeLevel.TP, newLevel = null))
        assertFalse(ageLimitChangeNeedsPrimaryPin(primaryHasPin = false, currentLevel = null, newLevel = AgeLevel.A7))
    }

    @Test
    fun keepingTheSameLimitNeverAsksForAPin() {
        assertFalse(ageLimitChangeNeedsPrimaryPin(primaryHasPin = true, currentLevel = AgeLevel.A12, newLevel = AgeLevel.A12))
        assertFalse(ageLimitChangeNeedsPrimaryPin(primaryHasPin = true, currentLevel = null, newLevel = null))
    }
}
