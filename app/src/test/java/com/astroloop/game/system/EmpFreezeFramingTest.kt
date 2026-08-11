package com.astroloop.game.system

import com.astroloop.game.core.GameConfig
import com.astroloop.game.entity.EnemyShip
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * EMP #1 must leave the charging party and its target framed together.
 *
 * The 10-minute encounter is one scene played from both ends. In the normal run the boss rushes
 * the player, plants, and opens the charge with an EMP that freezes them; in the corruption run
 * the player *is* the boss and the same EMP freezes Past Astro. Either way the charge runs for
 * `BOSS_CHARGE_DURATION` — 62 seconds — and whoever is charging has to be watchable while it does.
 *
 * The two sides had drifted apart. The corruption side zeroed Past Astro's velocity before shoving
 * him, so his coast was bounded by the impulse alone; the normal side shoved the player on top of
 * whatever speed they were already fleeing at. Since the camera follows the player, a fast build
 * EMP'd at full tilt kept coasting and towed the view off the stationary boss — the charge shot
 * then happened somewhere off screen.
 *
 * [FleetSystem.empFreeze] is the one gesture both sides now use, so the bound holds either way.
 */
class EmpFreezeFramingTest {

    private fun shipAt(x: Float, y: Float) = EnemyShip().apply { position.set(x, y) }

    private fun speedOf(e: EnemyShip) = sqrt(e.velocity.x * e.velocity.x + e.velocity.y * e.velocity.y)

    @Test
    fun `an EMP leaves the target coasting at exactly the scatter impulse`() {
        val target = shipAt(500f, 0f)

        FleetSystem.empFreeze(target, 0f, 0f)

        assertEquals(FleetSystem.EMP_SCATTER_IMPULSE, speedOf(target), 0.01f)
    }

    @Test
    fun `however fast the target was already going`() {
        val fleeing = shipAt(500f, 0f)
        // A maxed speed build running flat out, directly away from the boss — the worst case.
        fleeing.velocity.x = GameConfig.SHIP_BASE_SPEED * 2f

        FleetSystem.empFreeze(fleeing, 0f, 0f)

        assertEquals(
            "an EMP kills the engine; it must not add to the escape",
            FleetSystem.EMP_SCATTER_IMPULSE, speedOf(fleeing), 0.01f
        )
    }

    @Test
    fun `the shove still points away from the source`() {
        val target = shipAt(500f, 0f)
        target.velocity.x = -900f  // charging straight at the boss when the EMP lands

        FleetSystem.empFreeze(target, 0f, 0f)

        assertTrue("the EMP pushes away, never pulls in", target.velocity.x > 0f)
    }

    @Test
    fun `the coast is far shorter than the distance the boss plants at`() {
        // Distance under constant deceleration: v² / 2a.
        val coast = FleetSystem.EMP_SCATTER_IMPULSE * FleetSystem.EMP_SCATTER_IMPULSE /
            (2f * GameConfig.SHIP_DECELERATION)

        assertTrue(
            "the EMP shove ($coast px) must stay well inside the boss's ${GameConfig.BOSS_RUSH_TRIGGER_DISTANCE}px stand-off",
            coast < GameConfig.BOSS_RUSH_TRIGGER_DISTANCE / 2f
        )
    }

    @Test
    fun `a target sitting on the source produces no NaN`() {
        // Degenerate rather than expected — neither perspective puts the two in the same place —
        // but scatterEntity claims to be safe here, so the zeroing must not break that claim.
        val onTop = shipAt(0f, 0f)

        FleetSystem.empFreeze(onTop, 0f, 0f)

        assertFalse("velocity must stay a number", onTop.velocity.x.isNaN())
        assertFalse("velocity must stay a number", onTop.velocity.y.isNaN())
        assertTrue("with no direction to shove in, it simply holds", speedOf(onTop) < 0.01f)
    }
}
