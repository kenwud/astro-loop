package com.astroloop.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.core.AudioMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The audio button used to be a single on/off mute stored as `audio_muted`. Players already have
 * that flag set, so the four-state mode has to inherit from it rather than silently switching
 * someone's sound back on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersistenceAudioModeTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("astrohunt_save", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun persistence() = PersistenceManager(context)

    /** Writes the pre-existing flag directly, the way an installed copy of 1.1 would have it. */
    private fun writeLegacyMute(muted: Boolean) {
        context.getSharedPreferences("astrohunt_save", Context.MODE_PRIVATE)
            .edit().putBoolean("audio_muted", muted).commit()
    }

    @Test
    fun `a fresh install plays everything`() {
        assertEquals(AudioMode.ALL, persistence().getAudioMode())
    }

    @Test
    fun `the chosen mode survives a restart`() {
        persistence().setAudioMode(AudioMode.EFFECTS_MUTED)

        assertEquals(AudioMode.EFFECTS_MUTED, persistence().getAudioMode())
    }

    @Test
    fun `someone who had muted the game stays muted`() {
        writeLegacyMute(true)

        assertEquals(
            "Turning a muted player's sound back on would be rude",
            AudioMode.NONE, persistence().getAudioMode()
        )
    }

    @Test
    fun `someone who had not muted the game is unaffected`() {
        writeLegacyMute(false)

        assertEquals(AudioMode.ALL, persistence().getAudioMode())
    }

    @Test
    fun `an explicit choice overrides the legacy flag`() {
        writeLegacyMute(true)
        persistence().setAudioMode(AudioMode.MUSIC_MUTED)

        assertEquals(
            "Once the player uses the new button, the old flag stops mattering",
            AudioMode.MUSIC_MUTED, persistence().getAudioMode()
        )
    }

    @Test
    fun `resetting progress leaves the audio setting alone`() {
        val p = persistence()
        p.setAudioMode(AudioMode.MUSIC_MUTED)

        p.resetAllProgress()

        assertEquals(
            "Sound is a preference, not progress",
            AudioMode.MUSIC_MUTED, p.getAudioMode()
        )
    }
}
