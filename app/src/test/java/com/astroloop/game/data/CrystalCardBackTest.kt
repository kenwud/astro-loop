package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * The Time Crystal's card back: AGAIN, repeated until the tile is full.
 *
 * It used to be a grid — each row offset one letter, so columns read AGAIN downward and every
 * diagonal collapsed to a band of one letter — drawn character by character on a fixed pitch to
 * survive Exo 2 being proportional. Owner's call on 2026-08-10: the alignment did not read as
 * intended on a device, so the back is now a plain wall of text and the pitch machinery is gone.
 *
 * AGAIN is the word the crystal rewind puts on screen at every death, so the card says back what
 * the loop says. There is no speaker and no addressee here: it is a recording, not a character, so
 * no fourth-wall reaction is owed to it.
 */
class CrystalCardBackTest {

    @Test
    fun `a row is the word repeated`() {
        val row = CrystalCardBack.row(15)

        assertEquals("AGAINAGAINAGAIN", row)
    }

    @Test
    fun `a row is cut to exactly the length asked for`() {
        assertEquals(7, CrystalCardBack.row(7).length)
        assertEquals("AGAINAG", CrystalCardBack.row(7))
    }

    @Test
    fun `a row shorter than the word is still the start of it`() {
        assertEquals("AG", CrystalCardBack.row(2))
    }

    @Test
    fun `a row of no length is empty rather than a crash`() {
        assertEquals("", CrystalCardBack.row(0))
        assertEquals("", CrystalCardBack.row(-3))
    }

    @Test
    fun `a row is plain ASCII`() {
        // Exo 2's coverage for combining marks and block elements is unverified, and the tile
        // measures pixel widths — a missing glyph either tofus or mis-measures.
        assertTrue(CrystalCardBack.row(40).all { it in 'A'..'Z' })
    }

    @Test
    fun `the word is the one the death screen uses`() {
        assertEquals("AGAIN", CrystalCardBack.WORD)
    }
}
