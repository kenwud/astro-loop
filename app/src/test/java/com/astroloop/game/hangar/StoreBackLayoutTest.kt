package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * The card back has to fit inside its tile at every screen size.
 *
 * Once the copy was raised to the front's size (owner rule, 2026-08-09) the type stopped shrinking
 * with the tile — `frontBody` floors at 13px — so a narrow tile wraps the description into more
 * lines while each line stays just as tall. Measured before this existed: at a 303px tile the worst
 * card (Haul Line, below max, two effect lines and two next-deltas) ended at 0.93 of the tile, but
 * at 240px it ended at **1.10** — off the bottom.
 *
 * So the layout gives instead of the type: first the leading tightens, then the next-level block is
 * dropped. Current values and the description are what survive, because they are what the card is
 * for — "what do I have" and "what does it do". The next-level figure is the one the front already
 * hints at with its cost row.
 */
class StoreBackLayoutTest {

    /**
     * 240 is the narrowest tile real hardware produces with room to spare (a 360dp phone at 2x
     * gives 222px); 303 is the design width; 600 is a large-screen content column.
     *
     * 180 and 200 were in this list until the price row moved onto the back on 2026-08-11. They
     * are below [StoreBackLayout.MIN_TILE_FOR_PRICE] — see the note there — and the arithmetic
     * that excludes them is asserted in `a tile below the floor cannot carry both` rather than
     * quietly dropped.
     */
    private val tileSizes = listOf(240f, 303f, 400f, 600f)

    /** The worst real card: two effect lines, two next-deltas, and the longest description. */
    private val worstCase = Triple(2, 2, 3)

    @Test
    fun `the worst card fits inside every tile size`() {
        for (tileSize in tileSizes) {
            val (effects, next, detail) = worstCase
            val plan = StoreBackLayout.plan(tileSize, effects, next, detail)
            val bottom = StoreBackLayout.lastBaseline(tileSize, plan, effects, next, detail)

            assertTrue(
                "at tileSize $tileSize the back ends at ${bottom / tileSize} of the tile " +
                    "(step ${plan.lineStep}, next block ${plan.showNext})",
                bottom <= tileSize * StoreBackLayout.BOTTOM_LIMIT
            )
        }
    }

    @Test
    fun `a roomy tile keeps the next-level block`() {
        val plan = StoreBackLayout.plan(400f, effectLines = 2, nextLines = 2, detailLines = 2)

        assertTrue("there is room here, so nothing should be dropped", plan.showNext)
    }

    @Test
    fun `a cramped tile drops the next-level block rather than overflowing`() {
        // Six wrapped description lines is what a narrow tile does to the longest description.
        val plan = StoreBackLayout.plan(180f, effectLines = 2, nextLines = 2, detailLines = 6)

        assertFalse("something has to give, and it is the next-level figure", plan.showNext)
    }

    @Test
    fun `leading tightens before anything is dropped`() {
        // The band moved down when the price row took the bottom of the tile: 400 is now where
        // the roomy leading overflows but the tight one still carries everything.
        val roomy = StoreBackLayout.plan(600f, effectLines = 2, nextLines = 2, detailLines = 2)
        val tight = StoreBackLayout.plan(400f, effectLines = 2, nextLines = 2, detailLines = 3)

        assertTrue("the tight case should still carry its next block", tight.showNext)
        assertTrue(
            "and should have bought that by tightening the leading first",
            tight.lineStep / StoreTextSizes.backEffect(400f) <
                roomy.lineStep / StoreTextSizes.backEffect(600f)
        )
    }

    @Test
    fun `leading never drops below the height of the text it carries`() {
        for (tileSize in tileSizes) {
            val plan = StoreBackLayout.plan(tileSize, 2, 2, 6)
            assertTrue(
                "at tileSize $tileSize the step ${plan.lineStep} is tighter than the " +
                    "${StoreTextSizes.backEffect(tileSize)}px text it carries",
                plan.lineStep > StoreTextSizes.backEffect(tileSize)
            )
        }
    }

    @Test
    fun `a maxed card has no next block to drop and stays roomy`() {
        // At level 5 the renderer never asks for the next block, so nextLines is 0.
        val plan = StoreBackLayout.plan(240f, effectLines = 2, nextLines = 0, detailLines = 3)
        val bottom = StoreBackLayout.lastBaseline(240f, plan, 2, 0, 3)

        assertTrue(bottom <= 240f * StoreBackLayout.BOTTOM_LIMIT)
    }
}
