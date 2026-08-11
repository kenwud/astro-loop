package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidSize
import com.astroloop.game.entity.AsteroidType
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.util.Vector2
import org.junit.Assert.*
import org.junit.Test

/**
 * Fragments carry the Endless-mode health ramp too.
 *
 * They did not. `scaleHealth` was called in exactly one place — `spawnAsteroid` — so a LARGE rock
 * at 25 minutes had 675 health while the two MEDIUMs it broke into had 25 each, the untouched base
 * value. Since a maxed build spends most of its time shooting fragments, the ramp was doing a
 * fraction of the work its constant implies, which is a poor thing to be tuning against.
 *
 * The factor is taken from the clock rather than inherited from the parent, so it is the same rule
 * `spawnAsteroid` uses: every asteroid on the field at minute T is worth minute T. Inheriting would
 * let a rock that survived a long time seed weak fragments into a late game specifically designed
 * to stop being survivable.
 */
class SplitAsteroidHealthRampTest {

    private fun system() = SpawnSystem(EntityPool(factory = { Asteroid() }, initialSize = 20))

    private fun largeRock() = Asteroid().apply {
        initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2(1f, 0f))
    }

    private fun endlessAt(minutes: Float) = GameState().apply {
        reset()
        astroLoopMode = true
        survivalTime = minutes * 60f
    }

    @Test
    fun `fragments are unscaled before the ramp starts`() {
        val fragments = system().spawnSplitAsteroids(largeRock(), endlessAt(5f))

        assertTrue(fragments.isNotEmpty())
        // MEDIUM base is 25.
        for (f in fragments) assertEquals(25f, f.maxHealth, 0.01f)
    }

    @Test
    fun `fragments double when the ramp has doubled`() {
        // 12.5 minutes is one doubling past the 8-minute start.
        val fragments = system().spawnSplitAsteroids(largeRock(), endlessAt(12.5f))

        for (f in fragments) assertEquals(50f, f.maxHealth, 0.5f)
    }

    @Test
    fun `fragments take the same factor a fresh spawn would`() {
        val minutes = 21.5f   // three doublings: x8
        val fragments = system().spawnSplitAsteroids(largeRock(), endlessAt(minutes))
        val factor = SpawnSystem.asteroidHealthFactor(minutes * 60f, astroLoopMode = true)

        assertEquals("three doublings", 8f, factor, 0.01f)
        for (f in fragments) assertEquals(25f * factor, f.maxHealth, 1f)
    }

    @Test
    fun `fragments spawn at full health, not damaged`() {
        val fragments = system().spawnSplitAsteroids(largeRock(), endlessAt(17f))

        for (f in fragments) assertEquals(f.maxHealth, f.health, 0.01f)
    }

    @Test
    fun `normal mode fragments are never scaled`() {
        val normal = GameState().apply { reset(); astroLoopMode = false; survivalTime = 40f * 60f }

        val fragments = system().spawnSplitAsteroids(largeRock(), normal)

        for (f in fragments) assertEquals(
            "the ramp is Endless-only, at any survival time", 25f, f.maxHealth, 0.01f
        )
    }

    @Test
    fun `a metal parent's fragments keep the metal doubling on top of the ramp`() {
        val metal = Asteroid().apply {
            initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.METAL, Vector2(1f, 0f))
        }

        val fragments = system().spawnSplitAsteroids(metal, endlessAt(12.5f))

        // MEDIUM base 25, metal x2, ramp x2.
        for (f in fragments) assertEquals(100f, f.maxHealth, 1f)
    }
}
