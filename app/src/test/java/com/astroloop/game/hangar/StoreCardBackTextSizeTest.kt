package com.astroloop.game.hangar

import org.junit.Assert.*
import org.junit.Test

/**
 * The card back may never be set in smaller type than the tile front.
 *
 * Owner rule, 2026-08-09, after reading the backs on a device: "the copy is hard to read, let's
 * make it bigger, text can be no smaller than the text used on the front."
 *
 * The trap this guards is not the scale factor but the **clamp**. Every size in this renderer is
 * `(tileSize * f).coerceIn(min, max)`, and on any ordinary phone the tile is large enough that the
 * `max` is what actually binds — so the back read 14-18px against the front's 22px even though its
 * factors looked close. Lowering a factor is harmless; lowering a cap is what makes the copy
 * unreadable, and a factor-only comparison would not catch it. These assertions compare the
 * resolved size at real tile widths.
 */
class StoreCardBackTextSizeTest {

    /** Tile widths spanning a small phone, a design-width phone and a large-screen column. */
    private val tileSizes = listOf(120f, 180f, 240f, 303f, 400f, 600f)

    @Test
    fun `back body text is never smaller than the front's smallest text`() {
        for (tileSize in tileSizes) {
            val front = StoreTextSizes.frontBody(tileSize)
            for ((name, size) in listOf(
                "effect figures" to StoreTextSizes.backEffect(tileSize),
                "next-level deltas" to StoreTextSizes.backNext(tileSize),
                "description" to StoreTextSizes.backDetail(tileSize)
            )) {
                assertTrue(
                    "at tileSize $tileSize the back's $name is ${size}px against the front's ${front}px",
                    size >= front
                )
            }
        }
    }

    @Test
    fun `the back's title is at least as large as the front's title`() {
        for (tileSize in tileSizes) {
            assertTrue(
                "at tileSize $tileSize: back title ${StoreTextSizes.backTitle(tileSize)}px vs " +
                    "front title ${StoreTextSizes.frontTitle(tileSize)}px",
                StoreTextSizes.backTitle(tileSize) >= StoreTextSizes.frontTitle(tileSize)
            )
        }
    }

    @Test
    fun `sizes grow with the tile rather than sitting on the floor`() {
        // A guard against someone "fixing" a clamp by pinning every size to its minimum.
        assertTrue(
            StoreTextSizes.backDetail(600f) > StoreTextSizes.backDetail(120f)
        )
    }

    @Test
    fun `the line step leaves room for the text it now carries`() {
        // The step was authored for 14px copy at a fixed fraction of the tile and kept 1.5x
        // leading. The copy is larger now, so the step has to stay ahead of it — but not by so
        // much that the back runs off the bottom of the tile, which is what S9 checks on a device.
        for (tileSize in tileSizes) {
            val step = StoreTextSizes.lineStep(tileSize)
            val text = StoreTextSizes.backEffect(tileSize)
            assertTrue("at tileSize $tileSize the step $step is tighter than the text $text",
                step > text)
            assertTrue("at tileSize $tileSize the step $step is more than 1.6x the text $text",
                step <= text * 1.6f)
        }
    }
}
