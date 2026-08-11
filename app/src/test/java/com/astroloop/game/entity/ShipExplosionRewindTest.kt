package com.astroloop.game.entity

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

/**
 * The death explosion has to survive long enough to be un-exploded. Debris normally expire and are
 * removed during the 2s death play-out, so by the time the crystal starts there is nothing left to
 * reassemble — unless this explosion was told to hold them.
 *
 * Enemies use the same class, so holding is opt-in and off by default.
 */
class ShipExplosionRewindTest {

    private fun exploded(hold: Boolean): ShipExplosion = ShipExplosion().apply {
        holdDebris = hold
        start(100f, 200f, 0f, 0f, 0f, 30f, 0xFFFFFFFF.toInt())
    }

    /** Run well past both debris lifetime (max 2.0s) and duration (2f). */
    private fun ShipExplosion.runPastDeath() {
        repeat(180) { update(1f / 60f) }
    }

    @Test
    fun `an ordinary explosion still clears itself away`() {
        val enemy = exploded(hold = false)

        enemy.runPastDeath()

        assertTrue("Enemy wreckage must not linger", enemy.getDebris().isEmpty())
        assertFalse(enemy.isActive)
    }

    @Test
    fun `a held explosion keeps its pieces`() {
        val player = exploded(hold = true)
        // 12 hull fragments plus 8 spark particles, but assert against what was actually spawned
        // rather than a literal: the point is that nothing is removed, whatever the count is.
        val spawned = player.getDebris().size
        assertTrue("The explosion should have spawned something", spawned > 0)

        player.runPastDeath()

        assertEquals("Every piece must survive to be rewound", spawned, player.getDebris().size)
    }

    @Test
    fun `holding does not change the fade on the way out`() {
        val player = exploded(hold = true)

        repeat(90) { player.update(1f / 60f) }   // 1.5s — mid play-out

        // Alpha is derived from lifetime (VectorRenderer: alpha = lifetime / 2f), so a held
        // explosion must still be fading, not frozen at full brightness.
        assertTrue(
            "Held debris must still fade exactly as they do today",
            player.getDebris().all { it.lifetime < 1.5f }
        )
    }

    @Test
    fun `every piece remembers where it started`() {
        val player = exploded(hold = true)

        repeat(60) { player.update(1f / 60f) }

        for (d in player.getDebris()) {
            assertTrue(
                "A piece that never moved cannot demonstrate an origin",
                hypot(d.position.x - d.origin.x, d.position.y - d.origin.y) > 0.1f
            )
        }
    }

    @Test
    fun `rewinding to 1 puts every piece back on its origin`() {
        val player = exploded(hold = true)
        player.runPastDeath()
        player.captureScatter()

        player.rewind(1f)

        for (d in player.getDebris()) {
            assertEquals("x", d.origin.x, d.position.x, 0.01f)
            assertEquals("y", d.origin.y, d.position.y, 0.01f)
        }
    }

    @Test
    fun `rewinding brings the fade back with the motion`() {
        val player = exploded(hold = true)
        player.runPastDeath()
        player.captureScatter()
        val faded = player.getDebris().first().lifetime

        player.rewind(1f)

        assertTrue(
            "Time running backwards must restore the fade, not move invisible fragments",
            player.getDebris().first().lifetime > faded
        )
    }

    @Test
    fun `convergence is monotonic`() {
        val player = exploded(hold = true)
        player.runPastDeath()
        player.captureScatter()
        val piece = player.getDebris().first()
        fun distance() = hypot(piece.position.x - piece.origin.x, piece.position.y - piece.origin.y)

        var previous = Float.MAX_VALUE
        for (step in 0..10) {
            player.rewind(step / 10f)
            val now = distance()
            assertTrue("Distance to origin must never grow: $previous -> $now", now <= previous + 0.01f)
            previous = now
        }
    }

    // --- Handing over to the intact ship ---
    //
    // Converging alone leaves a tidy pile of fragments, which is what shipped first and what it
    // looked like: debris arriving in roughly the right place, never becoming a ship. The last
    // stretch of the rewind cross-fades the pieces out as the whole ship fades in over them.

    @Test
    fun `the ship stays hidden for most of the rewind`() {
        assertEquals(0f, ShipExplosion.shipAlphaAt(0f), 0.001f)
        assertEquals(0f, ShipExplosion.shipAlphaAt(0.5f), 0.001f)
        assertEquals(
            "Nothing should appear before the pieces are nearly home",
            0f, ShipExplosion.shipAlphaAt(ShipExplosion.REASSEMBLE_CROSSFADE_START), 0.001f
        )
    }

    @Test
    fun `the ship is fully drawn by the time the rewind ends`() {
        assertEquals(1f, ShipExplosion.shipAlphaAt(1f), 0.001f)
        assertEquals("Past the end it stays whole", 1f, ShipExplosion.shipAlphaAt(2f), 0.001f)
    }

    @Test
    fun `the ship fades in across the crossfade window`() {
        val start = ShipExplosion.REASSEMBLE_CROSSFADE_START
        val midway = start + (1f - start) / 2f

        assertEquals(0.5f, ShipExplosion.shipAlphaAt(midway), 0.05f)
    }

    @Test
    fun `debris fade out exactly as the ship fades in`() {
        val player = exploded(hold = true)
        player.runPastDeath()
        player.captureScatter()

        for (step in 0..10) {
            val progress = step / 10f
            player.rewind(progress)
            assertEquals(
                "The two must sum to one or the swap will flicker",
                1f, player.debrisAlphaScale + ShipExplosion.shipAlphaAt(progress), 0.001f
            )
        }
    }

    @Test
    fun `debris are fully visible until the handover begins`() {
        val player = exploded(hold = true)
        player.runPastDeath()
        player.captureScatter()

        player.rewind(ShipExplosion.REASSEMBLE_CROSSFADE_START)

        assertEquals(
            "The flight home must not be dimmed by the handover",
            1f, player.debrisAlphaScale, 0.001f
        )
    }

    @Test
    fun `debris are gone once the ship is whole`() {
        val player = exploded(hold = true)
        player.runPastDeath()
        player.captureScatter()

        player.rewind(1f)

        assertEquals("Nothing may sit on top of the restored ship", 0f, player.debrisAlphaScale, 0.001f)
    }

    @Test
    fun `rewinding at 0 leaves the scattered state alone`() {
        val player = exploded(hold = true)
        player.runPastDeath()
        player.captureScatter()
        val before = player.getDebris().map { it.position.x to it.position.y }

        player.rewind(0f)

        assertEquals(before, player.getDebris().map { it.position.x to it.position.y })
    }
}
