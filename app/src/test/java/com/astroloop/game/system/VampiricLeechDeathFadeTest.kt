package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.Ship
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * The leech stream has to leave the screen when the player dies.
 *
 * `update()` only runs from `updatePlaying`, so on death the particles stopped advancing and never
 * reached the `t >= 1f` expiry — while `renderPlaying` (which `DEATH_PLAY_OUT` still draws through)
 * kept painting them. The purple lines simply stood there for the whole death sequence.
 *
 * Letting them fly home instead would read as the corpse still feeding, so the stream breaks off:
 * particles keep their motion while the whole stream fades. Nothing may be seen to vanish, which
 * is why this is a fade and not a `particles.clear()`.
 */
class VampiricLeechDeathFadeTest {

    private lateinit var system: VampiricLeecherSystem
    private lateinit var state: GameState
    private lateinit var ship: Ship

    @Before
    fun setup() {
        system = VampiricLeecherSystem(onAsteroidDestroyed = {})
        state = GameState()
        state.reset()
        state.passiveStacks["vampiric_core"] = 1
        ship = Ship().apply {
            position.set(100f, 100f)
            health = 50f
            maxHealth = 100f
            radius = 25f
        }
    }

    /** Edge dist = 50 - 30 = 20 ≤ LEECH_RANGE → in range, so a stream seeds. */
    private fun inRangeAsteroid() = Asteroid().apply {
        isActive = true
        position.set(150f, 100f)
        radius = 30f
        health = 500f
        maxHealth = 500f
    }

    private fun seedStream() {
        system.update(ship, listOf(inRangeAsteroid()), state, 0.016f)
        assertTrue("the test needs a live stream to fade", system.particles.isNotEmpty())
    }

    @Test
    fun `the stream is at full strength during normal play`() {
        seedStream()
        assertEquals(1f, system.fadeAlpha, 0.0001f)
    }

    @Test
    fun `particles keep moving while the death fade runs`() {
        seedStream()
        val before = system.particles.map { it.t }

        system.fadeOut(0.05f)

        assertEquals("no particle may be dropped mid-fade", before.size, system.particles.size)
        system.particles.forEachIndexed { i, p ->
            assertTrue("particle $i must keep travelling during the fade", p.t > before[i])
        }
    }

    @Test
    fun `the fade dims the stream before it is gone`() {
        seedStream()

        system.fadeOut(VampiricLeecherSystem.DEATH_FADE_SECONDS / 2f)

        assertTrue("half way through, the stream is dimmed", system.fadeAlpha < 1f)
        assertTrue("but not yet gone", system.fadeAlpha > 0f)
        assertTrue("and still on screen", system.particles.isNotEmpty())
    }

    @Test
    fun `the stream is gone once the fade completes`() {
        seedStream()

        system.fadeOut(VampiricLeecherSystem.DEATH_FADE_SECONDS)

        assertEquals(0f, system.fadeAlpha, 0.0001f)
        assertTrue("nothing may be left drawing", system.particles.isEmpty())
    }

    @Test
    fun `the fade is quick enough to be over well inside the death play-out`() {
        assertTrue(
            "a fade the player waits on is not a fade",
            VampiricLeecherSystem.DEATH_FADE_SECONDS <= 0.5f
        )
    }

    @Test
    fun `a reset restores the stream for the next run`() {
        seedStream()
        system.fadeOut(VampiricLeecherSystem.DEATH_FADE_SECONDS)

        system.reset()

        assertEquals("the next run must not start faded out", 1f, system.fadeAlpha, 0.0001f)
    }
}
