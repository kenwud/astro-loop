package com.astroloop.game.weapon

import com.astroloop.game.weapon.weapons.LeechBurst
import com.astroloop.game.weapon.weapons.ScatterShot
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

/**
 * How wide the shotgun throws, and that its evolution throws the same way.
 *
 * Two players reported it independently: one called Scatter Shot "almost useless due
 * to its extremely low damage", the other "the spread is far too wide and random for the really
 * weak and slow pellets to do things most of the time". Its nominal damage is mid-table — five
 * pellets at ten — so the shortfall is not the damage number, it is that a 60-degree throw means
 * most of those pellets are nowhere near what you aimed at. Narrowing the cone raises effective
 * damage without touching the damage stat.
 *
 * The evolution is held to the same cone by construction. LeechBurst carried a comment saying it
 * "matches ScatterShot pattern" and nothing enforced it, which is exactly how a base weapon and
 * its evolution drift apart.
 */
class ScatterSpreadTest {

    @Test
    fun `the cone is tighter than the sixty degrees that was reported as too wide`() {
        val oldCone = PI.toFloat() / 3f   // 60 degrees
        assertTrue(
            "the reported cone was 60 degrees; this must be meaningfully under it",
            ScatterShot.SPREAD_CONE_RADIANS < oldCone
        )
    }

    @Test
    fun `the cone is still wide enough to be a shotgun`() {
        // Narrowing it to nothing would turn the weapon into a machine gun and lose its identity.
        val fifteenDegrees = PI.toFloat() / 12f
        assertTrue(
            "a spread weapon has to still spread",
            ScatterShot.SPREAD_CONE_RADIANS > fifteenDegrees
        )
    }

    @Test
    fun `the evolution throws exactly the same cone as its base weapon`() {
        assertEquals(
            "Leech Burst is Scatter Shot's evolution; a tightening that skipped it would leave " +
                "the upgrade feeling worse than the thing it replaced",
            ScatterShot.SPREAD_CONE_RADIANS,
            LeechBurst.SPREAD_CONE_RADIANS,
            0.0001f
        )
    }

    @Test
    fun `the cone is a half of what it was`() {
        assertEquals(PI.toFloat() / 6f, ScatterShot.SPREAD_CONE_RADIANS, 0.0001f)
    }
}
