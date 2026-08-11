package com.astroloop.game.hangar

/**
 * Type sizes for the store's tiles, front and back, resolved from the tile's width.
 *
 * Lifted out of `StorePageRenderer` so the one rule that binds them can be tested: **nothing on the
 * back may be set smaller than the front.** Owner rule, 2026-08-09, after reading the backs on a
 * device.
 *
 * The reason that rule needed a home is the clamp rather than the factor. Every size here is
 * `(tileSize * f).coerceIn(min, max)`, and on any ordinary phone the tile is wide enough that
 * **`max` is what actually binds** — so a back authored at `0.10f..0.14f` with caps of `14f..18f`
 * resolved to 14-18px against a front resolving to 22px, even though the factors looked close.
 * Comparing factors would have missed it entirely; [StoreCardBackTextSizeTest] compares the
 * resolved sizes at real tile widths.
 */
internal object StoreTextSizes {

    /** The tile name on the front. */
    fun frontTitle(tileSize: Float): Float = (tileSize * 0.17f).coerceIn(14f, 24f)

    /** The front's effect line and cost row — the smallest type the player reads on a tile face. */
    fun frontBody(tileSize: Float): Float = (tileSize * 0.16f).coerceIn(13f, 22f)

    /** The back's name-and-level header. */
    fun backTitle(tileSize: Float): Float = frontTitle(tileSize)

    /** The accumulated figures — "+45% pickup range". */
    fun backEffect(tileSize: Float): Float = frontBody(tileSize)

    /** The next-level deltas — "next +15% pickup range". */
    fun backNext(tileSize: Float): Float = frontBody(tileSize)

    /** The description sentence. */
    fun backDetail(tileSize: Float): Float = frontBody(tileSize)

    /**
     * Baseline-to-baseline step on the back.
     *
     * Derived from the type rather than from the tile. The previous step was a flat
     * `tileSize * 0.11f`, which happened to give 14px copy about 1.5x leading and would have given
     * the larger copy less than its own height at small tile widths. Tying it to the text keeps
     * the ratio constant at every screen size — and because the old step was over-generous for the
     * type it carried, the back has *more* vertical room now than it did with smaller copy.
     */
    fun lineStep(tileSize: Float): Float = backEffect(tileSize) * 1.35f
}
