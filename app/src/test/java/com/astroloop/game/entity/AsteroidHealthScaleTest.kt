package com.astroloop.game.entity

import com.astroloop.game.util.Vector2
import org.junit.Assert.*
import org.junit.Test

/**
 * Asteroid health scaling, used by the Astro Loop endless ramp.
 *
 * Applied at spawn, after initialize() has set the base health from size and type, so the
 * scale has to lift the ceiling as well as the current value — a scaled asteroid must spawn
 * at full health, not damaged.
 */
class AsteroidHealthScaleTest {

    private fun largeRock(): Asteroid = Asteroid().apply {
        initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK, Vector2(1f, 0f))
    }

    @Test
    fun `scaling raises max health and spawns at full health`() {
        val asteroid = largeRock()
        assertEquals(50f, asteroid.maxHealth, 0.01f)

        asteroid.scaleHealth(4f)

        assertEquals(200f, asteroid.maxHealth, 0.01f)
        assertEquals("A scaled asteroid must spawn undamaged", 200f, asteroid.health, 0.01f)
    }

    @Test
    fun `scaling compounds on the type multiplier rather than replacing it`() {
        val metal = Asteroid().apply {
            initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.METAL, Vector2(1f, 0f))
        }
        assertEquals("Metal is double a rock of the same size", 100f, metal.maxHealth, 0.01f)

        metal.scaleHealth(3f)

        assertEquals(300f, metal.maxHealth, 0.01f)
    }

    @Test
    fun `a factor of one leaves the asteroid untouched`() {
        val asteroid = largeRock()

        asteroid.scaleHealth(1f)

        assertEquals(50f, asteroid.maxHealth, 0.01f)
        assertEquals(50f, asteroid.health, 0.01f)
    }
}
