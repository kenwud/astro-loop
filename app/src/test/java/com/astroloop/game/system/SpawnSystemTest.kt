package com.astroloop.game.system

import com.astroloop.game.core.Camera
import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.AsteroidSize
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.Ship
import org.junit.Assert.*
import org.junit.Test

class SpawnSystemTest {

    /** Spawns one batch and returns the large asteroids from it (all spawns start LARGE). */
    private fun spawnBatch(survivalTime: Float, astroLoop: Boolean): List<Asteroid> {
        val system = SpawnSystem(EntityPool(factory = { Asteroid() }, initialSize = 20))
        system.initialize(1080f, 2400f)
        val state = GameState().apply {
            reset()
            this.survivalTime = survivalTime
            this.astroLoopMode = astroLoop
            difficultyMultiplier = 1f
        }
        // deltaTime large enough to trip the spawn timer immediately
        return system.update(10f, state, Ship(), Camera())
            .filter { it.size == AsteroidSize.LARGE }
    }

    @Test
    fun `asteroid count tracks the multiplier capped at 3`() {
        assertEquals(1, SpawnSystem.asteroidCount(1.0f))
        assertEquals(2, SpawnSystem.asteroidCount(2.25f)) // rounds to 2
        assertEquals(3, SpawnSystem.asteroidCount(2.75f)) // rounds to 3
        assertEquals(3, SpawnSystem.asteroidCount(4.0f))  // coerced to 3
    }

    @Test
    fun `asteroid count never drops below 1`() {
        assertEquals(1, SpawnSystem.asteroidCount(0.4f))
        assertEquals(1, SpawnSystem.asteroidCount(0f))
    }

    @Test
    fun `speed factor ramps with time and multiplier`() {
        // 4 min, mult 3.16: (1 + 4*0.15) * 3.16 = 5.056, below the cap
        assertEquals(5.056f, SpawnSystem.asteroidSpeedFactor(240f, 3.16f), 0.01f)
    }

    @Test
    fun `speed factor is capped at 7`() {
        // 8 min, mult 4.0: (1 + 8*0.15) * 4.0 = 8.8 -> clamped to 7.0
        assertEquals(7.0f, SpawnSystem.asteroidSpeedFactor(480f, 4.0f), 0.001f)
        // 10 min, mult 4.0: even higher -> still 7.0
        assertEquals(7.0f, SpawnSystem.asteroidSpeedFactor(600f, 4.0f), 0.001f)
    }

    // --- Asteroid health ramp (Astro Loop endless scaling) ---
    //
    // Astro Loop is the endless mode where the player sets a best time, so a run that
    // cannot end is a broken scoreboard. Count, speed and spawn rate all clamp, leaving
    // health as the only lever: it compounds from 8 minutes and is deliberately uncapped.

    @Test
    fun `health factor never scales outside astro loop mode`() {
        assertEquals(1.0f, SpawnSystem.asteroidHealthFactor(0f, false), 0.001f)
        assertEquals(1.0f, SpawnSystem.asteroidHealthFactor(600f, false), 0.001f)
        // 20 minutes into a normal run — still untouched
        assertEquals(1.0f, SpawnSystem.asteroidHealthFactor(1200f, false), 0.001f)
    }

    @Test
    fun `health factor is flat until the 8 minute ramp start`() {
        assertEquals(1.0f, SpawnSystem.asteroidHealthFactor(0f, true), 0.001f)
        assertEquals(1.0f, SpawnSystem.asteroidHealthFactor(240f, true), 0.001f)
        // Exactly at the start: continuous with what came before
        assertEquals(1.0f, SpawnSystem.asteroidHealthFactor(480f, true), 0.001f)
    }

    @Test
    fun `health factor doubles every 4 and a half minutes past the ramp start`() {
        assertEquals(2.0f, SpawnSystem.asteroidHealthFactor(750f, true), 0.01f)   // 12.5 min
        assertEquals(4.0f, SpawnSystem.asteroidHealthFactor(1020f, true), 0.01f)  // 17.0 min
        assertEquals(8.0f, SpawnSystem.asteroidHealthFactor(1290f, true), 0.01f)  // 21.5 min
    }

    @Test
    fun `health factor is uncapped so every run eventually ends`() {
        // 40 minutes: 2^((40-8)/4.5) = 2^7.111 ~ 138
        assertTrue(
            "Health must keep compounding with no ceiling",
            SpawnSystem.asteroidHealthFactor(2400f, true) > 100f
        )
    }

    @Test
    fun `asteroids spawn with scaled health in astro loop mode`() {
        // 17 minutes into an Astro Loop run: factor 4, so a LARGE rock is 200 not 50.
        // METAL is doubled at spawn, so assert on the ratio rather than an absolute.
        val spawned = spawnBatch(survivalTime = 1020f, astroLoop = true)
        assertTrue("Expected at least one asteroid", spawned.isNotEmpty())
        spawned.forEach {
            val baseline = if (it.type == com.astroloop.game.entity.AsteroidType.METAL) 100f else 50f
            assertEquals(baseline * 4f, it.maxHealth, 1f)
            assertEquals("Must spawn undamaged", it.maxHealth, it.health, 0.01f)
        }
    }

    @Test
    fun `asteroids spawn at base health in a normal run`() {
        // Same 17 minutes, normal mode: untouched, because normal pacing is authored elsewhere.
        val spawned = spawnBatch(survivalTime = 1020f, astroLoop = false)
        assertTrue("Expected at least one asteroid", spawned.isNotEmpty())
        spawned.forEach {
            val baseline = if (it.type == com.astroloop.game.entity.AsteroidType.METAL) 100f else 50f
            assertEquals(baseline, it.maxHealth, 0.01f)
        }
    }
}
