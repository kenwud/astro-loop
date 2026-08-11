package com.astroloop.game.hangar

import kotlin.math.abs

/**
 * What a moving finger has committed to in the hangar, and whether a store hold survives it.
 *
 * Kept free of Android types so the rules are unit-testable without a device — the view owns the
 * `MotionEvent`s, this owns the decisions.
 *
 * **The slop is the platform's, not a constant.** Callers pass
 * `ViewConfiguration.get(context).scaledTouchSlop`, which is 8dp in real pixels, so the thresholds
 * scale with the screen. A hard-coded pixel figure means one thing on a phone and another on a
 * tablet, and the hangar previously used 15px — about 5dp on a modern phone, tighter than the
 * platform's own minimum for calling something a drag.
 *
 * **A hold gets a bigger budget than a swipe.** Both Android and iOS separate the two, because a
 * finger held still for a second drifts more than one about to flick: Apple's
 * `UILongPressGestureRecognizer.allowableMovement` defaults to 10 points against an 8dp scroll
 * slop. Here the hold's budget is the pressed tile itself, which is the rule a player already
 * expects from every button they have used — drift as much as you like, but leave the control and
 * you have let go of it.
 */
object HangarGestures {

    /**
     * A page swipe needs **horizontal** travel.
     *
     * The hangar's three rooms are tiled side by side, so sideways is the only direction that
     * scrolls. Vertical drift used to start a "page swipe" too, which scrolled by a dx of roughly
     * zero — invisible, but it cancelled any store hold in progress. That was the purchase being
     * taken away for a gesture the page cannot even perform.
     */
    fun startsPageSwipe(totalDx: Float, totalDy: Float, slop: Float): Boolean = abs(totalDx) > slop

    /**
     * A ship drag needs vertical travel that beats the horizontal, and a ship under the finger.
     *
     * Shipyard only, which is why a store hold never has to compete with it.
     */
    fun startsShipDrag(
        totalDx: Float,
        totalDy: Float,
        slop: Float,
        shipDragPossible: Boolean
    ): Boolean = shipDragPossible && abs(totalDy) > slop && abs(totalDy) > abs(totalDx)

    /** Whether a hold started on this tile is still on it. */
    fun holdSurvivesDrift(
        x: Float,
        y: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Boolean = x >= left && x <= right && y >= top && y <= bottom
}
