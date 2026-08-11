package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * A store press has three outcomes, not two.
 *
 * Owner, 2026-08-09: "I don't like how you can hold, let go and the card will still flip." Until
 * now any release under the buy threshold was a tap, so an abandoned purchase and a deliberate tap
 * were literally the same event — there was no way to start buying and change your mind.
 *
 * The thresholds come off the platform and off this game's own precedent. Android's
 * `ViewConfiguration` puts a tap at 100ms and a long press at 500ms; a natural tap releases in
 * 80-120ms. This game already widened its double-tap window from 300ms to 400ms "for comfortable
 * double-tap", so its own hand runs slightly slow. 250ms sits at roughly double a natural tap and
 * well short of a long press.
 *
 * The fill starts at the same 250ms deliberately: the moment a card stops being tappable is the
 * moment the fill appears, so the rule is visible rather than invisible — if you can see the fill,
 * letting go cancels.
 */
class StoreGestureZonesTest {

    @Test
    fun `a tap is a release inside the tap window`() {
        assertTrue(HoldToBuy.isTap(0.10f))
        assertTrue("the boundary itself still counts as a tap", HoldToBuy.isTap(HoldToBuy.TAP_SECONDS))
    }

    @Test
    fun `a release past the tap window is not a tap`() {
        assertFalse(HoldToBuy.isTap(HoldToBuy.TAP_SECONDS + 0.01f))
        assertFalse("an abandoned purchase must not flip the card", HoldToBuy.isTap(0.8f))
    }

    @Test
    fun `the tap window is around a quarter second`() {
        // Roughly double a natural tap, comfortably inside Android's 500ms long-press.
        assertEquals(0.25f, HoldToBuy.TAP_SECONDS, 0.0001f)
    }

    @Test
    fun `the fill only starts once the card has stopped being tappable`() {
        val hold = HoldToBuy()
        hold.start(0)

        hold.advance(HoldToBuy.TAP_SECONDS * 0.5f)
        assertEquals("nothing is drawn while a tap is still possible", 0f, hold.progress, 0.001f)

        hold.advance(HoldToBuy.TAP_SECONDS * 0.5f + 0.01f)
        assertTrue("past the tap window the fill has begun", hold.progress > 0f)
    }

    @Test
    fun `the fill sweeps the whole way between the tap window and the purchase`() {
        val hold = HoldToBuy()
        hold.start(0)
        hold.advance(HoldToBuy.DEFAULT_THRESHOLD_SECONDS - 0.001f)

        assertTrue("the bar should be nearly full just before it buys", hold.progress > 0.98f)
    }

    @Test
    fun `a purchase still lands at a full second`() {
        val hold = HoldToBuy()
        hold.start(0)

        assertFalse(hold.advance(0.99f))
        assertTrue(hold.advance(0.02f))
    }

    @Test
    fun `the three zones do not overlap`() {
        assertTrue(
            "the tap window has to end before the purchase does, or a tap would buy",
            HoldToBuy.TAP_SECONDS < HoldToBuy.DEFAULT_THRESHOLD_SECONDS
        )
    }
}
