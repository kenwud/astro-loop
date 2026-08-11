package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * The store flip is a timed peek, not a toggle — the same shape as the pilot cards.
 *
 * It gets its own duration because a stat block is a denser read than one line of passive text.
 * There is deliberately no dismiss path: nothing can get stuck showing its back.
 */
class StoreFlipClockTest {

    @Test
    fun `the back is readable for the duration minus one fade leg`() {
        // The cycle is: fade the front out, hold the back, fade the back out. So the back is fully
        // legible for (DURATION - FADE) and that is the number worth tuning.
        val readable = HangarSurfaceView.STORE_FLIP_DURATION - HangarSurfaceView.STORE_FLIP_FADE

        assertTrue(
            "a stat block needs longer than the pilot card's 2s, got ${readable}s",
            readable >= 4f
        )
    }

    @Test
    fun `the store peek lasts longer than the pilot peek`() {
        assertTrue(
            "if these were equal the store would not need its own constant",
            HangarSurfaceView.STORE_FLIP_DURATION > HangarSurfaceView.PILOT_FLIP_DURATION
        )
    }

    @Test
    fun `the fade legs match the pilot card's, so the two flips feel the same`() {
        assertEquals(
            HangarSurfaceView.PILOT_FLIP_FADE, HangarSurfaceView.STORE_FLIP_FADE, 0.0001f
        )
    }
}
