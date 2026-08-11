package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * The note the bar leaves on a locked pilot's card once someone has hinted about them.
 *
 * Owner, 2026-08-09: after the hint drops, the "?" on the next pilot becomes a short quote that
 * reminds you how to unlock them — because a hint heard once, several runs ago, in a bar you were
 * passing through, is not a reminder.
 *
 * Two things decide whether a card has a note, and they come from different places:
 *  - **Pilots 1-10** are hinted by the previously recruited pilot, tracked by the highest index
 *    whose hint has fired.
 *  - **ASTRO** is hinted from two directions at once, by design — TB-26 through his own
 *    `astroHintLines`, and HAVOC through the chain, two people noticing the same stranger. His
 *    card therefore reveals on either.
 *
 * And one thing takes them all away: from **story loop 2 onward** every non-Astro pilot unlocks
 * after a single run regardless of their stated condition (`HangarState.checkPilotUnlockCondition`),
 * so a note saying "Is looking for yen" there would be a lie rather than a reminder.
 */
class PilotHintCardsTest {

    private val astroIndex = PilotDefinitions.pilots.indexOfFirst { it.id == "pilot_astro" }

    private fun card(
        pilotIndex: Int,
        hintedPilotIndex: Int = 11,
        astroHinted: Boolean = true,
        hasLoopedBefore: Boolean = false
    ) = PilotHintCards.cardFor(pilotIndex, hintedPilotIndex, astroHinted, hasLoopedBefore)

    @Test
    fun `every locked pilot has a note`() {
        // MEDIC is free and never locked, so she needs none. Everyone else does — and this failing
        // is the point when a twelfth pilot is added without one.
        for ((index, pilot) in PilotDefinitions.pilots.withIndex()) {
            if (pilot.id == "pilot_medic") continue
            assertNotNull("${pilot.callsign} has no note", card(index))
        }
    }

    @Test
    fun `the free pilot has none`() {
        assertNull(card(0))
    }

    @Test
    fun `a note only appears once the hint about that pilot has dropped`() {
        assertNull("nothing has been hinted yet", card(1, hintedPilotIndex = -1))
        assertNotNull("MEDIC has now mentioned them", card(1, hintedPilotIndex = 1))
    }

    @Test
    fun `a pilot further down the roster has no note yet`() {
        assertNull("only the next one has been hinted", card(4, hintedPilotIndex = 1))
    }

    @Test
    fun `astro reveals on either foreshadowing`() {
        // Both TB-26 and HAVOC hint at Astro, by design — TB through astroHintLines, HAVOC through
        // the pilot chain. Either one landing means the bar has learned something, so either
        // reveals the card; requiring TB's specifically would leave a player who only ever heard
        // HAVOC staring at a "?" they had already been told about.
        assertNotNull(
            "TB-26 hinted",
            card(astroIndex, hintedPilotIndex = -1, astroHinted = true)
        )
        assertNotNull(
            "HAVOC hinted through the chain",
            card(astroIndex, hintedPilotIndex = astroIndex, astroHinted = false)
        )
        assertNull(
            "nobody has said anything about him yet",
            card(astroIndex, hintedPilotIndex = astroIndex - 1, astroHinted = false)
        )
    }

    @Test
    fun `astro's note names both halves of his condition`() {
        // ALL_OTHERS needs every pilot AND every ship. A note mentioning only the crew would
        // mislead a player who is one hull short.
        val note = card(astroIndex)!!

        assertTrue("must mention pilots: $note", note.contains("pilot", true))
        assertTrue("must mention ships: $note", note.contains("ship", true))
    }

    @Test
    fun `no notes once unlocking stops following the stated conditions`() {
        for (index in PilotDefinitions.pilots.indices) {
            assertNull(
                "from loop 2 a single run unlocks anyone, so a condition note would be a lie",
                card(index, hasLoopedBefore = true)
            )
        }
    }

    @Test
    fun `notes are quoted, short, and speak about the pilot rather than to the player`() {
        for (note in PilotHintCards.notes.values) {
            assertTrue("not quoted: $note", note.startsWith("\"") && note.endsWith("\""))
            assertTrue("${note.length} chars is too long for a roster card: $note", note.length <= 42)
            assertFalse(
                "a card must not address the commander — that is TB-26's alone: $note",
                note.contains("commander", true)
            )
        }
    }
}
