package com.astroloop.game.core

import com.astroloop.game.render.CrystalRenderer

/**
 * Clock for the rewind that runs under the crystal on death.
 *
 * Holds only the progress of the window; what moves is decided by the caller. Separated from
 * `GameSurfaceView` so the timing is testable — the thing most likely to be wrong here is the
 * relationship between this window and the lattice, not the arithmetic of moving a position.
 */
class CrystalRewind {

    companion object {
        /**
         * How long time runs backwards, in seconds, at 1x.
         *
         * Exactly as long as the lattice takes to close, so the ship finishes reassembling at the
         * instant coverage reaches 1.0 — the rewind and the crystal are one event and they end
         * together. The DEATH_HOLD_DURATION second that follows then rests on a restored, static
         * scene rather than cutting to the bar mid-motion.
         *
         * Derived rather than written out, so retuning DEATH_COVERAGE_RATE moves both together
         * instead of silently desynchronising them.
         *
         * **This is the starting point, and the thing most likely to need adjusting on a device.**
         * Too long and rocks travel far enough to leave the screen, exposing that nothing is
         * genuinely being undone; too short and the motion never reads as reversal at all.
         */
        const val DURATION_SECONDS = 1f / CrystalRenderer.DEATH_COVERAGE_RATE
    }

    var isRunning: Boolean = false
        private set

    var progress: Float = 0f
        private set

    private var elapsed: Float = 0f

    fun start() {
        isRunning = true
        elapsed = 0f
        progress = 0f
    }

    fun update(deltaTime: Float) {
        if (!isRunning) return
        elapsed += deltaTime
        progress = (elapsed / DURATION_SECONDS).coerceIn(0f, 1f)
        if (progress >= 1f) isRunning = false
    }

    fun reset() {
        isRunning = false
        elapsed = 0f
        progress = 0f
    }
}
