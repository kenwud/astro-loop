package com.astroloop.game.data

import com.astroloop.game.core.GameConfig
import kotlin.math.roundToInt

/**
 * The permanent upgrades sold in the hangar store, in the order they are laid out.
 *
 * Lifted out of `StorePageRenderer.drawStorePage` so the copy is testable and sits with every other
 * definitions object. **The order is the layout** — the renderer walks this list and derives each
 * tile's row and column from its index across a 3-wide grid, so reordering moves tiles on screen.
 *
 * The store's fiction is black-market salvage, and the names carry it. `Salvage Plate` (hull) and
 * `Scavenger Rig` (drops) reading as near-synonyms is deliberate flavour, not an oversight.
 */
object StoreUpgradeDefinitions {

    /** How a value is written on a card back. */
    enum class Format { FLAT, PERCENT }

    /**
     * What kind of number a card can honestly print for an effect.
     *
     * Only some of the eight tiles have an absolute worth showing. Speed's underlying unit is
     * pixels per second, the magnet's is pixels, yen and salvage are rate multipliers, and damage
     * has no single base at all — it is per weapon. Printing those raw would be worse than
     * printing nothing.
     */
    enum class Readout {
        /** A stat the ship already has some of: "Health 80". */
        FROM_BASE,
        /** A real absolute that starts at zero: "Crit 15%". */
        ABSOLUTE,
        /** No meaningful absolute, so state the gain: "+15% ship speed". */
        RELATIVE
    }

    /**
     * One line of a tile's effect.
     *
     * [perLevel] is the amount a single level grants. The card multiplies it by the owned level for
     * the "now" figure and prints it bare for the "next" figure, so there is exactly one number here
     * and no second copy to drift.
     *
     * [shown] exists for the magnet's pull speed. The owner cut that number from the card as not
     * relevant to players, but the game still applies it, and `StoreUpgradeAgreementTest` pins it
     * against `GameState` — so it stays in the data and is skipped at the point of printing rather
     * than deleted, which would quietly drop the guard.
     */
    data class UpgradeEffect(
        val label: String,
        val perLevel: Float,
        val format: Format,
        val readout: Readout = Readout.RELATIVE,
        val base: Float = 0f,
        val shown: Boolean = true
    ) {
        // roundToInt(), not toInt(): the perLevel constants in use today all land on whole numbers,
        // but nothing about the type guarantees that — a future perLevel like 0.29f would truncate
        // its level-1 figure down a point with a green suite next to it. See
        // StoreUpgradeDefinitionsTest for the pinned value that would fail under truncation.
        private fun amount(level: Int): Int = when (format) {
            Format.FLAT -> (perLevel * level).roundToInt()
            Format.PERCENT -> (perLevel * level * 100f).roundToInt()
        }

        /** The figure as it appears on the card at [level]. */
        fun at(level: Int): String = when (readout) {
            Readout.FROM_BASE -> "$label ${(base + perLevel * level).roundToInt()}"
            Readout.ABSOLUTE -> "$label ${amount(level)}%"
            Readout.RELATIVE -> when (format) {
                Format.FLAT -> "+${amount(level)} $label"
                Format.PERCENT -> "+${amount(level)}% $label"
            }
        }

        /** What one more level adds, with no label — it sits under the figure it belongs to. */
        fun delta(): String = when (format) {
            Format.FLAT -> "+${amount(1)}"
            Format.PERCENT -> "+${amount(1)}%"
        }

        /**
         * Whether this figure says anything at [level].
         *
         * An absolute is information even at zero — "Health 50" is the ship you are flying now, and
         * showing it was the point of the change. A relative one at zero is "+0% ship speed", which
         * is noise on the card of exactly the player who has never bought this tile.
         */
        fun statesSomethingAt(level: Int): Boolean =
            shown && (readout != Readout.RELATIVE || level > 0)
    }

    /**
     * @param id the key the level is persisted under, and `null` for a tile that is not bought.
     * @param detail one plain sentence of what the upgrade does, shown on the card back. Literal
     *   rather than flavoured: the back exists to answer a purchase question, and the tile names
     *   already carry the atmosphere.
     * @param effects usually one line; the magnet is the only tile granting two separate things.
     */
    data class UpgradeTile(
        val name: String,
        val id: String?,          // null = NG+ locked
        val effect: String,
        val isNgPlus: Boolean = false,
        val detail: String = "",
        val effects: List<UpgradeEffect> = emptyList()
    ) {
        /**
         * The figures the card prints at [level], one string per shown effect.
         *
         * Absolutes appear at every level, including zero — "Health 50" is the ship you fly today.
         * Relatives drop out at zero, where they would only say "+0%".
         */
        fun effectsAt(level: Int): List<String> =
            effects.filter { it.statesSomethingAt(level) }.map { it.at(level) }

        /** What one more level buys, one string per shown effect. */
        fun nextDeltas(): List<String> = effects.filter { it.shown }.map { it.delta() }
    }

    val tiles = listOf(
        // Row 1 — Survival
        UpgradeTile(
            "Salvage Plate", "health", "+10 max health",
            detail = "Survive more hits.",
            effects = listOf(
                UpgradeEffect("Health", 10f, Format.FLAT, Readout.FROM_BASE, base = GameConfig.SHIP_BASE_HEALTH)
            )
        ),
        UpgradeTile(
            "Deflector Rig", "shields", "+10 max shield",
            detail = "Absorbs more hits.",
            effects = listOf(
                UpgradeEffect("Shield", 10f, Format.FLAT, Readout.FROM_BASE, base = GameConfig.SHIP_BASE_SHIELDS)
            )
        ),
        UpgradeTile(
            "Nitro Boost", "speed", "+5% ship speed",
            detail = "You move faster.",
            effects = listOf(UpgradeEffect("ship speed", 0.05f, Format.PERCENT))
        ),
        // Row 2 — Combat
        UpgradeTile(
            "Hot Rounds", "damage", "+5% all damage",
            detail = "Everything you fire hits harder.",
            effects = listOf(UpgradeEffect("all damage", 0.05f, Format.PERCENT))
        ),
        UpgradeTile(
            "Lucky Rounds", "crit", "+5% crit chance",
            detail = "Some shots hit for double.",
            effects = listOf(
                UpgradeEffect("Crit", 0.05f, Format.PERCENT, Readout.ABSOLUTE)
            )
        ),
        UpgradeTile(
            "Haul Line", "magnet", "+15% pickup range",
            detail = "Collects star dust from further out.",
            effects = listOf(
                UpgradeEffect("pickup range", 0.15f, Format.PERCENT),
                // Kept, never printed: the owner cut the pull number as not player-relevant, but the
                // game still applies it and the agreement test still pins it against GameState.
                UpgradeEffect("pull speed", 0.20f, Format.PERCENT, shown = false)
            )
        ),
        // Row 3 — Economy + NG+
        UpgradeTile(
            "Finder's Fee", "yen_bonus", "+20% yen earned",
            detail = "Earn more yen every run.",
            effects = listOf(UpgradeEffect("yen earned", 0.20f, Format.PERCENT))
        ),
        UpgradeTile(
            "Scavenger Rig", "salvage", "+20% drop rate",
            detail = "Asteroids drop upgrades more often.",
            effects = listOf(UpgradeEffect("upgrade drops", 0.20f, Format.PERCENT))
        ),
        UpgradeTile("Emergency Shield", "emergency_shield", "Survive a lethal hit", isNgPlus = true)
    )

    /**
     * The ids a player actually spends yen on.
     *
     * Every one of these must survive a save and be cleared by a reset. An id here that
     * `PersistenceManager` does not know about produces a tile that silently forgets what it sold,
     * which is why there is a test walking this list rather than a hardcoded copy of it.
     */
    val purchasableIds: List<String> = tiles.filter { !it.isNgPlus }.mapNotNull { it.id }
}
