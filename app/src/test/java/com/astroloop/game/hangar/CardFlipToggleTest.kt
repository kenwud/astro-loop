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
 * Tapping a card that is already turned over turns it back.
 *
 * Owner, 2026-08-09. Both grids behave the same way, because to a player they are the same gesture
 * on the same kind of object — a pilot card in the bar and an upgrade tile in the store.
 *
 * Closing **runs the closing fade** rather than snapping the card round: the timer is pulled down
 * to one fade leg instead of being zeroed, so the back is seen to go. Zeroing it would make the
 * back vanish between frames, and nothing on screen may go without being seen to go — the same
 * rule that made these peeks cross-fade in the first place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CardFlipToggleTest {

    private lateinit var state: HangarState

    private val storeDuration = HangarSurfaceView.STORE_FLIP_DURATION
    private val storeFade = HangarSurfaceView.STORE_FLIP_FADE

    @Before
    fun setup() {
        val persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        state = HangarState(persistence)
    }

    // --- Store tiles ---

    @Test
    fun `tapping a closed tile opens it`() {
        state.toggleStoreCard(2, storeDuration)

        assertTrue(state.isStoreCardFlipped(2))
    }

    @Test
    fun `tapping an open tile closes it`() {
        state.toggleStoreCard(2, storeDuration)
        state.advanceStoreFlips(storeFade + 0.1f)   // past the opening fade, back on screen
        assertTrue("the tile must be open for this test to mean anything", state.storeFlipShowBack(2))

        state.toggleStoreCard(2, storeDuration)

        state.advanceStoreFlips(storeFade + 0.01f)
        assertFalse("it should have closed within one fade leg", state.isStoreCardFlipped(2))
    }

    @Test
    fun `closing runs the fade rather than snapping`() {
        state.toggleStoreCard(2, storeDuration)
        state.advanceStoreFlips(storeFade + 0.1f)

        state.toggleStoreCard(2, storeDuration)

        assertTrue("still on screen the instant it is dismissed", state.isStoreCardFlipped(2))
        state.advanceStoreFlips(storeFade * 0.5f)
        val mid = state.storeFlipProgress(2)
        assertTrue("and part-faded halfway through the leg, not gone: $mid", mid > 0f && mid < 1f)
    }

    @Test
    fun `closing one tile leaves the others alone`() {
        state.toggleStoreCard(2, storeDuration)
        state.toggleStoreCard(5, storeDuration)
        state.advanceStoreFlips(storeFade + 0.1f)

        state.toggleStoreCard(2, storeDuration)
        state.advanceStoreFlips(storeFade + 0.01f)

        assertFalse(state.isStoreCardFlipped(2))
        assertTrue("the other card was not part of the gesture", state.isStoreCardFlipped(5))
    }

    @Test
    fun `a tile closed mid-open-fade still closes`() {
        state.toggleStoreCard(2, storeDuration)          // opening fade, front still showing

        state.toggleStoreCard(2, storeDuration)
        state.advanceStoreFlips(storeFade + 0.01f)

        assertFalse("a second tap always means 'put it back'", state.isStoreCardFlipped(2))
    }

    @Test
    fun `tile nine toggles like any other card`() {
        // The Time Crystal and Emergency Shield faces are store cards too, even though they are
        // tracked separately and can never be bought.
        val crystal = HangarSurfaceView.CRYSTAL_TILE_INDEX
        state.toggleStoreCard(crystal, storeDuration)
        state.advanceStoreFlips(storeFade + 0.1f)

        state.toggleStoreCard(crystal, storeDuration)
        state.advanceStoreFlips(storeFade + 0.01f)

        assertFalse(state.isStoreCardFlipped(crystal))
    }

    // --- Pilot cards ---

    @Test
    fun `tapping a closed pilot card opens it`() {
        state.togglePilotFlip(3)

        assertEquals(3, state.pilotFlipIndex)
        assertTrue(state.pilotFlipTimer > 0f)
    }

    @Test
    fun `tapping an open pilot card closes it`() {
        state.togglePilotFlip(3)
        state.pilotFlipTimer = HangarSurfaceView.PILOT_FLIP_DURATION - HangarSurfaceView.PILOT_FLIP_FADE - 0.1f

        state.togglePilotFlip(3)

        assertTrue(
            "closing pulls the clock down to one fade leg, not to zero",
            state.pilotFlipTimer <= HangarSurfaceView.PILOT_FLIP_FADE && state.pilotFlipTimer > 0f
        )
    }

    @Test
    fun `tapping a different pilot card opens that one instead`() {
        state.togglePilotFlip(3)

        state.togglePilotFlip(5)

        assertEquals("only one pilot card turns over at a time", 5, state.pilotFlipIndex)
        assertEquals(HangarSurfaceView.PILOT_FLIP_DURATION, state.pilotFlipTimer, 0.001f)
    }
}
