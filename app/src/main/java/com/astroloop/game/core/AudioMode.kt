package com.astroloop.game.core

/**
 * What the audio button is currently silencing.
 *
 * A player asked to turn off the gunfire while keeping the music, which the single global mute
 * could not express. Rather than grow a row of switches, one button cycles four states — the four
 * corners of a 2x2: music on or off, sound effects on or off.
 *
 * **Effects means every effect.** An earlier version spared interface taps and the slot machine, on
 * the theory that they belong to the hangar rather than the fight. That made [EFFECTS_MUTED]'s
 * "MUSIC ONLY" label untrue — the hangar carried on clicking underneath it — and it needed a list of
 * exceptions that had to be maintained by hand. Nothing is exempt now, and the labels are honest.
 */
enum class AudioMode {

    /** Everything plays. */
    ALL,

    /** Silence. The state the old global mute became. */
    NONE,

    /** Music keeps playing and nothing else does. The state that prompted all of this. */
    EFFECTS_MUTED,

    /** Every effect stays audible, the music stops. */
    MUSIC_MUTED;

    /** The next state on the cycle, wrapping back to [ALL]. */
    fun next(): AudioMode = values()[(ordinal + 1) % values().size]

    val musicSilenced: Boolean get() = this == NONE || this == MUSIC_MUTED

    /**
     * Whether sound effects are silenced — all of them, wherever they play.
     *
     * `SoundManager` asks this in two places: when a sound is requested, and again when a sound
     * that was still loading finally arrives. One property rather than a rule spelled out twice,
     * because that is how the deferred path ends up disagreeing with the immediate one.
     */
    val effectsSilenced: Boolean get() = this == NONE || this == EFFECTS_MUTED

    /**
     * True only for [NONE].
     *
     * Not a gate on playback — [effectsSilenced] and [musicSilenced] cover that between them. This
     * is for the button's icon, where total silence earns the strikethrough and a half-silenced
     * state does not.
     */
    val everythingSilenced: Boolean get() = this == NONE

    /**
     * What the slot machine's readout says when the player arrives at this state.
     *
     * Names what remains **audible** rather than what was silenced: for a four-state cycle that
     * reads better, because the player never has to invert it. Lives on the enum so a fifth state
     * cannot compile without someone writing its label.
     */
    val readoutLabel: String
        get() = when (this) {
            ALL -> "ALL SOUND"
            NONE -> "NO SOUND"
            EFFECTS_MUTED -> "MUSIC ONLY"
            MUSIC_MUTED -> "EFFECTS ONLY"
        }

    companion object {
        /**
         * The mode to use given what is stored, falling back to the pre-1.2 `audio_muted` boolean.
         *
         * Shared by `PersistenceManager` and `SoundManager.init`, which read the same preferences
         * file at different moments — duplicating the fallback in both is how they drift apart.
         * An unrecognised stored value resolves to [ALL] rather than throwing: a corrupt setting
         * should not take the game's sound down with it.
         */
        fun resolve(storedName: String?, legacyMuted: Boolean): AudioMode =
            storedName?.let { name -> values().firstOrNull { it.name == name } }
                ?: if (legacyMuted) NONE else ALL
    }
}
