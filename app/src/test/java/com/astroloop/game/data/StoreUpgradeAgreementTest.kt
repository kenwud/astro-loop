package com.astroloop.game.data

import com.astroloop.game.core.GameState
import org.junit.Assert.*
import org.junit.Test

/**
 * The card backs quote numbers that combat computes elsewhere.
 *
 * `StoreUpgradeDefinitions` deliberately holds its own copy of each per-level value rather than
 * reaching into `GameState`, which would mean constructing a whole game object to format a tile.
 * That copy is only safe while something proves the two agree — this is that something. A card
 * that lies about a number is worse than no card at all.
 */
class StoreUpgradeAgreementTest {

    private fun tile(id: String) = StoreUpgradeDefinitions.tiles.first { it.id == id }

    /** The value the card claims for [id]'s [line]th effect at [level], as a raw float. */
    private fun claimed(id: String, level: Int, line: Int = 0): Float {
        val effect = tile(id).effects[line]
        return effect.perLevel * level
    }

    @Test
    fun `health matches getPermanentHealthBonus at every level`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentHealthLevel = level
            assertEquals(
                "health level $level",
                state.getPermanentHealthBonus(), claimed("health", level), 0.001f
            )
        }
    }

    @Test
    fun `shields matches getPermanentShieldsBonus at every level`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentShieldsLevel = level
            assertEquals(
                "shields level $level",
                state.getPermanentShieldsBonus(), claimed("shields", level), 0.001f
            )
        }
    }

    @Test
    fun `speed matches getPermanentSpeedBonus at every level`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentSpeedLevel = level
            assertEquals(
                "speed level $level",
                state.getPermanentSpeedBonus(), claimed("speed", level), 0.001f
            )
        }
    }

    @Test
    fun `damage matches getPermanentDamageBonus at every level`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentDamageLevel = level
            assertEquals(
                "damage level $level",
                state.getPermanentDamageBonus(), claimed("damage", level), 0.001f
            )
        }
    }

    @Test
    fun `crit matches getCritChance at every level`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentCritLevel = level
            assertEquals(
                "crit level $level",
                state.getCritChance(), claimed("crit", level), 0.001f
            )
        }
    }

    @Test
    fun `yen bonus matches getYenMultiplier at every level`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentYenBonusLevel = level
            // The multiplier starts at 1.0; the card states the bonus above it.
            assertEquals(
                "yen_bonus level $level",
                state.getYenMultiplier() - 1f, claimed("yen_bonus", level), 0.001f
            )
        }
    }

    @Test
    fun `salvage matches getSalvageMultiplier at every level`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentSalvageLevel = level
            assertEquals(
                "salvage level $level",
                state.getSalvageMultiplier() - 1f, claimed("salvage", level), 0.001f
            )
        }
    }

    @Test
    fun `magnet states both the range and the pull speed it grants`() {
        val state = GameState()
        for (level in 0..5) {
            state.permanentMagnetLevel = level
            assertEquals(
                "magnet range level $level",
                state.getMagnetRangeMultiplier() - 1f, claimed("magnet", level, line = 0), 0.001f
            )
            assertEquals(
                "magnet pull speed level $level",
                state.getMagnetSpeedMultiplier() - 1f, claimed("magnet", level, line = 1), 0.001f
            )
        }
    }

    @Test
    fun `every purchasable id is covered by this file`() {
        // Adding a purchasable upgrade without pinning it here is the failure mode this guards.
        assertEquals(
            listOf("health", "shields", "speed", "damage", "crit", "magnet", "yen_bonus", "salvage"),
            StoreUpgradeDefinitions.purchasableIds
        )
    }
}
