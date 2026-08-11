package com.astroloop.game.render

import org.junit.Assert.*
import org.junit.Test

/**
 * Greedy wrapping packs the first line full and leaves a stub behind — "Everything you fire hits"
 * over "harder.", "Gain a 5th weapon" over "slot". Both lines are legal; the card just looks
 * broken. When a description needs a second line, the break should fall near the middle.
 *
 * **Only the two-line case is balanced.** Anything that wraps to three or more keeps the greedy
 * result, so line *counts* never change — the store card backs are laid out against a vertical
 * budget that counts them, and a rebalance that added a line would blow it.
 *
 * Measurement is injected so this runs without a Paint: every character is 10px wide.
 */
class TextWrapTest {

    private val measure: (String) -> Float = { it.length * 10f }

    private fun wrap(text: String, maxWidth: Float) = TextWrap.wrap(text, maxWidth, measure)

    @Test
    fun `text that fits is left alone`() {
        assertEquals(listOf("Gain a 5th weapon slot"), wrap("Gain a 5th weapon slot", 500f))
    }

    @Test
    fun `Hot Rounds does not leave one word stranded`() {
        // Greedy gives "Everything you fire hits" (240) / "harder." (70).
        assertEquals(
            listOf("Everything you", "fire hits harder."),
            wrap("Everything you fire hits harder.", 250f)
        )
    }

    @Test
    fun `Weapon Expansion splits down the middle`() {
        // Greedy gives "Gain a 5th weapon" (170) / "slot" (40).
        assertEquals(
            listOf("Gain a 5th", "weapon slot"),
            wrap("Gain a 5th weapon slot", 200f)
        )
    }

    @Test
    fun `a balanced split still fits inside the width`() {
        val lines = wrap("Everything you fire hits harder.", 250f)
        assertEquals(2, lines.size)
        for (line in lines) {
            assertTrue("'$line' must fit the column", measure(line) <= 250f)
        }
    }

    @Test
    fun `three or more lines keep the greedy layout`() {
        val text = "Raises your maximum shield and it regenerates after a short pause in combat."
        val balanced = wrap(text, 200f)
        val greedy = TextWrap.greedy(text, 200f, measure)
        assertTrue("this text must need three lines for the guard to mean anything", greedy.size >= 3)
        assertEquals(greedy, balanced)
    }

    @Test
    fun `a word wider than the column gets its own line rather than vanishing`() {
        val lines = wrap("tiny supercalifragilistic", 100f)
        assertTrue(lines.contains("supercalifragilistic"))
        assertEquals("tiny supercalifragilistic", lines.joinToString(" "))
    }

    @Test
    fun `an explicit newline is honoured`() {
        assertEquals(listOf("+1 weapon", "-1 passive"), wrap("+1 weapon\n-1 passive", 500f))
    }

    @Test
    fun `clamp leaves a short list alone`() {
        val lines = listOf("one", "two")
        assertEquals(lines, TextWrap.clamp(lines, 2))
        assertEquals(lines, TextWrap.clamp(lines, 3))
    }

    @Test
    fun `clamp marks the cut so an overflow is visible`() {
        // Silent truncation is what made two weapon descriptions read as badly written copy.
        assertEquals(listOf("one", "two…"), TextWrap.clamp(listOf("one", "two", "three"), 2))
    }

    @Test
    fun `an explicit newline whose half is too wide still wraps`() {
        // The crew card's passive descriptions place their own breaks. Each half is wrapped in
        // turn, so a half too wide for the cell becomes two lines and the description three —
        // which is why the caller clamps to the rows it actually has.
        val lines = wrap("More upgrades drop\nall picked randomly", 100f)
        assertTrue("the second half does not fit 100px and must break", lines.size > 2)
        assertEquals(2, TextWrap.clamp(lines, 2).size)
    }

    @Test
    fun `no words are lost or reordered`() {
        val text = "Everything you fire hits harder."
        assertEquals(text, wrap(text, 250f).joinToString(" "))
    }
}
