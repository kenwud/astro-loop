package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * The desert flashback offers a moral choice: once Tobar says "We should stop.", stopping or
 * retreating south reaches the good ending, and driving north reaches the horror path.
 *
 * On the **first** pass that choice is withheld. Tobar never says the line, the branch never
 * arms, and the village is destroyed. The choice returns on every later pass, so the good
 * ending is something the loop lets you reach the second time round rather than something
 * available immediately.
 *
 * That works because the desert recurs: the horror path calls incrementStoryLoop() and sends
 * the player back around the arc, while the good ending is terminal and enters ASTRO_LOOP.
 */
class DesertScriptTest {

    @Test
    fun `the first pass through the desert is not a choice`() {
        assertTrue(DesertDefinitions.isForcedHorror(storyLoop = 1))
    }

    @Test
    fun `later passes restore the choice`() {
        assertFalse(DesertDefinitions.isForcedHorror(storyLoop = 2))
        assertFalse(DesertDefinitions.isForcedHorror(storyLoop = 3))
    }

    @Test
    fun `the forced script drops Tobar's stop line entirely`() {
        val forced = DesertDefinitions.phase2LinesFor(forcedHorror = true)
        assertTrue(
            "The stop check must never arm on the forced pass",
            forced.none { it.trigger == DesertDefinitions.DesertTrigger.STOP_CHECK }
        )
        assertTrue(
            "No replacement line: Tobar simply does not say it",
            forced.none { it.text.contains("stop", ignoreCase = true) }
        )
    }

    @Test
    fun `the ordinary script keeps Tobar's stop line`() {
        val ordinary = DesertDefinitions.phase2LinesFor(forcedHorror = false)
        assertEquals(
            "The ordinary pass is the unmodified script",
            DesertDefinitions.phase2Lines, ordinary
        )
        assertTrue(
            ordinary.any { it.trigger == DesertDefinitions.DesertTrigger.STOP_CHECK }
        )
    }

    @Test
    fun `only the stop line is withheld and the escalation is untouched`() {
        val ordinary = DesertDefinitions.phase2LinesFor(forcedHorror = false)
        val forced = DesertDefinitions.phase2LinesFor(forcedHorror = true)

        assertEquals(ordinary.size - 1, forced.size)
        assertEquals(
            "The lines that remain keep their order and content",
            ordinary.filter { it.trigger != DesertDefinitions.DesertTrigger.STOP_CHECK },
            forced
        )
    }
}
