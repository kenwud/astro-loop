package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Passive descriptions leave the line breaks to the layout.
 *
 * They used to carry their own — `"Slow zone\naround ship"` — from before anything measured how
 * text fitted. A hard break assumes a width, and the same string is drawn at two of them: the
 * pickup card during a run, and the narrow strip on a flipped crew card. A break calibrated for one
 * is wrong for the other, and worse, it forces two lines onto a card with room for the sentence on
 * one.
 *
 * `TextWrap` decides now. It still honours a `\n` where one exists, so this is a rule about the
 * copy rather than a limitation of the wrapper.
 *
 * **`effectPerStack` is deliberately not covered by this.** Those go through
 * `UpgradeSelectionRenderer.splitEffectText`, which breaks on `\n`, commas and "and" to put each
 * *fact* on its own row — `"+1 weapon\n-1 passive"` is two claims, not one wrapped sentence.
 */
class PassiveDescriptionTest {

    private val passives = PassiveDefinitions.passives

    /** The longest description that reads on a flipped crew card's strip. See the test below. */
    private val CREW_CARD_BUDGET = 32

    @Test
    fun `no description carries a hard line break`() {
        for (p in passives) {
            assertFalse(
                "${p.id} still breaks its own lines: \"${p.description}\" — the layout does that now",
                p.description.contains('\n')
            )
        }
    }

    @Test
    fun `every description still says something`() {
        for (p in passives) {
            assertTrue("${p.id} has no description", p.description.isNotBlank())
            assertFalse("${p.id} has a doubled space from a stripped break", p.description.contains("  "))
            assertEquals("${p.id} has stray whitespace", p.description.trim(), p.description)
        }
    }

    @Test
    fun `no description outruns the crew card`() {
        // 32 is Vampiric Core's "Drain life from nearby asteroids", the longest that reads on the
        // narrow strip of a flipped pilot card. Calibrated from the device rather than measured
        // here: Lucky Star at 39 was reported truncated on 2026-08-11 and 32 was not, so the cap
        // sits at the longest known good. Move it only with a screen in front of you.
        for (p in passives) {
            assertTrue(
                "${p.id} is ${p.description.length} chars, over the $CREW_CARD_BUDGET the strip " +
                    "holds: \"${p.description}\"",
                p.description.length <= CREW_CARD_BUDGET
            )
        }
    }

    @Test
    fun `Lucky Star still says you give up the choice`() {
        // The shortening must not cost the downside. Auto-picking is the trade the player is
        // making, and a line that only promised "more upgrades" would sell the upside alone.
        val lucky = passives.first { it.id == "lucky_star" }
        assertTrue("the drop bonus", lucky.description.contains("More", ignoreCase = true))
        assertTrue("and the cost of it", lucky.description.contains("random", ignoreCase = true))
    }

    @Test
    fun `a description that lost a break did not become a run-on`() {
        // Lucky Star's break was doing punctuation's job: "More upgrades drop" / "all picked
        // randomly" are two clauses, and joining them with a space alone read as one bad sentence.
        // Anywhere a break was carrying grammar, the grammar has to be written out.
        val lucky = passives.first { it.id == "lucky_star" }
        assertTrue(
            "two clauses need a comma once the line break stops separating them: " +
                "\"${lucky.description}\"",
            lucky.description.contains(", ")
        )
    }

    @Test
    fun `the effect lines keep their separators`() {
        // The opposite rule, and the reason the two fields are treated differently: these are
        // separate facts stacked on their own rows, not a sentence being fitted to a width.
        val slot = passives.first { it.id == "extra_weapon_slot" }
        assertTrue(
            "the weapon gain and the passive cost must stay on separate rows",
            slot.effectPerStack.contains('\n')
        )
    }
}
