package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * The desert flashback is where the player learns that TOBAR and TB-26 are the same person.
 *
 * The scene had the name in it twice and then ended on the crystal accusing Astro — "You wanted
 * this." — which read as the game scolding the player through its own villain, and left the
 * TOBAR/TB-26 connection to be inferred later from a bartender's voice.
 *
 * So Astro uses the name where he is being warm or wry, reciprocating the LIEUTENANT Tobar calls
 * him, and the scene's last word from Astro is the name changing in his mouth as it happens.
 */
class DesertNameRevealTest {

    private val astroLines = DesertDefinitions.allLines
        .filter { it.speaker == DesertDefinitions.ASTRO }
        .map { it.text }

    @Test
    fun `Astro names Tobar across the scene, not just at the end`() {
        val named = astroLines.count { it.contains("Tobar") }

        assertEquals(
            "the name has to be theirs before losing it can mean anything",
            5, named
        )
    }

    @Test
    fun `the name is seeded before the crystal scene`() {
        val beforeTheEnd = DesertDefinitions.phase1Lines
            .filter { it.speaker == DesertDefinitions.ASTRO }
            .count { it.text.contains("Tobar") }

        assertTrue("most of the seeding belongs in the drive north", beforeTheEnd >= 4)
    }

    @Test
    fun `Astro's last line is the name changing`() {
        val last = DesertDefinitions.crystalLines.last { it.speaker == DesertDefinitions.ASTRO }

        assertTrue("the old name is reached for first", last.text.contains("Tobar"))
        assertTrue("and the one he will use forever after arrives in the same breath",
            last.text.contains("TB"))
    }

    @Test
    fun `the crystal does not blame Astro for the price`() {
        assertFalse(
            "\"You wanted this\" scolds the player through the villain",
            DesertDefinitions.allLines.any { it.text.contains("You wanted this") }
        )
    }

    @Test
    fun `the crystal closes on the price rather than on an accusation`() {
        assertEquals("The price is paid.", DesertDefinitions.crystalLines.last().text)
        assertEquals(DesertDefinitions.CRYSTAL, DesertDefinitions.crystalLines.last().speaker)
    }

    @Test
    fun `every desert line stays inside the radio width`() {
        // Radio text is <= 35 characters for Exo 2 at 24px, and truncateToFit
        // ellipsizes silently past that. An ellipsized reveal is a dead reveal.
        for (line in DesertDefinitions.allLines) {
            assertTrue(
                "'${line.text}' is ${line.text.length} chars, over the 35 budget",
                line.text.length <= 35
            )
        }
    }
}
