package com.astroloop.game.hangar

/**
 * The hold-to-buy gesture for store tiles, as a pure clock.
 *
 * Kept free of Android types so the rule — how long, what cancels it, how often it may fire — is
 * unit-testable without a device. The view owns the touch events and the renderer owns the fill;
 * this owns only the decision.
 *
 * **One purchase per hold.** [advance] returns true on the single frame the threshold is crossed
 * and then goes idle, so a finger left on the tile cannot spend twice. Auto-repeat was considered
 * and rejected: the fill exists to make an accidental spend recoverable, and repeating while held
 * would reintroduce the problem it solves.
 */
class HoldToBuy(private val thresholdSeconds: Float = DEFAULT_THRESHOLD_SECONDS) {

    companion object {
        /**
         * Long enough that a tap cannot reach it, short enough not to feel like a punishment.
         *
         * Raised from 0.5s on 2026-08-09 after holding tiles on a device: half a second completed
         * before the fill read as progress, so the purchase felt like something that happened to
         * the player rather than something they did. The fill's sweep is derived from this, so
         * changing it changes both.
         */
        const val DEFAULT_THRESHOLD_SECONDS = 1.0f

        /**
         * How long a press may last and still count as a tap.
         *
         * A store press has three outcomes, not two. Before this existed, *any* release under the
         * buy threshold was a tap, so abandoning a purchase and tapping to read were the same
         * event — there was no way to start buying and change your mind, which is exactly what the
         * owner objected to on 2026-08-09.
         *
         * 250ms comes off the platform and off this game. Android's `ViewConfiguration` puts a tap
         * at 100ms and a long press at 500ms, and a natural tap releases in 80-120ms; this game
         * already widened its own double-tap window from 300ms to 400ms "for comfortable
         * double-tap", so its hand runs slightly slow. A quarter second is about double a natural
         * tap and still well inside a long press.
         *
         * The fill starts here too, which is the point: the moment a card stops being tappable is
         * the moment the fill appears, so the rule is visible rather than invisible. If you can see
         * the fill, letting go cancels.
         */
        const val TAP_SECONDS = 0.25f

        /** Whether a press released after [heldSeconds] should be treated as a tap. */
        fun isTap(heldSeconds: Float): Boolean = heldSeconds <= TAP_SECONDS
    }

    var index: Int = -1
        private set

    private var elapsed: Float = 0f

    val isActive: Boolean get() = index >= 0

    /** How long the current press has lasted. Zero when nothing is held. */
    val heldSeconds: Float get() = if (!isActive) 0f else elapsed

    /**
     * How full the fill should be drawn, 0..1.
     *
     * Zero until [TAP_SECONDS], then sweeping the rest of the way to the purchase. The fill and the
     * tap window share that boundary on purpose: while a release would still flip the card there is
     * nothing to see, and the instant it would not, the fill appears.
     */
    val progress: Float
        get() {
            if (!isActive || elapsed <= TAP_SECONDS) return 0f
            val span = thresholdSeconds - TAP_SECONDS
            return ((elapsed - TAP_SECONDS) / span).coerceIn(0f, 1f)
        }

    /**
     * Whether the tile is visibly filling — the cue for the hold's haptic hum.
     *
     * Shares [progress]'s boundary rather than restating it, so the hum can never start before
     * the fill does: the two are one lesson, "if you can feel or see this, letting go cancels."
     */
    val isFilling: Boolean get() = progress > 0f

    /** Begin holding [tileIndex], abandoning any hold already in flight. */
    fun start(tileIndex: Int) {
        index = tileIndex
        elapsed = 0f
    }

    /** Abandon the hold with no purchase. Called on release, on drag, and on cancel. */
    fun cancel() {
        index = -1
        elapsed = 0f
    }

    /**
     * Advance the clock.
     *
     * @return true on the one frame the threshold is crossed — the caller's cue to buy. The hold
     *   goes idle immediately afterwards, so this can never return true twice for one gesture.
     */
    fun advance(deltaTime: Float): Boolean {
        if (!isActive) return false
        elapsed += deltaTime
        if (elapsed < thresholdSeconds) return false
        cancel()
        return true
    }
}
