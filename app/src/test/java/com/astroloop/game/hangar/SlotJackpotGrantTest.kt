package com.astroloop.game.hangar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A jackpot's free upgrade must not be granted while the reels are still turning.
 *
 * The payout is the reveal: banking it at roll time meant the store tile had already gained a
 * level before the third reel landed, so the machine told the player the answer before it showed
 * it to them. The yen payout was always deferred to the stop; the upgrade was not.
 *
 * `roll = 0f` is below every jackpot threshold in `handleSlotSpin` (the lowest is 0.005f), so it
 * forces a jackpot on any save without spinning until one turns up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SlotJackpotGrantTest {

    private val upgradeIds =
        listOf("health", "shields", "speed", "damage", "crit", "magnet", "yen_bonus", "salvage")

    private lateinit var persistence: PersistenceManager
    private lateinit var view: HangarSurfaceView

    private fun totalUpgradeLevels() = upgradeIds.sumOf { persistence.getUpgradeLevel(it) }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        persistence = PersistenceManager(context)
        persistence.resetAllProgress()
        persistence.setYen(100_000)
        view = HangarSurfaceView(context) { _, _ -> }
        view.state.actualYen = persistence.getYen()
    }

    @Test
    fun `the jackpot upgrade is not granted while the reels are still spinning`() {
        view.handleSlotSpin(roll = 0f)

        assertTrue("roll 0f must produce a spin in progress", view.state.isSpinning)
        assertEquals(
            "no upgrade level may be written before the reels stop",
            0, totalUpgradeLevels()
        )
    }

    @Test
    fun `the jackpot upgrade lands when the reels stop`() {
        view.handleSlotSpin(roll = 0f)
        view.completeSpin(System.currentTimeMillis())

        assertFalse(view.state.isSpinning)
        assertEquals(
            "the reels stopping grants exactly one level",
            1, totalUpgradeLevels()
        )
    }

    @Test
    fun `the upgrade named on the reels is the one that gets granted`() {
        view.handleSlotSpin(roll = 0f)
        val announced = view.state.spinResultUpgrade
        assertNotNull("a jackpot on an unmaxed save announces an upgrade", announced)

        view.completeSpin(System.currentTimeMillis())

        val granted = upgradeIds.filter { persistence.getUpgradeLevel(it) > 0 }
        assertEquals("exactly one upgrade is granted", 1, granted.size)
        assertEquals(
            "the granted upgrade must be the one the reveal named",
            announced, HangarSurfaceView.upgradeDisplayName(granted.single())
        )
    }

    @Test
    fun `an all-maxed save pays yen instead of an upgrade`() {
        for (id in upgradeIds) persistence.setUpgradeLevel(id, 5)
        val yenBefore = persistence.getYen()

        view.handleSlotSpin(roll = 0f)
        view.completeSpin(System.currentTimeMillis())

        assertNull("nothing left to grant", view.state.spinResultUpgrade)
        assertTrue("the jackpot pays out in yen instead", persistence.getYen() > yenBefore)
    }
}
