package com.astroloop.game.hangar

import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.core.AudioMode
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The readout is shared with slot results, and a button message takes it outright: the displaced
 * result is cleared rather than deferred, so it cannot flicker back when the message fades.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HangarReadoutMessageTest {

    private lateinit var state: HangarState

    @Before
    fun setup() {
        val persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        state = HangarState(persistence)
    }

    @Test
    fun `no message is showing to begin with`() {
        assertNull(state.readoutMessage)
        assertEquals(0L, state.readoutMessageTime)
    }

    @Test
    fun `showing a message records it with the time it was set`() {
        val before = System.currentTimeMillis()

        state.showReadoutMessage(AudioMode.MUSIC_MUTED.readoutLabel)

        assertEquals("EFFECTS ONLY", state.readoutMessage)
        assertTrue("The timer must start when the message is set", state.readoutMessageTime >= before)
    }

    @Test
    fun `a message discards the spin result rather than deferring it`() {
        state.spinResultTime = System.currentTimeMillis()
        state.spinResultYen = 700

        state.showReadoutMessage("NO SOUND")

        assertEquals(
            "A jackpot resuming after the message faded would look like a glitch",
            0L, state.spinResultTime
        )
    }

    @Test
    fun `a second message replaces the first and restarts its timer`() {
        state.showReadoutMessage("NO SOUND")
        val firstTime = state.readoutMessageTime
        Thread.sleep(5)

        state.showReadoutMessage("MUSIC ONLY")

        assertEquals("MUSIC ONLY", state.readoutMessage)
        assertTrue("Pressing again restarts the 3s window", state.readoutMessageTime > firstTime)
    }
}
