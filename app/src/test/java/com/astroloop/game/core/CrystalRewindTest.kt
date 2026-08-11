package com.astroloop.game.core

import com.astroloop.game.render.CrystalRenderer
import org.junit.Assert.*
import org.junit.Test

/**
 * The rewind runs while the crystal lattice grows and ends exactly as it closes, so the ship
 * finishes reassembling at the instant coverage reaches 1.0 and the hold second that follows rests
 * on a restored, static scene rather than cutting to the bar mid-motion.
 */
class CrystalRewindTest {

    /**
     * Steps at 60Hz for at least [seconds]. Rounds the step count up: truncating would leave each
     * call fractionally short, and two half-window calls would then never quite close the window.
     */
    private fun run(rewind: CrystalRewind, seconds: Float) {
        val step = 1f / 60f
        repeat(kotlin.math.ceil(seconds / step).toInt()) { rewind.update(step) }
    }

    @Test
    fun `it does nothing until it is started`() {
        val rewind = CrystalRewind()

        run(rewind, 1f)

        assertFalse(rewind.isRunning)
        assertEquals(0f, rewind.progress, 0.001f)
    }

    @Test
    fun `progress runs from 0 to 1 across the window`() {
        val rewind = CrystalRewind()
        rewind.start()

        assertEquals(0f, rewind.progress, 0.001f)
        run(rewind, CrystalRewind.DURATION_SECONDS / 2f)
        assertEquals(0.5f, rewind.progress, 0.05f)
        run(rewind, CrystalRewind.DURATION_SECONDS / 2f)
        assertEquals(1f, rewind.progress, 0.001f)
    }

    @Test
    fun `it closes exactly as the lattice does`() {
        assertEquals(
            "The rewind and the crystal are one event and must end together",
            1f / CrystalRenderer.DEATH_COVERAGE_RATE, CrystalRewind.DURATION_SECONDS, 0.001f
        )
    }

    @Test
    fun `progress never exceeds 1 however long it runs`() {
        val rewind = CrystalRewind()
        rewind.start()

        run(rewind, CrystalRewind.DURATION_SECONDS * 3f)

        assertEquals(1f, rewind.progress, 0.001f)
        assertFalse("It stops running once complete", rewind.isRunning)
    }

    @Test
    fun `starting again rewinds from the beginning`() {
        val rewind = CrystalRewind()
        rewind.start()
        run(rewind, CrystalRewind.DURATION_SECONDS)

        rewind.start()

        assertEquals(0f, rewind.progress, 0.001f)
        assertTrue(rewind.isRunning)
    }
}
