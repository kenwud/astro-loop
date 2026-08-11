package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * What a card back prints for its figures.
 *
 * Owner, 2026-08-09: "it should also show the current value (for example, 50 health if you don't
 * have any health upgrades yet)". Only some of the eight tiles have an absolute number worth
 * printing, so the readout is per-effect:
 *
 *  - **FROM_BASE** — health and shields, which start at a real number the ship already has.
 *  - **ABSOLUTE** — crit, which starts at zero and whose figure is a true percentage.
 *  - **RELATIVE** — speed, damage, yen, salvage and pickup range, where the underlying unit is
 *    pixels per second or a rate multiplier and means nothing to a player. These state the gain.
 *
 * The level-0 rule follows from the same distinction: an absolute figure is information at level 0
 * ("Health 50" is exactly the example asked for), a relative one is noise ("+0% ship speed").
 */
class StoreUpgradeReadoutTest {

    private fun tile(id: String) = StoreUpgradeDefinitions.tiles.first { it.id == id }

    @Test
    fun `health shows the value the ship actually has`() {
        assertEquals(listOf("Health 80"), tile("health").effectsAt(3))
    }

    @Test
    fun `an unbought absolute tile still shows its base`() {
        // The example from the brief: 50 health when you have bought nothing.
        assertEquals(listOf("Health 50"), tile("health").effectsAt(0))
        assertEquals(listOf("Shield 50"), tile("shields").effectsAt(0))
    }

    @Test
    fun `crit reads as a true percentage from zero`() {
        assertEquals(listOf("Crit 0%"), tile("crit").effectsAt(0))
        assertEquals(listOf("Crit 15%"), tile("crit").effectsAt(3))
    }

    @Test
    fun `a relative tile states the gain, not a meaningless absolute`() {
        assertEquals(listOf("+15% ship speed"), tile("speed").effectsAt(3))
        assertEquals(listOf("+60% yen earned"), tile("yen_bonus").effectsAt(3))
    }

    @Test
    fun `a relative tile says nothing at level zero`() {
        assertTrue("'+0% ship speed' is noise where 'Health 50' is information",
            tile("speed").effectsAt(0).isEmpty())
        assertTrue(tile("salvage").effectsAt(0).isEmpty())
    }

    @Test
    fun `the pull speed is kept in the data but never printed`() {
        // Owner, 2026-08-09: the pull number is not relevant to players. The effect stays in the
        // definitions so StoreUpgradeAgreementTest keeps pinning it against GameState — deleting it
        // would quietly drop the guard on a value the game still applies.
        val haulLine = tile("magnet")

        assertEquals("both effects stay in the data", 2, haulLine.effects.size)
        assertEquals("only the range is printed",
            listOf("+45% pickup range"), haulLine.effectsAt(3))
        assertEquals("and only the range is offered as the next step",
            listOf("+15%"), haulLine.nextDeltas())
    }

    @Test
    fun `next deltas state one level's worth`() {
        assertEquals(listOf("+10"), tile("health").nextDeltas())
        assertEquals(listOf("+5%"), tile("crit").nextDeltas())
        assertEquals(listOf("+5%"), tile("speed").nextDeltas())
    }

    @Test
    fun `every purchasable tile has a description of what it does`() {
        for (t in StoreUpgradeDefinitions.tiles.filter { !it.isNgPlus }) {
            assertTrue("${t.name} has no description", t.detail.isNotBlank())
            assertFalse(
                "${t.name}'s description names the device rather than the world: ${t.detail}",
                t.detail.contains("tap", true) || t.detail.contains("finger", true) ||
                    t.detail.contains("screen", true) || t.detail.contains("bar", true)
            )
        }
    }
}
