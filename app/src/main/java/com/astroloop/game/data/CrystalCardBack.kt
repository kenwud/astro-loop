package com.astroloop.game.data

/**
 * The Time Crystal card back — AGAIN, repeated until the tile is full.
 *
 * AGAIN is the word the crystal rewind puts on screen at every death, so the card says back what
 * the loop says. There is no speaker and no addressee here: it is a recording, not a character, so
 * no fourth-wall reaction is owed to it.
 *
 * *History:* this was a grid — every row offset one letter, so columns read AGAIN downward and the
 * diagonals collapsed into single-letter bands — which needed character-by-character drawing on a
 * fixed pitch because Exo 2 is proportional. The alignment did not earn its complexity on a real
 * screen (owner, 2026-08-10), so rows are now plain strings handed straight to `drawText`.
 */
object CrystalCardBack {

    const val WORD = "AGAIN"

    /** One row of the back: the word repeated and cut to exactly [columns] characters. */
    fun row(columns: Int): String {
        if (columns <= 0) return ""
        return WORD.repeat(columns / WORD.length + 1).substring(0, columns)
    }
}
