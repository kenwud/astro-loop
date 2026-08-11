package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * Which gesture a moving finger has committed to, and whether a hold survives it.
 *
 * Owner, 2026-08-11: buying an upgrade asks for too much precision. Three things were wrong, and
 * all three came from one line that treated every kind of movement the same way.
 *
 * **The threshold was raw pixels.** 15px, not density units — about 5dp on a 3x phone. Android's
 * own `ViewConfiguration.getScaledTouchSlop()` is 8dp (~24px there), and Apple's
 * `UILongPressGestureRecognizer.allowableMovement` defaults to 10 points (~30px). The hold was
 * being cancelled at roughly half the drift iOS tolerates, and under what Android needs to call
 * something a drag at all. It now comes from the platform, so it scales with the screen.
 *
 * **Vertical drift cancelled a purchase for nothing.** The store page only scrolls sideways — ship
 * drag is a shipyard gesture — so a thumb rolling downward on a tile was cancelling against a
 * gesture that does not exist there. A page swipe now needs *horizontal* movement.
 *
 * **A long press should get a bigger budget than a scroll**, on both platforms, because a finger
 * held still for a second drifts more than one about to flick. Here they were the same number.
 * The hold's budget is now the tile itself: drift as far as you like inside the control you are
 * pressing, which is what both platforms do and what a player expects.
 */
class HangarGesturesTest {

    /** Stand-in for the platform's density-scaled slop; the real one comes from ViewConfiguration. */
    private val slop = 24f

    // ── Page swipe ──────────────────────────────────────────────────────────

    @Test
    fun `horizontal movement past the slop starts a page swipe`() {
        assertTrue(HangarGestures.startsPageSwipe(totalDx = slop + 1f, totalDy = 0f, slop = slop))
    }

    @Test
    fun `movement under the slop commits to nothing`() {
        assertFalse(HangarGestures.startsPageSwipe(totalDx = slop - 1f, totalDy = 0f, slop = slop))
    }

    @Test
    fun `vertical drift alone never starts a page swipe`() {
        // The store page has nothing to scroll vertically, so this must not cancel a purchase.
        assertFalse(HangarGestures.startsPageSwipe(totalDx = 2f, totalDy = 200f, slop = slop))
    }

    @Test
    fun `a diagonal drag that clears the slop sideways is still a page swipe`() {
        assertTrue(HangarGestures.startsPageSwipe(totalDx = slop + 5f, totalDy = slop + 40f, slop = slop))
    }

    // ── Ship drag ───────────────────────────────────────────────────────────

    @Test
    fun `a dominant vertical drag on the ship starts a ship drag`() {
        assertTrue(HangarGestures.startsShipDrag(
            totalDx = 5f, totalDy = slop + 1f, slop = slop, shipDragPossible = true))
    }

    @Test
    fun `vertical movement off the ship starts no ship drag`() {
        assertFalse(HangarGestures.startsShipDrag(
            totalDx = 5f, totalDy = slop + 1f, slop = slop, shipDragPossible = false))
    }

    @Test
    fun `a mostly-horizontal drag on the ship is a page swipe, not a ship drag`() {
        assertFalse(HangarGestures.startsShipDrag(
            totalDx = 100f, totalDy = slop + 1f, slop = slop, shipDragPossible = true))
    }

    // ── The hold's budget is the tile ───────────────────────────────────────

    @Test
    fun `a hold survives any drift that stays on the tile`() {
        // A tile is a third of the screen; the old 15px budget used a fraction of it.
        assertTrue(HangarGestures.holdSurvivesDrift(
            x = 190f, y = 10f, left = 0f, top = 0f, right = 200f, bottom = 200f))
        assertTrue(HangarGestures.holdSurvivesDrift(
            x = 100f, y = 199f, left = 0f, top = 0f, right = 200f, bottom = 200f))
    }

    @Test
    fun `a hold ends when the finger leaves the tile it started on`() {
        assertFalse(HangarGestures.holdSurvivesDrift(
            x = 201f, y = 100f, left = 0f, top = 0f, right = 200f, bottom = 200f))
        assertFalse(HangarGestures.holdSurvivesDrift(
            x = 100f, y = -1f, left = 0f, top = 0f, right = 200f, bottom = 200f))
    }

    @Test
    fun `the hold tolerates far more drift than the swipe slop`() {
        // The point of the change: pressing a button should not be a test of steadiness.
        val tolerated = 100f
        assertTrue("a tile is much wider than the swipe slop", tolerated > slop)
        assertTrue(HangarGestures.holdSurvivesDrift(
            x = tolerated, y = tolerated, left = 0f, top = 0f, right = 200f, bottom = 200f))
    }
}
