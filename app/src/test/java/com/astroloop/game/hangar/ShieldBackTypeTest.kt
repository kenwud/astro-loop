package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * The Emergency Shield's card back reads at the same size as every other back.
 *
 * Owner, 2026-08-11: its text was too small. It was — this face set its own type at `0.10f` of the
 * tile capped at 14px, while the eight upgrade backs used `StoreTextSizes.backDetail`, `0.16f`
 * capped at 22px. It was the one card in the grid you had to lean in for, and it had drifted
 * because it is drawn by a separate function with its own hard-coded fractions rather than through
 * the shared scale.
 *
 * Growing the type costs width, and S10 already had this back within about a line of clipping, so
 * the copy came down with it. These tests hold both halves of that trade.
 */
class ShieldBackTypeTest {

    private val body = StorePageRenderer.SHIELD_BACK_BODY

    /** What this face used to set its own body at, before it joined the shared scale. */
    private fun retiredShieldBodySize(tile: Float) = (tile * 0.10f).coerceIn(9f, 14f)

    @Test
    fun `the body is bigger than the size it used to set for itself`() {
        // At every tile size, not just a convenient one — the old fraction and the shared one have
        // different caps (14px against 22px), so they could in principle have converged somewhere.
        for (tile in listOf(180f, 222f, 240f, 303f, 342f, 600f)) {
            assertTrue(
                "at tile $tile the shield body is ${StoreTextSizes.backDetail(tile)}, " +
                    "no better than the ${retiredShieldBodySize(tile)} it replaced",
                StoreTextSizes.backDetail(tile) > retiredShieldBodySize(tile)
            )
        }
    }

    @Test
    fun `it reads at least half again as large on a real phone tile`() {
        // ~342px is a 1080-wide phone. The complaint was legibility, so the fix has to be a
        // difference you can see rather than a nudge.
        val tile = 342f
        assertTrue(
            "${StoreTextSizes.backDetail(tile)} against ${retiredShieldBodySize(tile)}",
            StoreTextSizes.backDetail(tile) >= retiredShieldBodySize(tile) * 1.5f
        )
    }

    @Test
    fun `the copy fits the space the bigger type leaves`() {
        val chars = body.sumOf { it.length }
        assertTrue(
            "$chars characters, over the ${StorePageRenderer.SHIELD_BACK_BUDGET} the tile holds " +
                "at this size: $body",
            chars <= StorePageRenderer.SHIELD_BACK_BUDGET
        )
    }

    @Test
    fun `it still corrects the front's promise`() {
        // The front says "Survive a lethal hit", which reads as fight on. The code calls
        // startRetreat(): the run ends and you leave alive. If the back stops saying so, the
        // card is back to misleading people about what they bought.
        val text = body.joinToString(" ")
        assertTrue("the back has to say the run ends", text.contains("withdraw"))
        assertTrue("and that you survive it", text.contains("alive"))
    }

    @Test
    fun `it keeps the blank line between its two paragraphs`() {
        assertEquals("two paragraphs and a spacer", 3, body.size)
        assertTrue("the middle entry is the spacer", body[1].isEmpty())
        assertTrue("both paragraphs say something", body[0].isNotBlank() && body[2].isNotBlank())
    }

    @Test
    fun `no em dashes here either`() {
        // Same rule as all the other player-facing copy.
        assertFalse(body.any { it.contains('—') })
    }
}
