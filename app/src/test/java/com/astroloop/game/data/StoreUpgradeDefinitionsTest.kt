package com.astroloop.game.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the store tiles against the failure that actually costs a player: a tile that takes yen
 * and then forgets what it sold.
 *
 * Every purchasable id has to be a key `PersistenceManager` both saves and clears. Those were two
 * hand-maintained lists in different files, so the test walks the real tile list rather than a copy
 * of it — a ninth upgrade added to the store now fails here until persistence knows about it.
 *
 * Deliberately **not** tested: effect-string width. The renderer does not truncate, so overflow is
 * a real failure mode, but the budget depends on font metrics that cannot be measured off-device.
 * A character count asserted here would only be enforcing a guess.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreUpgradeDefinitionsTest {

    private lateinit var persistence: PersistenceManager

    @Before
    fun setup() {
        persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
    }

    @Test
    fun `the grid is three by three`() {
        assertEquals("The renderer derives row and column from the index", 9, StoreUpgradeDefinitions.tiles.size)
    }

    @Test
    fun `eight tiles are bought and one is the NG plus reward`() {
        val ngPlus = StoreUpgradeDefinitions.tiles.filter { it.isNgPlus }
        assertEquals(1, ngPlus.size)
        assertEquals("Emergency Shield", ngPlus.single().name)
        assertEquals(8, StoreUpgradeDefinitions.purchasableIds.size)
    }

    @Test
    fun `every tile says who it is and what it does`() {
        for (tile in StoreUpgradeDefinitions.tiles) {
            assertTrue("A tile with no name", tile.name.isNotBlank())
            assertTrue("${tile.name} does not say what it does", tile.effect.isNotBlank())
        }
    }

    @Test
    fun `no two tiles share an id`() {
        val ids = StoreUpgradeDefinitions.purchasableIds
        assertEquals("A duplicated id would make two tiles the same upgrade", ids.size, ids.toSet().size)
    }

    @Test
    fun `every purchasable upgrade survives being bought`() {
        for (id in StoreUpgradeDefinitions.purchasableIds) {
            persistence.setUpgradeLevel(id, 3)
            assertEquals("$id did not persist — the tile would forget what it sold", 3, persistence.getUpgradeLevel(id))
        }
    }

    @Test
    fun `every purchasable upgrade is cleared by a reset`() {
        for (id in StoreUpgradeDefinitions.purchasableIds) {
            persistence.setUpgradeLevel(id, 5)
        }

        persistence.resetAllProgress()

        for (id in StoreUpgradeDefinitions.purchasableIds) {
            assertEquals(
                "$id survived a full reset — PersistenceManager does not know about it",
                0, persistence.getUpgradeLevel(id)
            )
        }
    }

    @Test
    fun `effectsAt states the accumulated value, not the per-level one`() {
        val haulLine = StoreUpgradeDefinitions.tiles.first { it.id == "magnet" }

        // The pull-speed effect is still in the data — the agreement test pins it — but the owner
        // cut its number from the card on 2026-08-09 as not player-relevant, so it is not printed.
        assertEquals(listOf("+45% pickup range"), haulLine.effectsAt(3))
    }

    @Test
    fun `nextDeltas states what one more level buys`() {
        val haulLine = StoreUpgradeDefinitions.tiles.first { it.id == "magnet" }

        // Deltas carry no label — they sit directly under the figure they belong to.
        assertEquals(listOf("+15%"), haulLine.nextDeltas())
    }

    @Test
    fun `level zero shows an absolute but not a relative`() {
        // Reversed on 2026-08-09, and the reversal is the point of that change: "Health 50" is the
        // ship the player is flying right now, which is information. A relative figure at level 0
        // would only read "+0% ship speed", which is the noise the old rule existed to suppress.
        val plate = StoreUpgradeDefinitions.tiles.first { it.id == "health" }
        val nitro = StoreUpgradeDefinitions.tiles.first { it.id == "speed" }

        assertEquals(listOf("Health 50"), plate.effectsAt(0))
        assertEquals(emptyList<String>(), nitro.effectsAt(0))
    }

    @Test
    fun `every purchasable tile carries a description`() {
        for (tile in StoreUpgradeDefinitions.tiles.filter { !it.isNgPlus }) {
            assertTrue("${tile.name} has no description", tile.detail.isNotBlank())
        }
    }

    /**
     * `UpgradeEffect.at()` rounds rather than truncates. None of the four `perLevel` constants
     * actually in use happen to land on a value `.toInt()` gets wrong, which is exactly why a
     * truncating implementation could ship green — this pins a value it would have gotten wrong.
     */
    @Test
    fun `at rounds a fractional percentage instead of truncating it toward zero`() {
        // 0.006f is 0.6% per level — at level 1 that is 0.6, which floors to +0% but rounds to +1%.
        val effect = StoreUpgradeDefinitions.UpgradeEffect("test", 0.006f, StoreUpgradeDefinitions.Format.PERCENT)

        assertEquals("+1% test", effect.at(1))
    }

    @Test
    fun `at rounds a fractional flat value instead of truncating it toward zero`() {
        // 0.6f per level floors to +0 at level 1 but rounds to +1.
        val effect = StoreUpgradeDefinitions.UpgradeEffect("test", 0.6f, StoreUpgradeDefinitions.Format.FLAT)

        assertEquals("+1 test", effect.at(1))
    }
}
