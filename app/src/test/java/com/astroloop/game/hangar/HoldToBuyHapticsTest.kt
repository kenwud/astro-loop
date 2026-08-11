package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * When the tile hums under the finger.
 *
 * The fill is the only teacher for the hold gesture — a player updating from 1.1 never sees the
 * tutorial line — so the haptic is tied to exactly the same boundary the fill is: silence while a
 * release would still count as a tap, a hum the instant it would not. Feeling the hold arm is the
 * same lesson as seeing it, delivered to a thumb that is covering the tile it is learning about.
 *
 * The purchase pulse itself is a one-shot fired off [HoldToBuy.advance]'s single true, which the
 * existing HoldToBuyTest already pins to one per gesture.
 */
class HoldToBuyHapticsTest {

    @Test
    fun `an untouched tile does not hum`() {
        assertFalse(HoldToBuy().isFilling)
    }

    @Test
    fun `nothing hums while a release would still be a tap`() {
        val hold = HoldToBuy()
        hold.start(0)

        hold.advance(HoldToBuy.TAP_SECONDS - 0.01f)

        assertFalse("a tap must not buzz — it is not buying anything", hold.isFilling)
    }

    @Test
    fun `the hum starts with the fill`() {
        val hold = HoldToBuy()
        hold.start(0)

        hold.advance(HoldToBuy.TAP_SECONDS + 0.01f)

        assertTrue("the hum and the fill share a boundary", hold.isFilling)
        assertTrue("and the fill really is showing by then", hold.progress > 0f)
    }

    @Test
    fun `the hum stops the moment the purchase completes`() {
        val hold = HoldToBuy(thresholdSeconds = 0.5f)
        hold.start(0)

        assertTrue("this advance is the purchase", hold.advance(0.6f))

        assertFalse("the pulse takes over from the hum", hold.isFilling)
    }

    @Test
    fun `the hum stops on an abandoned hold`() {
        val hold = HoldToBuy()
        hold.start(0)
        hold.advance(HoldToBuy.TAP_SECONDS + 0.1f)
        assertTrue(hold.isFilling)

        hold.cancel()

        assertFalse("letting go must silence the tile", hold.isFilling)
    }
}
