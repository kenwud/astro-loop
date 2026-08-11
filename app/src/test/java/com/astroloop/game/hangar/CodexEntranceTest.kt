package com.astroloop.game.hangar

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The codex is reached through the maintenance hatch on the slot machine and nowhere else.
 *
 * It had two doors: the hatch — the secret the bar hints point at, "search around the slot
 * machine" — and a book on the bar counter that opened it outright once any evolution had been
 * discovered. Owner's call on 2026-08-10: the book is not a door. It is still drawn on the
 * counter; it just does not open anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CodexEntranceTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var view: HangarSurfaceView

    private val bookRect = RectF(10f, 10f, 40f, 30f)
    private val paperRect = RectF(100f, 100f, 140f, 130f)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        persistence = PersistenceManager(context)
        persistence.resetAllProgress()
        // The book only ever opened the codex once something was in it, so the old route has to be
        // live for this test to be about the removal rather than about an empty codex.
        persistence.discoverEvolution("jackpot_mines")
        view = HangarSurfaceView(context) { _, _ -> }
        view.renderer.barPageRenderer.codexBookRect = bookRect
    }

    @Test
    fun `tapping the book on the counter does not open the codex`() {
        view.handleBarTap(bookRect.centerX(), bookRect.centerY())

        assertNotEquals(
            "the book is set dressing, not an entrance",
            HangarPhase.CODEX, view.state.phase
        )
    }

    @Test
    fun `the hatch is still the way in`() {
        view.state.hatchOpen = true
        view.state.paperRect = paperRect

        view.handleStoreTap(paperRect.centerX(), paperRect.centerY())

        assertEquals(
            "the maintenance hatch must remain the one route to the codex",
            HangarPhase.CODEX, view.state.phase
        )
    }

    @Test
    fun `the paper does nothing while the hatch is still shut`() {
        view.state.hatchOpen = false
        view.state.paperRect = paperRect

        view.handleStoreTap(paperRect.centerX(), paperRect.centerY())

        assertNotEquals(HangarPhase.CODEX, view.state.phase)
    }
}
