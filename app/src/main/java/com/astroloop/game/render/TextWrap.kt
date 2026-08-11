package com.astroloop.game.render

/**
 * Word wrapping for card copy.
 *
 * Greedy wrapping fills line one to the brim and leaves whatever is left over on line two, which
 * on a two-line description reads as a mistake — "Everything you fire hits" / "harder.". When the
 * text needs exactly two lines, the break is moved to wherever the two come out closest in width.
 *
 * **Three or more lines are left greedy on purpose.** Balancing those would change how many lines
 * a description occupies, and the store card backs are laid out against a vertical budget that
 * counts them, and the worst card has no room to absorb another. Two lines rebalanced are still
 * two lines.
 *
 * Measurement is a lambda rather than a `Paint` so the layout is testable without a device.
 */
object TextWrap {

    /** Wrap [text] to [maxWidth], balancing the break if the result is exactly two lines. */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> =
        text.split('\n').flatMap { segment ->
            val greedy = greedy(segment, maxWidth, measure)
            if (greedy.size == 2) balance(segment, maxWidth, measure) ?: greedy else greedy
        }

    /**
     * [lines] cut to [max], with the last one marked if anything was dropped.
     *
     * Cards have a fixed number of rows, and dropping the surplus in silence makes copy that
     * outgrew its space look like copy that was written badly — which is exactly how two weapon
     * descriptions came to be reported as broken. An ellipsis does not create room; it makes the
     * overflow visible to whoever is looking at it.
     */
    fun clamp(lines: List<String>, max: Int): List<String> {
        if (lines.size <= max) return lines
        val kept = lines.take(max).toMutableList()
        kept[max - 1] = kept[max - 1].trimEnd() + "…"
        return kept
    }

    /** Classic fill-then-break. A word wider than the column gets a line to itself. */
    fun greedy(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (word in text.split(" ").filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || measure(candidate) <= maxWidth) {
                line = candidate
            } else {
                out.add(line)
                line = word
            }
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }

    /**
     * The two-line split whose halves come out closest in width, or null if no split fits — a word
     * too wide for the column has no balanced arrangement, and greedy's overflow is then the only
     * honest answer.
     */
    private fun balance(text: String, maxWidth: Float, measure: (String) -> Float): List<String>? {
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.size < 2) return null

        var best: List<String>? = null
        var bestDelta = Float.MAX_VALUE
        for (split in 1 until words.size) {
            val first = words.subList(0, split).joinToString(" ")
            val second = words.subList(split, words.size).joinToString(" ")
            val firstWidth = measure(first)
            val secondWidth = measure(second)
            if (firstWidth > maxWidth || secondWidth > maxWidth) continue

            val delta = kotlin.math.abs(firstWidth - secondWidth)
            if (delta < bestDelta) {
                bestDelta = delta
                best = listOf(first, second)
            }
        }
        return best
    }
}
