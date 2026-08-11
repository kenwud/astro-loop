package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Onboarding is two beats of bar chatter and nothing else — no pop-ups, no info screens.
 *
 * TB-26 explains, and because he is the only character who may acknowledge the player, MEDIC
 * reacts to him apparently talking to nobody. She is the only pilot unlocked this early, so she
 * carries both reactions and they escalate.
 */
class TutorialDefinitionsTest {

    /** Worst-case all-caps budget for the chat column. TB-26 and MEDIC are both 58. */
    private val speakerBudget = 58

    @Test
    fun `beats are handed out in order and then run out`() {
        assertSame(TutorialDefinitions.beats[0], TutorialDefinitions.beatFor(0))
        assertSame(TutorialDefinitions.beats[1], TutorialDefinitions.beatFor(1))
        assertNull("Onboarding must never resurface later", TutorialDefinitions.beatFor(2))
        assertNull(TutorialDefinitions.beatFor(99))
    }

    @Test
    fun `the first beat covers the bars, the currency and the pause`() {
        val text = TutorialDefinitions.beats[0].tbLines.joinToString(" ").lowercase()
        assertTrue("Should name the green bar as health: $text", text.contains("health"))
        assertTrue("Should name the blue bar as shields: $text", text.contains("shields"))
        assertTrue("Should name star dust as the currency: $text", text.contains("star dust"))
        assertTrue("Should name yen: $text", text.contains("yen"))
        assertTrue("Should teach double-tap to pause: $text", text.contains("double-tap"))
    }

    @Test
    fun `the second beat covers earning pilots and spending yen`() {
        val text = TutorialDefinitions.beats[1].tbLines.joinToString(" ").lowercase()
        assertTrue("Pilots are earned, never hired: $text", text.contains("earn"))
        assertTrue("Should mention pilots: $text", text.contains("pilot"))
        assertTrue("Should mention upgrades: $text", text.contains("upgrades"))
        assertTrue("Should mention ships: $text", text.contains("ships"))
    }

    @Test
    fun `medic reacts to both beats and the second is aimed straight at him`() {
        val first = TutorialDefinitions.beats[0].reaction
        val second = TutorialDefinitions.beats[1].reaction

        assertTrue("First reaction asks who he is talking to: $first", first.contains("talking to"))
        assertTrue(
            "Second reaction addresses TB-26 directly rather than commenting about him: $second",
            second.startsWith("You")
        )
        assertNotEquals("The reactions must escalate, not repeat", first, second)
    }

    @Test
    fun `every line fits the chat column so no punchline is ellipsized`() {
        for ((index, beat) in TutorialDefinitions.beats.withIndex()) {
            for (line in beat.tbLines) {
                assertTrue(
                    "TB-26 line in beat $index is ${line.length} chars, over $speakerBudget: $line",
                    line.length <= speakerBudget
                )
            }
            assertTrue(
                "MEDIC reaction in beat $index is ${beat.reaction.length} chars, over $speakerBudget",
                beat.reaction.length <= speakerBudget
            )
        }
    }

    @Test
    fun `beat two teaches the store gesture`() {
        val beatTwo = TutorialDefinitions.beats[1]

        assertTrue(
            "the hold has to be taught somewhere, got ${beatTwo.tbLines}",
            beatTwo.tbLines.any { it.contains("hold to buy", ignoreCase = true) }
        )
    }

    @Test
    fun `the store line names the store, because ships are still bought with a tap`() {
        val line = TutorialDefinitions.beats[1].tbLines.first { it.contains("hold to buy", true) }

        assertTrue("an unscoped line would lie about the shipyard: $line", line.contains("store"))
    }

    @Test
    fun `onboarding is still two beats`() {
        // A third beat would push the yen report to the fourth return — the gate is beatFor(), which
        // is indexed by beat. A line inside an existing beat costs nothing structurally.
        assertEquals(2, TutorialDefinitions.beats.size)
    }
}
