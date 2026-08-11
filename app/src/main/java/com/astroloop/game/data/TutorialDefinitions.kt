package com.astroloop.game.data

/**
 * Onboarding, delivered as bar chatter.
 *
 * Players arriving from F-Droid reported not realising the star-like pickups were currency or
 * that the green and blue bars were health and shields, and separately that double-tapping to
 * pause was undiscoverable. The fix stays inside the fiction: no pop-ups and no info screens,
 * which would break the one thing the bar is for.
 *
 * TB-26 does the explaining because he is the only character permitted to acknowledge the player
 * at all. That has a cost the lines have to pay: to everyone else in the bar he is talking to
 * nobody, so each beat carries MEDIC's reaction to it. She is the only pilot unlocked this early,
 * and her two reactions escalate — first confusion, then addressing him directly.
 *
 * Two beats only. Three tutorials in a row is a lecture, and the third fact (pause) rides along
 * with the first beat rather than earning its own.
 */
object TutorialDefinitions {

    /**
     * One onboarding beat: what TB-26 says on a return, and how MEDIC answers it.
     *
     * [reaction] replaces MEDIC's usual déjà vu line on these returns rather than following it —
     * the sequence is already three lines long and short is the brief.
     */
    data class TutorialBeat(
        val tbLines: List<String>,
        val reaction: String
    )

    /**
     * Delivered on the first and second returns from a flight, in order.
     *
     * Line lengths are bounded by the chat column: the `[CALLSIGN]: ` prefix eats into it, and
     * `truncateToFit` ellipsizes anything longer. TB-26 and MEDIC both budget 58 characters
     * worst-case, and a test holds every line to it — an ellipsized punchline is a dead one.
     */
    val beats: List<TutorialBeat> = listOf(
        TutorialBeat(
            tbLines = listOf(
                "Green is health, blue is shields.",
                "Star dust is yen. Don't leave it.",
                "Double-tap to pause. I never do."
            ),
            reaction = "...Who are you talking to?"
        ),
        TutorialBeat(
            tbLines = listOf(
                "You don't hire pilots. You earn them.",
                "Yen buys upgrades and new ships.",
                "In the store: tap to read, hold to buy."
            ),
            reaction = "You're doing it again."
        )
    )

    /**
     * The beat owed on this return, or null once onboarding is finished.
     *
     * [tutorialsShown] is the count already delivered, so it doubles as the index. Returning null
     * past the end is what stops onboarding resurfacing at hour twenty.
     */
    fun beatFor(tutorialsShown: Int): TutorialBeat? = beats.getOrNull(tutorialsShown)
}
