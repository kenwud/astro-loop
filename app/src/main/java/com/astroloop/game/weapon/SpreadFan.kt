package com.astroloop.game.weapon

/**
 * Angle offsets for a spread that always keeps one shot on the aim line.
 *
 * The obvious formula — `step × (i − (count−1)/2)` — is symmetric but only puts a projectile dead
 * centre when the count is **odd**. At even counts it straddles: two pellets either side of the
 * target and nothing down the middle, which on an auto-aiming weapon means it deliberately misses
 * what it just finished aiming at.
 *
 * That parity is not under the player's control. Projectile counts here are `level +
 * state.extraProjectiles`, so an unrelated passive pickup can flip a weapon between aiming true
 * and straddling. Putting the guarantee in the angle formula rather than in the count is what makes
 * that impossible: no arithmetic elsewhere can re-break it.
 *
 * **Shape:** one pellet at the centre, the rest paired outward. An even count leaves one over,
 * whose side [mirrored] flips between volleys so the lean averages out rather than always favouring
 * the same flank. Odd counts have no leftover and are identical to the old symmetric formula, so
 * the levels that already felt right are untouched.
 */
object SpreadFan {

    /**
     * [count] offsets in the same units as [step], centre first.
     *
     * @param mirrored flips the side of the leftover pellet on even counts. Alternate it per shot.
     */
    fun offsets(count: Int, step: Float, mirrored: Boolean): List<Float> {
        if (count <= 0) return emptyList()

        val out = ArrayList<Float>(count)
        out.add(0f)

        val remaining = count - 1
        val pairs = remaining / 2
        for (ring in 1..pairs) {
            out.add(-ring * step)
            out.add(ring * step)
        }
        if (remaining % 2 == 1) {
            val ring = pairs + 1
            out.add(if (mirrored) -ring * step else ring * step)
        }
        return out
    }
}
