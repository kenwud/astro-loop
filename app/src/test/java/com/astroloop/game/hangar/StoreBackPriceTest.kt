package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * The card back carries the price too, in the front's position.
 *
 * Owner, 2026-08-11. The back deliberately omitted it — "the front already carries it in gold on
 * every tile" — but a player reading the back to decide whether to buy had to flip it over to find
 * out what buying cost. Both faces show it now, at the same baseline, so it does not appear to
 * move when the card turns.
 *
 * That baseline is what makes this a layout change rather than one more `drawText`. The back's
 * content used to be allowed down to 0.94 of the tile, and the price sits at 0.88, so the two
 * would have collided on exactly the cards S9 flags as the tightest.
 */
class StoreBackPriceTest {

    /** ~340px is a 1080-wide phone: three columns inside the content width. */
    private val phoneTile = 342f

    @Test
    fun `both faces put the price on the same baseline`() {
        assertEquals(
            "the price must not appear to jump when the card turns",
            0.88f, StoreBackLayout.PRICE_BASELINE, 0.0001f
        )
    }

    @Test
    fun `the content stops clear of the price row`() {
        // The price is set in frontBody type; its glyphs rise about 0.7 of that above the
        // baseline, so the row starts near 0.88 - 0.7 x 0.16 = 0.768 of the tile. Content has to
        // finish above that, with room for its own descenders.
        val priceCapTop = StoreBackLayout.PRICE_BASELINE - 0.7f * 0.16f
        assertTrue(
            "content may run to ${StoreBackLayout.BOTTOM_LIMIT} but the price starts at $priceCapTop",
            StoreBackLayout.BOTTOM_LIMIT < priceCapTop
        )
    }

    @Test
    fun `the worst card still fits on a phone without touching the price`() {
        // Haul Line below max: two effect lines, two next lines, a two-line description.
        val plan = StoreBackLayout.plan(phoneTile, effectLines = 2, nextLines = 2, detailLines = 2)
        val bottom = StoreBackLayout.lastBaseline(phoneTile, plan, 2, 2, 2)

        assertTrue(
            "last baseline $bottom must clear ${phoneTile * StoreBackLayout.BOTTOM_LIMIT}",
            bottom <= phoneTile * StoreBackLayout.BOTTOM_LIMIT
        )
    }

    @Test
    fun `a phone keeps the next-level block, at tighter leading`() {
        // The squeeze is paid for out of leading first, which is the order StoreBackLayout
        // documents. Losing the next block on an ordinary phone would be too high a price.
        val plan = StoreBackLayout.plan(phoneTile, effectLines = 2, nextLines = 2, detailLines = 2)
        assertTrue("the next-level figures survive on a phone", plan.showNext)
    }

    @Test
    fun `a cramped tile still drops the next block rather than running into the price`() {
        val small = 240f
        val plan = StoreBackLayout.plan(small, effectLines = 2, nextLines = 2, detailLines = 3)
        assertFalse("something has to give on a small tile", plan.showNext)
    }

    @Test
    fun `a tile below the floor cannot carry both, and the floor says so`() {
        // Recorded rather than hidden. frontBody caps at 22px, so a small tile carries full-size
        // lines in a fraction of the height: at 180px the worst card needs ~106px of content and
        // has 83px above the price. The narrowest real phone tile is 222px, so hardware clears it
        // — but only the narrowest real screen to hand can confirm that.
        val below = 180f
        val plan = StoreBackLayout.plan(below, effectLines = 2, nextLines = 2, detailLines = 3)
        val bottom = StoreBackLayout.lastBaseline(below, plan, 2, 2, 3)

        assertTrue("180px is below the documented floor", below < StoreBackLayout.MIN_TILE_FOR_PRICE)
        assertTrue(
            "and this is why: the content overruns the limit at $bottom",
            bottom > below * StoreBackLayout.BOTTOM_LIMIT
        )
        assertTrue(
            "the floor must sit above every tile that overruns",
            StoreBackLayout.MIN_TILE_FOR_PRICE > below
        )
    }
}
