package com.nuvio.tv.ui.screens.party

import com.nuvio.tv.core.party.PARTY_CODE_ALPHABET
import com.nuvio.tv.core.party.PARTY_CODE_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M51: reglas puras del teclado y de las casillas del código. El estado, la unión y la salida
 * siguen exactamente en PartyScreen y PartyRepository.
 */
class NotflixPartyTest {

    @Test
    fun keypadRowsKeepEveryCharacterInOrder() {
        val rows = partyKeypadRows()
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.length == 8 })
        assertEquals(PARTY_CODE_ALPHABET, rows.joinToString(""))
    }

    @Test
    fun keypadRowsFollowTheColumnCount() {
        assertEquals(listOf("AB", "CD"), partyKeypadRows(alphabet = "ABCD", columns = 2))
        assertTrue(partyKeypadRows(alphabet = "ABCD", columns = 0).isEmpty())
    }

    @Test
    fun codeSlotsFillFromTheLeft() {
        val filled = (0 until PARTY_CODE_LENGTH).map { partyCodeSlotFilled("AB", it) }
        assertEquals(listOf(true, true, false, false, false, false), filled)
    }

    @Test
    fun codeSlotsIgnorePositionsOutsideTheCode() {
        assertFalse(partyCodeSlotFilled("", 0))
        assertFalse(partyCodeSlotFilled("AB", -1))
        assertFalse(partyCodeSlotFilled("AB", PARTY_CODE_LENGTH))
        assertFalse(partyCodeSlotFilled("A B", 1))
        assertTrue(partyCodeSlotFilled("ABCDEF", 5))
    }
}
