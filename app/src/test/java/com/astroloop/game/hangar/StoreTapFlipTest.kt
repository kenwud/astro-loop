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
 * A tap on a store tile now reads it rather than buying it.
 *
 * The purchase moved to a hold, so the one thing that must never regress is that a tap cannot spend
 * yen — a player with muscle memory from 1.1 will tap first, and their money has to survive it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreTapFlipTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var state: HangarState

    @Before
    fun setup() {
        persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        persistence.setYen(100_000)
        state = HangarState(persistence)
    }

    @Test
    fun `flipping a card arms the peek on that tile`() {
        state.flipStoreCard(4, HangarSurfaceView.STORE_FLIP_DURATION)

        assertTrue(state.isStoreCardFlipped(4))
        assertFalse("the front is still on screen for the first fade leg", state.storeFlipShowBack(4))
    }

    // Turning over a second card used to turn the first one back; it no longer does, and
    // StoreMultiFlipTest owns that behaviour now.

    @Test
    fun `flipping spends nothing`() {
        val before = persistence.getYen()

        state.flipStoreCard(0, HangarSurfaceView.STORE_FLIP_DURATION)

        assertEquals("a tap must never cost money", before, persistence.getYen())
        assertEquals(0, persistence.getUpgradeLevel("health"))
    }
}
