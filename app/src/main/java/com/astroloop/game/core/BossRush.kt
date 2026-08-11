package com.astroloop.game.core

/**
 * Pure math for the boss-fight EMP rush-in (both runs).
 *
 * The rule the numbers serve: the rusher always arrives, in a bounded time, from
 * any starting distance, against a target fleeing at full speed the whole way.
 * Everything below follows from that one guarantee.
 */
object BossRush {

    /**
     * Speed the rusher commits to at ignition, fixed for the whole rush.
     * The gap term guarantees arrival within BOSS_RUSH_MAX_CLOSE_TIME even if the
     * target starts thousands of px away and flees at [targetSpeed] the whole way.
     */
    fun rushSpeed(chaserEffectiveSpeed: Float, ignitionGap: Float, targetSpeed: Float): Float =
        maxOf(
            2f * chaserEffectiveSpeed,
            GameConfig.BOSS_RUSH_SPEED_FLOOR,
            ignitionGap / GameConfig.BOSS_RUSH_MAX_CLOSE_TIME + targetSpeed
        )

    /** EMP range: the rush ends and the arrival sequence starts at this gap. */
    fun hasArrived(gap: Float): Boolean = gap <= GameConfig.BOSS_RUSH_TRIGGER_DISTANCE

    /** Linear ignition ramp 0→1 over BOSS_RUSH_EASE_DURATION — the burn visibly ignites. */
    fun easeIn(rushTimer: Float): Float =
        (rushTimer / GameConfig.BOSS_RUSH_EASE_DURATION).coerceIn(0f, 1f)
}
