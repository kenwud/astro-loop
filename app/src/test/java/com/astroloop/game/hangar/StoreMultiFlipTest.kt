package com.astroloop.game.hangar

import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Flipping a second card no longer turns the first one back.
 *
 * Owner, 2026-08-09: "no need for flipped pages to flip back when you select another upgrade."
 * The first cut kept a single `storeFlipIndex`, so tapping a second tile retargeted it and the
 * first card snapped to its front with no fade at all — which also quietly broke the rule that
 * nothing is seen to vanish. Each tile now runs its own clock, so a player can turn over two
 * or three and compare them, which is the whole reason the back exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreMultiFlipTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var state: HangarState

    private val duration = HangarSurfaceView.STORE_FLIP_DURATION

    @Before
    fun setup() {
        persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        state = HangarState(persistence)
    }

    @Test
    fun `two cards can show their backs at the same time`() {
        state.flipStoreCard(1, duration)
        state.flipStoreCard(4, duration)

        assertTrue("the first card must stay turned over", state.isStoreCardFlipped(1))
        assertTrue("the second card must be turned over too", state.isStoreCardFlipped(4))
    }

    @Test
    fun `a card left alone is not flipped`() {
        state.flipStoreCard(1, duration)

        assertFalse(state.isStoreCardFlipped(0))
        assertFalse(state.isStoreCardFlipped(8))
    }

    @Test
    fun `each card's clock runs independently`() {
        state.flipStoreCard(1, duration)
        state.advanceStoreFlips(duration * 0.6f)
        state.flipStoreCard(4, duration)

        // Enough to retire the first card's remaining 40% but not the second's full duration.
        state.advanceStoreFlips(duration * 0.5f)

        assertFalse("the older card's peek should have expired", state.isStoreCardFlipped(1))
        assertTrue("the newer card's peek should still be running", state.isStoreCardFlipped(4))
    }

    @Test
    fun `re-tapping a card that is already flipped restarts its peek`() {
        state.flipStoreCard(1, duration)
        state.advanceStoreFlips(duration * 0.9f)

        state.flipStoreCard(1, duration)
        state.advanceStoreFlips(duration * 0.5f)

        assertTrue("the second tap should have bought a fresh peek", state.isStoreCardFlipped(1))
    }

    @Test
    fun `a peek ends on its own with no dismiss path`() {
        state.flipStoreCard(1, duration)

        state.advanceStoreFlips(duration + 0.01f)

        assertFalse(state.isStoreCardFlipped(1))
        assertEquals("an expired card must not linger at part alpha", 0f, state.storeFlipProgress(1), 0.001f)
    }

    @Test
    fun `the front is on screen for the first fade leg, then the back`() {
        state.flipStoreCard(1, duration)

        assertFalse("the front fades out first", state.storeFlipShowBack(1))

        state.advanceStoreFlips(HangarSurfaceView.STORE_FLIP_FADE + 0.01f)

        assertTrue("the back takes over once the front has faded", state.storeFlipShowBack(1))
    }
}
