package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * What a weapon card says about itself.
 *
 * Reported by a player: "a couple of the Evo descriptions are broken. The Gambers Mines'
 * description only says 'mines with random' without specifying what. Warp Saw's description is
 * literally just 'the'."
 *
 * The strings were whole; the card was cutting them. `UpgradeSelectionRenderer` wrapped and then
 * took the first two lines with no ellipsis, and every evolution description opened by restating
 * its own recipe — `"Energy Saw + Momentum Drive: the blade detaches and hunts on its own"` spent
 * 28 of its 68 characters on a prefix before saying anything. The card already draws the base
 * weapon's icon and `+ PassiveName` directly above, so those characters bought nothing and pushed
 * the part that mattered off the bottom.
 *
 * So the budget below is not arbitrary tidiness: it is what makes a description survive the card
 * it is drawn on.
 */
class WeaponDescriptionTest {

    /** Two lines on the narrowest card at 22px Exo 2, with the recipe prefix gone. */
    private val budget = 40

    // Both lists: the evolutions are the ones that overran, so a check that skipped them would
    // have passed against the exact strings that were reported broken.
    private val descriptions =
        (WeaponDefinitions.weapons + WeaponDefinitions.evolutions).map { it.id to it.description }

    @Test
    fun `every description fits the card`() {
        for ((id, text) in descriptions) {
            assertTrue(
                "$id is ${text.length} chars, over the $budget budget: \"$text\"",
                text.length <= budget
            )
        }
    }

    @Test
    fun `no description restates the recipe the card already shows`() {
        // "Base Weapon + Passive: effect" — the card draws that pairing as an icon and a
        // "+ PassiveName" line, so spelling it out again is what overran the space.
        for ((id, text) in descriptions) {
            assertFalse(
                "$id repeats its recipe instead of describing itself: \"$text\"",
                text.contains(" + ") && text.contains(": ")
            )
        }
    }

    @Test
    fun `every description says something`() {
        for ((id, text) in descriptions) {
            assertTrue("$id has no description", text.isNotBlank())
            assertTrue("$id is too terse to inform anyone: \"$text\"", text.length >= 8)
        }
    }

    @Test
    fun `descriptions start capitalised, evolutions included`() {
        // Evolution effects used to be the tail of a sentence, so they began lowercase. With the
        // prefix gone they are sentences in their own right.
        for ((id, text) in descriptions) {
            assertTrue("$id starts lowercase: \"$text\"", text.first().isUpperCase())
        }
    }

    @Test
    fun `the two that were reported say what the weapon does`() {
        val warpSaw = WeaponDefinitions.getWeaponDef("warp_saw")!!.description
        val gamblers = WeaponDefinitions.getWeaponDef("jackpot_mines")!!.description

        assertTrue("Warp Saw must say what the blade does", warpSaw.contains("blade"))
        assertTrue(warpSaw.length <= budget)

        // "Mines with random effects" was the reported fragment's full form, and it was barely
        // better than the fragment: there are no "effects" plural. There is one, a 5% jackpot per
        // detonation that fires a 220-radius blast and showers yen (applyGamblersMineEffect).
        // Naming it is the difference between a description and a shrug.
        assertFalse(
            "\"random effects\" describes nothing: \"$gamblers\"",
            gamblers.contains("random", ignoreCase = true)
        )
        assertTrue(
            "the payout is a jackpot, and the weapon is called Gambler's Mines: \"$gamblers\"",
            gamblers.contains("jackpot", ignoreCase = true)
        )
    }

    @Test
    fun `Frost Ring does not claim to slow anything`() {
        // The orbs only mark what they touch with a blue outline — GameSurfaceView:1748,
        // "cryo visual (blue outline, no slowdown)". The slowing is the Cryo Field passive the
        // player already had to be holding to earn this evolution, so crediting it to the ring
        // sells them something they own twice. What the evolution actually adds is a second ring
        // and permanence.
        val frost = WeaponDefinitions.getWeaponDef("frost_ring")!!.description
        assertFalse("the ring does not slow: \"$frost\"", frost.contains("slow", ignoreCase = true))
    }

    @Test
    fun `Hunter Killer claims a rate, not a speed`() {
        // Cluster Bomb and Hunter Killer both travel at 200. The evolution halves the cooldown
        // (2.0s to 1.0s), so it fires twice as often — it does not fly faster, and a player who
        // read "twice as fast" would be waiting for a torpedo that never speeds up.
        val hk = WeaponDefinitions.getWeaponDef("hunter_killer")!!.description
        assertFalse("its speed is unchanged: \"$hk\"", hk.contains("faster") || hk.contains("as fast"))
    }

    @Test
    fun `no description promises a spread the weapon no longer throws`() {
        // Scatter Shot's cone was halved on 2026-08-11; "Wide" stopped being true that day.
        val scatter = WeaponDefinitions.getWeaponDef("scatter_shot")!!.description
        assertFalse("the cone is 30 degrees now: \"$scatter\"", scatter.contains("Wide"))
    }
}
