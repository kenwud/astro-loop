package com.astroloop.game.core

import org.junit.Assert.*
import org.junit.Test

/**
 * One button cycles four states, which are the four corners of a 2x2: music on or off, sound
 * effects on or off.
 *
 * Effects means **every** effect, not just the ones made in combat. An earlier version spared
 * interface taps and the slot machine on the theory that they belong to the hangar rather than the
 * fight, which made the "MUSIC ONLY" label a lie — the hangar was still clicking away underneath
 * it. Nothing is exempt now.
 */
class AudioModeTest {

    @Test
    fun `the button cycles through every state and returns to the start`() {
        var mode = AudioMode.ALL
        val seen = mutableListOf(mode)
        repeat(3) {
            mode = mode.next()
            seen.add(mode)
        }
        assertEquals(
            listOf(AudioMode.ALL, AudioMode.NONE, AudioMode.EFFECTS_MUTED, AudioMode.MUSIC_MUTED),
            seen
        )
        assertEquals("A fourth press must return to the start", AudioMode.ALL, mode.next())
    }

    @Test
    fun `every state is reachable by pressing the button`() {
        val reached = mutableSetOf(AudioMode.ALL)
        var mode = AudioMode.ALL
        repeat(AudioMode.values().size) {
            mode = mode.next()
            reached.add(mode)
        }
        assertEquals(AudioMode.values().toSet(), reached)
    }

    @Test
    fun `the four states are the four corners of music by effects`() {
        assertFalse("ALL: music", AudioMode.ALL.musicSilenced)
        assertFalse("ALL: effects", AudioMode.ALL.effectsSilenced)

        assertTrue("NONE: music", AudioMode.NONE.musicSilenced)
        assertTrue("NONE: effects", AudioMode.NONE.effectsSilenced)

        assertFalse("EFFECTS_MUTED: music plays", AudioMode.EFFECTS_MUTED.musicSilenced)
        assertTrue("EFFECTS_MUTED: effects stop", AudioMode.EFFECTS_MUTED.effectsSilenced)

        assertTrue("MUSIC_MUTED: music stops", AudioMode.MUSIC_MUTED.musicSilenced)
        assertFalse("MUSIC_MUTED: effects play", AudioMode.MUSIC_MUTED.effectsSilenced)
    }

    @Test
    fun `EFFECTS_MUTED silences the hangar too, not just the fight`() {
        // The state's whole promise is "music only". Interface taps and the slot machine are
        // effects like any other, and sparing them was what made the label untrue.
        assertTrue(AudioMode.EFFECTS_MUTED.effectsSilenced)
        assertFalse(
            "The music is the one thing this state keeps",
            AudioMode.EFFECTS_MUTED.musicSilenced
        )
    }

    @Test
    fun `only NONE is total silence`() {
        assertTrue(AudioMode.NONE.everythingSilenced)
        assertFalse(AudioMode.ALL.everythingSilenced)
        assertFalse(AudioMode.EFFECTS_MUTED.everythingSilenced)
        assertFalse(AudioMode.MUSIC_MUTED.everythingSilenced)
    }

    @Test
    fun `every state has a readout label so a new state cannot ship without one`() {
        for (mode in AudioMode.values()) {
            assertTrue("$mode has a blank readout label", mode.readoutLabel.isNotBlank())
        }
    }

    @Test
    fun `each label names what remains audible, and now tells the truth`() {
        assertEquals("ALL SOUND", AudioMode.ALL.readoutLabel)
        assertEquals("NO SOUND", AudioMode.NONE.readoutLabel)
        assertEquals("MUSIC ONLY", AudioMode.EFFECTS_MUTED.readoutLabel)
        assertEquals("EFFECTS ONLY", AudioMode.MUSIC_MUTED.readoutLabel)
        val labels = AudioMode.values().map { it.readoutLabel }
        assertEquals("Two states must never read the same", labels.size, labels.toSet().size)
    }

    // `resolve` is shared by PersistenceManager and SoundManager.init, which both read the same
    // preferences file. Duplicating the migration in two places is how they drift apart.

    @Test
    fun `a stored choice wins`() {
        assertEquals(
            AudioMode.EFFECTS_MUTED,
            AudioMode.resolve(storedName = "EFFECTS_MUTED", legacyMuted = true)
        )
    }

    @Test
    fun `with no stored choice the old mute flag decides`() {
        assertEquals(AudioMode.NONE, AudioMode.resolve(storedName = null, legacyMuted = true))
        assertEquals(AudioMode.ALL, AudioMode.resolve(storedName = null, legacyMuted = false))
    }

    @Test
    fun `an unrecognised stored value falls back rather than crashing`() {
        assertEquals(
            "A renamed or corrupt value must not take the sound down with it",
            AudioMode.ALL,
            AudioMode.resolve(storedName = "SOMETHING_ELSE", legacyMuted = false)
        )
    }
}
