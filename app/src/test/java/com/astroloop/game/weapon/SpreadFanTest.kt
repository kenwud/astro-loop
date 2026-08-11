package com.astroloop.game.weapon

import org.junit.Assert.*
import org.junit.Test

/**
 * A spread that always keeps one shot on the aim line.
 *
 * Pulse Cannon auto-aims, then fired `baseAngle + step × (i − (count−1)/2)`. At odd counts that
 * puts one pellet dead centre; at **even** counts it straddles, so L2 fired ±7.5° and nothing at
 * all down the middle. Owner, 2026-08-11: "you lose the particle going straight, so aiming becomes
 * weird." A weapon whose whole identity is that it points at the target for you should not
 * deliberately miss to either side.
 *
 * The parity is not just a level thing — `extraProjectiles` from passives adds to the count too,
 * so the weapon flipped between aiming true and straddling as the player picked up unrelated
 * upgrades. That is why the guarantee lives in the angle formula rather than in the count: no
 * arithmetic anywhere else can re-break it.
 *
 * So: reserve one for the centre, pair the rest outward. Even counts have one left over, and its
 * side alternates per volley so the bias averages out at two shots a second.
 */
class SpreadFanTest {

    private val step = 15f

    @Test
    fun `a single pellet goes straight down the aim line`() {
        assertEquals(listOf(0f), SpreadFan.offsets(1, step, mirrored = false))
    }

    @Test
    fun `every count keeps exactly one pellet on the aim line`() {
        for (count in 1..12) {
            val onAim = SpreadFan.offsets(count, step, mirrored = false).count { it == 0f }
            assertEquals("count $count must fire exactly one pellet straight", 1, onAim)
        }
    }

    @Test
    fun `the fan fires as many pellets as it was asked for`() {
        for (count in 1..12) {
            assertEquals(count, SpreadFan.offsets(count, step, mirrored = false).size)
        }
    }

    @Test
    fun `odd counts stay symmetric, exactly as they already were`() {
        // These already worked, so the fix must not disturb them.
        assertEquals(
            listOf(0f, -step, step),
            SpreadFan.offsets(3, step, mirrored = false)
        )
        assertEquals(
            listOf(0f, -step, step, -2 * step, 2 * step),
            SpreadFan.offsets(5, step, mirrored = false)
        )
    }

    @Test
    fun `odd counts ignore the mirror, having nothing left over`() {
        assertEquals(
            SpreadFan.offsets(5, step, mirrored = false),
            SpreadFan.offsets(5, step, mirrored = true)
        )
    }

    @Test
    fun `an even count keeps the centre and puts the odd one out to one side`() {
        assertEquals(listOf(0f, step), SpreadFan.offsets(2, step, mirrored = false))
        assertEquals(listOf(0f, -step, step, 2 * step), SpreadFan.offsets(4, step, mirrored = false))
    }

    @Test
    fun `the leftover changes side between volleys`() {
        assertEquals(listOf(0f, -step), SpreadFan.offsets(2, step, mirrored = true))
        assertEquals(
            listOf(0f, -step, step, -2 * step),
            SpreadFan.offsets(4, step, mirrored = true)
        )
    }

    @Test
    fun `the fan widens with the count`() {
        val narrow = SpreadFan.offsets(3, step, mirrored = false).maxOf { kotlin.math.abs(it) }
        val wide = SpreadFan.offsets(9, step, mirrored = false).maxOf { kotlin.math.abs(it) }
        assertTrue("more pellets must cover more ground", wide > narrow)
    }

    @Test
    fun `a count of nothing fires nothing`() {
        assertTrue(SpreadFan.offsets(0, step, mirrored = false).isEmpty())
        assertTrue(SpreadFan.offsets(-3, step, mirrored = false).isEmpty())
    }
}
