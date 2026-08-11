package com.astroloop.game.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetSystemEngineRestartTest {

    @Test
    fun `scale is zero during the sputter`() {
        assertEquals(0f, FleetSystem.engineRestartSpeedScale(0f), 0f)
        assertEquals(0f, FleetSystem.engineRestartSpeedScale(FleetSystem.ENGINE_SPUTTER_DURATION - 0.01f), 0f)
    }

    @Test
    fun `scale ramps linearly after the catch`() {
        val mid = FleetSystem.ENGINE_SPUTTER_DURATION + FleetSystem.ENGINE_RAMP_DURATION / 2f
        assertEquals(0.5f, FleetSystem.engineRestartSpeedScale(mid), 0.001f)
    }

    @Test
    fun `scale reaches and holds full at the end of the ramp`() {
        val end = FleetSystem.ENGINE_SPUTTER_DURATION + FleetSystem.ENGINE_RAMP_DURATION
        assertEquals(1f, FleetSystem.engineRestartSpeedScale(end), 0.001f)
        assertEquals(1f, FleetSystem.engineRestartSpeedScale(end + 5f), 0f)
    }

    @Test
    fun `fleet warp beat lets the engine restart fully play out`() {
        assertTrue(
            "fleet must not warp in before the sputter and ramp complete",
            FleetSystem.FLEET_WARP_BEAT >=
                FleetSystem.ENGINE_SPUTTER_DURATION + FleetSystem.ENGINE_RAMP_DURATION
        )
    }

    @Test
    fun `fleet warp beat is 4 seconds`() {
        assertEquals(4f, FleetSystem.FLEET_WARP_BEAT, 0.001f)
    }

    // ── The struggle line's place in the restart ──────────────────────────
    //
    // "Come on—" used to fire 0.3s after the restart began, and both runs start the restart on
    // the very frame TB-26 says "Since when do I listen?" — so his line held the screen for 0.3s
    // of its 4s DISPLAY_DURATION before being replaced. Owner reported it as barely any gap.
    //
    // It is derived from the restart's own phases rather than set as a literal, because the
    // failure mode of getting it wrong is silent: `updateEngineRestart` returns early once
    // `engineRestarting` clears, so a delay past the end means the line never fires at all.

    @Test
    fun `the struggle line always fires before the restart ends`() {
        val restartEnds = FleetSystem.ENGINE_SPUTTER_DURATION + FleetSystem.ENGINE_RAMP_DURATION
        assertTrue(
            "a delay past the end of the restart deletes the line silently",
            FleetSystem.ENGINE_STRUGGLE_LINE_AT < restartEnds
        )
    }

    @Test
    fun `the struggle line lands while the engine is ramping, not on the sputter`() {
        // Late enough to leave TB-26's line on screen for a beat; still inside the restart, so it
        // reads as Astro straining the ship up to speed rather than as an unrelated line.
        assertTrue(
            "firing during the sputter is what made it tread on the previous line",
            FleetSystem.ENGINE_STRUGGLE_LINE_AT > FleetSystem.ENGINE_SPUTTER_DURATION
        )
        assertTrue(
            "the engine is under power by now",
            FleetSystem.engineRestartSpeedScale(FleetSystem.ENGINE_STRUGGLE_LINE_AT) > 0f
        )
    }

    @Test
    fun `TB-26's line gets a real beat, and the fleet still arrives after`() {
        assertTrue(
            "0.3s was the complaint; anything under a second is the same complaint",
            FleetSystem.ENGINE_STRUGGLE_LINE_AT >= 1f
        )
        assertTrue(
            "both runs warp the fleet in FLEET_WARP_BEAT after TB's line, and the struggle " +
                "line has to land inside that window",
            FleetSystem.ENGINE_STRUGGLE_LINE_AT < FleetSystem.FLEET_WARP_BEAT
        )
    }
}
