package com.astroloop.game.hangar

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every path that must abandon an in-flight gesture rather than let it complete: a ship drag or
 * a store hold cancelled by the OS (`ACTION_CANCEL`), by a second finger touching down
 * (`ACTION_POINTER_UP`), or by the surface being torn down mid-hold (`surfaceDestroyed`) — plus
 * the guard that keeps a hold from ever starting on a tile it cannot complete.
 *
 * Drives the real `onTouchEvent` dispatch rather than calling the handler methods directly: the
 * whole point of these fixes is what a real `MotionEvent` sequence does, and `renderScale`
 * defaults to 1f with `screenWidth`/`screenHeight` at their 0f defaults, so `roomX()` is the
 * identity (see `StoreHoldSuppressesFlipTest`'s doc comment) and no surface/draw pass is needed
 * to make `ACTION_DOWN`/`ACTION_MOVE`/`ACTION_UP`/`ACTION_CANCEL`/`ACTION_POINTER_UP` resolve
 * correctly against a manually-injected `upgradeRects` entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TouchGestureAbandonTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var view: HangarSurfaceView

    private val tileIndex = 0 // upgradeIds[0] == "health" / StoreUpgradeDefinitions.purchasableIds[0]
    private val tileRect = RectF(0f, 0f, 100f, 100f)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        persistence = PersistenceManager(context)
        persistence.resetAllProgress()
        view = HangarSurfaceView(context) { _, _ -> }
    }

    private fun dispatch(action: Int, x: Float, y: Float, eventTime: Long = 0L) {
        val event = MotionEvent.obtain(0L, eventTime, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    // --- Important 1: ACTION_CANCEL must abandon a ship drag, not just fail to complete it ---

    @Test
    fun `ACTION_CANCEL during a ship drag clears isDraggingShip`() {
        view.state.currentPage = 1 // shipyard; default ship (ship_blue, index 0) is unlocked by default

        dispatch(MotionEvent.ACTION_DOWN, 100f, 100f)
        dispatch(MotionEvent.ACTION_MOVE, 100f, 140f) // dy=40 > 15f slop, dx=0 → ship drag, not a page swipe
        assertTrue(
            "the drag must have grabbed the ship for this test to mean anything",
            view.state.isDraggingShip
        )

        dispatch(MotionEvent.ACTION_CANCEL, 100f, 140f)

        assertFalse(
            "a cancel must release the ship — the snap-back animation is gated on " +
                "!isDraggingShip, and the halo check keeps re-arming the rumble while it's true",
            view.state.isDraggingShip
        )
    }

    // --- S12: ACTION_CANCEL mid-hold must abandon, never buy ---

    @Test
    fun `ACTION_CANCEL mid-hold abandons the hold without spending yen`() {
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000
        val yenBefore = persistence.getYen()

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())
        assertEquals("the hold must have started for this test to mean anything",
            tileIndex, view.heldUpgradeIndex)

        dispatch(MotionEvent.ACTION_CANCEL, tileRect.centerX(), tileRect.centerY())

        assertEquals("a cancel must abandon the hold", -1, view.heldUpgradeIndex)
        assertEquals("a cancel must never spend yen", yenBefore, persistence.getYen())
        assertEquals("a cancel must never buy a level", 0, persistence.getUpgradeLevel("health"))
    }

    // --- Important 2: a hold must never start on a tile that cannot be bought ---

    @Test
    fun `ACTION_DOWN on a maxed tile does not start a hold`() {
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setUpgradeLevel("health", 5)
        persistence.setYen(100_000)
        view.state.actualYen = persistence.getYen()

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())

        assertEquals("a maxed tile must start no fill", -1, view.heldUpgradeIndex)
    }

    @Test
    fun `ACTION_DOWN on an unaffordable tile does not start a hold`() {
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(0)
        view.state.actualYen = 0

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())

        assertEquals("an unaffordable tile must start no fill", -1, view.heldUpgradeIndex)
    }

    @Test
    fun `ACTION_DOWN on an affordable unmaxed tile does start a hold`() {
        // Control: the gate above must not swallow the ordinary case it did not exist to fix.
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())

        assertEquals(tileIndex, view.heldUpgradeIndex)
    }

    @Test
    fun `releasing a maxed tile that never held still flips it, like any other tap`() {
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setUpgradeLevel("health", 5)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())
        dispatch(MotionEvent.ACTION_UP, tileRect.centerX(), tileRect.centerY())

        assertTrue("the release must fall through to a plain tap and flip the card",
            view.state.isStoreCardFlipped(tileIndex))
        assertEquals("a maxed tile must stay maxed", 5, persistence.getUpgradeLevel("health"))
    }

    // --- Fix 5: ACTION_POINTER_UP must cancel a hold, not silently let it complete ---

    @Test
    fun `ACTION_POINTER_UP mid-hold cancels rather than leaving the purchase to complete`() {
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())
        assertEquals("the hold must have started for this test to mean anything",
            tileIndex, view.heldUpgradeIndex)

        dispatch(MotionEvent.ACTION_POINTER_UP, tileRect.centerX(), tileRect.centerY())

        assertEquals("a second finger lifting must abandon the hold",
            -1, view.heldUpgradeIndex)
        assertEquals("no purchase may happen with only a phantom finger held down",
            0, persistence.getUpgradeLevel("health"))
    }

    @Test
    fun `the release after an abandoned drag is not a tap`() {
        // ACTION_POINTER_UP does not end the gesture — a trailing ACTION_UP still arrives when the
        // last finger lifts. If abandoning clears isDragging, that release takes the !isDragging
        // branch and is dispatched as a tap, so a drag the player deliberately made is reinterpreted
        // as a press. Here that only flips a card; on the shipyard the same shape reaches
        // handleShipyardTap and buys a locked ship outright, with no confirmation, for up to
        // ¥100,000.
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())
        dispatch(MotionEvent.ACTION_MOVE, tileRect.centerX() + 40f, tileRect.centerY())
        dispatch(MotionEvent.ACTION_POINTER_UP, tileRect.centerX() + 40f, tileRect.centerY())

        dispatch(MotionEvent.ACTION_UP, tileRect.centerX(), tileRect.centerY())

        assertFalse(
            "an abandoned gesture must not become a tap on the way out",
            view.state.isStoreCardFlipped(tileIndex)
        )
    }

    // --- Owner 2026-08-09: an abandoned purchase must not fall through to a flip ---

    @Test
    fun `releasing after the tap window abandons the purchase without flipping`() {
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())
        // Age the press past the tap window but short of the purchase. The render thread normally
        // does this; a unit test cannot run it, hence the seam.
        view.storeHold.advance(HoldToBuy.TAP_SECONDS + 0.1f)

        dispatch(MotionEvent.ACTION_UP, tileRect.centerX(), tileRect.centerY())

        assertFalse(
            "holding then letting go must not turn the card over — that ambiguity is the bug",
            view.state.isStoreCardFlipped(tileIndex)
        )
        assertEquals("and it must not buy either", 0, persistence.getUpgradeLevel("health"))
    }

    @Test
    fun `releasing inside the tap window still flips`() {
        // Control: the new branch must not swallow an ordinary tap.
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())
        view.storeHold.advance(HoldToBuy.TAP_SECONDS * 0.5f)

        dispatch(MotionEvent.ACTION_UP, tileRect.centerX(), tileRect.centerY())

        assertTrue("a quick release is still a tap", view.state.isStoreCardFlipped(tileIndex))
    }

    // --- Fix 4: a hold (and a held spin button) must not survive the surface being destroyed ---

    @Test
    fun `surfaceDestroyed cancels an in-flight hold`() {
        view.state.currentPage = 2
        view.renderer.upgradeRects.add(tileRect)
        persistence.setYen(100_000)
        view.state.actualYen = 100_000

        dispatch(MotionEvent.ACTION_DOWN, tileRect.centerX(), tileRect.centerY())
        assertEquals(tileIndex, view.heldUpgradeIndex)

        view.surfaceDestroyed(view.holder)

        assertEquals(
            "a hold must not survive the surface being destroyed, or it can complete on " +
                "resume with no finger on screen",
            -1, view.heldUpgradeIndex
        )
    }

    @Test
    fun `surfaceDestroyed releases a held spin button`() {
        view.state.currentPage = 2
        view.renderer.storePageRenderer.spinButtonRect = RectF(0f, 0f, 50f, 50f)

        dispatch(MotionEvent.ACTION_DOWN, 25f, 25f)
        assertTrue("the spin button must have been grabbed for this test to mean anything",
            view.spinButtonHeld)

        view.surfaceDestroyed(view.holder)

        assertFalse(
            "a held spin button has the identical exposure as a hold and must not survive either",
            view.spinButtonHeld
        )
    }
}
