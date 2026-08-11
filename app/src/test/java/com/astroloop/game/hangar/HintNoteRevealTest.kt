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
 * The `?` cross-fades into the bar's note rather than being swapped out under the player.
 *
 * The note appears the moment the hint is spoken, which is the moment the player is watching the
 * bar — so without this the `?` simply ceases to exist mid-conversation. The rule covers UI
 * elements for exactly this reason: if it was on screen, it has to be seen to leave.
 *
 * The reveal is deliberately not persisted. On a later launch the note is simply there, already
 * known — a card should not replay its reveal every time the bar is opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HintNoteRevealTest {

    private lateinit var state: HangarState

    @Before
    fun setup() {
        val persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        state = HangarState(persistence)
    }

    @Test
    fun `a card with no reveal running shows its note outright`() {
        assertEquals("nothing to fade — the note is simply known", 1f, state.hintNoteAlpha(), 0.001f)
    }

    @Test
    fun `the note starts invisible and the question mark starts whole`() {
        state.beginHintNoteReveal()

        assertEquals(0f, state.hintNoteAlpha(), 0.001f)
    }

    @Test
    fun `the note fades in across the reveal`() {
        state.beginHintNoteReveal()

        state.advanceHintNoteReveal(HangarState.HINT_NOTE_REVEAL_SECONDS / 2f)

        assertEquals("halfway through, halfway visible", 0.5f, state.hintNoteAlpha(), 0.02f)
    }

    @Test
    fun `the reveal finishes and stays finished`() {
        state.beginHintNoteReveal()

        state.advanceHintNoteReveal(HangarState.HINT_NOTE_REVEAL_SECONDS + 0.5f)

        assertEquals(1f, state.hintNoteAlpha(), 0.001f)

        state.advanceHintNoteReveal(10f)
        assertEquals("and does not wrap round or restart", 1f, state.hintNoteAlpha(), 0.001f)
    }

    @Test
    fun `only the first hint about a pilot arms the reveal`() {
        // The hint keeps firing at 30% until the pilot is recruited. Re-arming on every one of
        // those replays the cross-fade, which pops the "?" back to full and fades it out again —
        // the card visibly reverting to a mystery it is no longer in.
        val persistence = state.persistence

        assertTrue("the first hint about this pilot", persistence.setHintedPilotIndex(3))
        assertFalse("the same pilot hinted again", persistence.setHintedPilotIndex(3))
        assertFalse("and an older one cannot re-arm it either", persistence.setHintedPilotIndex(2))
        assertTrue("but the next pilot along is a new reveal", persistence.setHintedPilotIndex(4))
    }

    @Test
    fun `advancing without a reveal in flight does nothing`() {
        state.advanceHintNoteReveal(5f)

        assertEquals(1f, state.hintNoteAlpha(), 0.001f)
    }
}
