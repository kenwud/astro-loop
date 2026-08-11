package com.astroloop.game.hangar

/**
 * Fits the card back's content inside its tile.
 *
 * Since the copy was raised to the front's size, the type no longer shrinks with the tile — it
 * floors at 13px — so a narrower tile wraps the description into more lines while every line stays
 * the same height. Something has to give, and it is deliberately not the type: the whole point of
 * the change was that the back was unreadable.
 *
 * So the layout gives, in this order:
 *  1. **Leading tightens**, from a comfortable 1.35x the type down to 1.15x.
 *  2. **The next-level block is dropped.**
 *
 * Current values and the description are what survive, because they answer the two questions the
 * card exists for — what do I have, and what does this do. The next-level figure is the one thing
 * the front already gestures at with its cost row.
 */
internal object StoreBackLayout {

    /** Where the content starts, as a fraction of the tile — clear of the name-and-level header. */
    const val TOP = 0.28f

    /**
     * Where the cost row sits, on **both faces**.
     *
     * The back grew a price on 2026-08-11 so a player deciding whether to buy does not have to
     * turn the card back over to find out what it costs. Shared rather than repeated in the
     * renderer, because the whole point is that the figure does not appear to move when the card
     * turns — two copies of 0.88f would have drifted the first time either face was adjusted.
     */
    const val PRICE_BASELINE = 0.88f

    /**
     * The lowest a content baseline may sit.
     *
     * **Was 0.94 until the price moved onto the back.** The price is set in `frontBody` type,
     * whose glyphs rise roughly 0.7 of their size above the baseline, so at 0.16 of the tile the
     * row starts near `0.88 - 0.112 = 0.768`. Content has to finish above that with room for its
     * own descenders, which is what 0.74 buys.
     *
     * The cost of that is paid in leading: on an ordinary phone tile (~342px on a 1080-wide
     * screen) the worst card — Haul Line below max — still keeps its next-level block, but only at
     * the tight 1.15x step rather than the roomy 1.35x. Smaller tiles drop the next block, which
     * is the order of sacrifice this object already documents, and it reads better now than it did
     * before: the next-level figure was justified as the one thing "the front already gestures at
     * with its cost row", and the back now carries that row itself.
     */
    const val BOTTOM_LIMIT = 0.74f

    /**
     * The smallest tile that can carry the worst card **and** the price row.
     *
     * Below roughly 210px the two collide, and it is arithmetic rather than tuning: `frontBody`
     * *caps* at 22px, so on a small tile the type does not shrink with it — a 180px tile carries
     * the same 22px lines a 600px one does, in a third of the height. The worst card needs about
     * 106px of content at the tightest arrangement available, and a 180px tile only has 83px above
     * the price.
     *
     * Real hardware clears it: the narrowest plausible phone, 360dp at 2x, gives a 222px tile.
     * A 320dp device would not, which is why this wants checking on the narrowest screen to
     * hand rather than on the arithmetic alone — if the description ever
     * runs into the price on a real screen, the next rung of the sacrifice ladder is truncating
     * the description with the `…` convention the weapon cards already use.
     */
    const val MIN_TILE_FOR_PRICE = 210f

    private const val LEADING_ROOMY = 1.35f
    private const val LEADING_TIGHT = 1.15f

    /** Gap between blocks, as a fraction of one line step. */
    const val BLOCK_GAP = 0.4f

    /** The description sits on slightly tighter leading than the figures above it. */
    const val DETAIL_LEADING = 0.9f

    data class Plan(val lineStep: Float, val showNext: Boolean)

    /**
     * The tightest arrangement that fits, or the tightest available if nothing does.
     *
     * [detailLines] comes from the renderer, which has the Paint needed to measure the wrap.
     */
    fun plan(tileSize: Float, effectLines: Int, nextLines: Int, detailLines: Int): Plan {
        val text = StoreTextSizes.backEffect(tileSize)
        val candidates = listOf(
            Plan(text * LEADING_ROOMY, showNext = nextLines > 0),
            Plan(text * LEADING_TIGHT, showNext = nextLines > 0),
            Plan(text * LEADING_ROOMY, showNext = false),
            Plan(text * LEADING_TIGHT, showNext = false)
        )
        return candidates.firstOrNull { candidate ->
            lastBaseline(tileSize, candidate, effectLines, nextLines, detailLines) <=
                tileSize * BOTTOM_LIMIT
        } ?: candidates.last()
    }

    /**
     * Where the last line's baseline lands for [plan], in pixels from the top of the tile.
     *
     * Mirrors the renderer's advances exactly — if one changes, so must the other, which is why
     * they are tested together rather than eyeballed.
     */
    fun lastBaseline(
        tileSize: Float,
        plan: Plan,
        effectLines: Int,
        nextLines: Int,
        detailLines: Int
    ): Float {
        val step = plan.lineStep
        var y = tileSize * TOP
        var last = y

        if (effectLines > 0) {
            last = y + step * (effectLines - 1)
            y += step * effectLines
        }
        if (plan.showNext && nextLines > 0) {
            y += step * BLOCK_GAP
            last = y + step * (nextLines - 1)
            y += step * nextLines
        }
        if (detailLines > 0) {
            y += step * BLOCK_GAP
            last = y + step * DETAIL_LEADING * (detailLines - 1)
        }
        return last
    }
}
