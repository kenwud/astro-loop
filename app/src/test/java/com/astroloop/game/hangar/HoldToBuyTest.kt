package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * Holding a store tile buys one level of it.
 *
 * The fill is not decoration: it is the only thing that teaches the gesture, and it is what makes
 * an accidental hold recoverable — releasing before the threshold spends nothing. One purchase per
 * hold, because a gesture that keeps spending while held reintroduces exactly the accidental spend
 * the fill was added to prevent.
 */
class HoldToBuyTest {

    @Test
    fun `the default hold is a full second`() {
        // Owner, 2026-08-09, after holding tiles on a device: half a second was too quick to read
        // as a deliberate act. This is the tuning knob, and the fill's sweep is derived from it.
        assertEquals(1.0f, HoldToBuy.DEFAULT_THRESHOLD_SECONDS, 0.0001f)
    }

    @Test
    fun `a default-threshold hold does not complete at half a second`() {
        val hold = HoldToBuy()
        hold.start(3)

        assertFalse("half a second used to buy; it must not any more", hold.advance(0.5f))
        assertTrue("a full second still buys", hold.advance(0.5f))
    }

    @Test
    fun `an idle hold reports no tile`() {
        val hold = HoldToBuy()

        assertFalse(hold.isActive)
        assertEquals(-1, hold.index)
        assertEquals(0f, hold.progress, 0.001f)
    }

    @Test
    fun `progress sweeps from nothing to full across the threshold`() {
        // The fill spans the tap window to the purchase, not zero to the purchase: while a release
        // would still flip the card there is deliberately nothing drawn.
        val hold = HoldToBuy()
        hold.start(3)

        val span = HoldToBuy.DEFAULT_THRESHOLD_SECONDS - HoldToBuy.TAP_SECONDS
        hold.advance(HoldToBuy.TAP_SECONDS + span / 2f)

        assertEquals("halfway through the fill's span is half the bar", 0.5f, hold.progress, 0.001f)
    }

    @Test
    fun `crossing the threshold completes exactly once`() {
        val hold = HoldToBuy(thresholdSeconds = 0.5f)
        hold.start(3)

        assertFalse("not yet", hold.advance(0.4f))
        assertTrue("crossed", hold.advance(0.2f))
        assertFalse("a held finger must not buy a second level", hold.advance(1f))
    }

    @Test
    fun `completing releases the tile`() {
        val hold = HoldToBuy(thresholdSeconds = 0.5f)
        hold.start(3)
        hold.advance(0.6f)

        assertFalse(hold.isActive)
        assertEquals(-1, hold.index)
    }

    @Test
    fun `cancelling before the threshold never completes`() {
        val hold = HoldToBuy(thresholdSeconds = 0.5f)
        hold.start(3)
        hold.advance(0.4f)

        hold.cancel()

        assertFalse(hold.isActive)
        assertEquals(0f, hold.progress, 0.001f)
        assertFalse("a cancelled hold cannot buy", hold.advance(1f))
    }

    @Test
    fun `advancing while idle does nothing`() {
        val hold = HoldToBuy()

        assertFalse(hold.advance(10f))
    }

    @Test
    fun `starting a new tile abandons the previous one`() {
        val hold = HoldToBuy(thresholdSeconds = 0.5f)
        hold.start(3)
        hold.advance(0.4f)

        hold.start(5)

        assertEquals(5, hold.index)
        assertEquals("the new tile starts from empty", 0f, hold.progress, 0.001f)
    }
}
