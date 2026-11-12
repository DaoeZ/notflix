package com.nuvio.tv.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

/** M58: device-wide show rule for the one-shot «Novedades» notice. */
class NotflixWhatsNewTest {
    private val items = listOf("Novedad de prueba")

    @Test
    fun `new install stores the current version and shows nothing`() {
        assertEquals(
            NotflixWhatsNewAction.MARK_SEEN,
            notflixWhatsNewAction(lastSeenVersionCode = 0, currentVersionCode = 120, items = items)
        )
    }

    @Test
    fun `an update shows the list`() {
        assertEquals(
            NotflixWhatsNewAction.SHOW,
            notflixWhatsNewAction(lastSeenVersionCode = 119, currentVersionCode = 120, items = items)
        )
    }

    @Test
    fun `the same version does not show again`() {
        assertEquals(
            NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(lastSeenVersionCode = 120, currentVersionCode = 120, items = items)
        )
    }

    @Test
    fun `an empty list never shows but a new install still stores its version`() {
        assertEquals(
            NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(lastSeenVersionCode = 119, currentVersionCode = 120, items = emptyList())
        )
        assertEquals(
            NotflixWhatsNewAction.MARK_SEEN,
            notflixWhatsNewAction(lastSeenVersionCode = 0, currentVersionCode = 120, items = emptyList())
        )
    }

    @Test
    fun `a downgrade does not show an older device version`() {
        assertEquals(
            NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(lastSeenVersionCode = 121, currentVersionCode = 120, items = items)
        )
    }

    @Test
    fun `an invalid version code never shows`() {
        assertEquals(
            NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(lastSeenVersionCode = 119, currentVersionCode = 0, items = items)
        )
    }

    @Test
    fun `first device version is initialized even before Notflix can be presented`() {
        assertEquals(NotflixWhatsNewAction.MARK_SEEN,
            notflixWhatsNewAction(0, 120, items, enabled = false, canPresent = false))
    }

    @Test
    fun `an update waits for Home and a foreground window without another dialog`() {
        assertEquals(NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(119, 120, items, enabled = false))
        assertEquals(NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(119, 120, items, canPresent = false))
        assertEquals(NotflixWhatsNewAction.SHOW,
            notflixWhatsNewAction(119, 120, items))
    }

    @Test
    fun `a handled version never reopens while its asynchronous write is pending`() {
        repeat(3) {
            assertEquals(NotflixWhatsNewAction.NOTHING,
                notflixWhatsNewAction(119, 120, items, handled = true))
        }
    }

    @Test
    fun `blank release notes do not open an empty dialog`() {
        assertEquals(NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(119, 120, listOf("", " ", "\n")))
    }

    @Test
    fun `an update over a beta that never stored a version shows the notes once`() {
        // beta.1-3 had no stored version: an update over them is not a new install.
        assertEquals(NotflixWhatsNewAction.SHOW,
            notflixWhatsNewAction(0, 1074, listOf("New"), updatedInstall = true))
        assertEquals(NotflixWhatsNewAction.MARK_SEEN,
            notflixWhatsNewAction(0, 1074, listOf("New"), updatedInstall = false))
        // Off Home it waits instead of marking it seen.
        assertEquals(NotflixWhatsNewAction.NOTHING,
            notflixWhatsNewAction(0, 1074, listOf("New"), enabled = false, updatedInstall = true))
    }
}
