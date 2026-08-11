package com.astroloop.game.hangar

import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.BossHintDefinitions
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.data.TutorialDefinitions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A hint is owed after a run dies past the ten-minute boss, and is spent on the next return to
 * the bar. It shares the slot onboarding uses — directly after the greeting — and its crew
 * reaction replaces the returning pilot's déjà vu line, so the return never grows a fourth beat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatSystemBossHintTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var state: HangarState
    private lateinit var chatSystem: ChatSystem

    private val medicDejaVu = "Elevated stress readings... can't remember why."

    @Before
    fun setup() {
        persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        state = HangarState(persistence)
        chatSystem = ChatSystem()
        repeat(TutorialDefinitions.beats.size) { persistence.incrementTutorialsShown() }
    }

    private fun drainConversation() {
        var guard = 0
        while (state.activeConversation != null && guard++ < 40) {
            chatSystem.update(ChatSystem.LINE_PAUSE + 0.1f, state)
        }
    }

    private fun texts() = state.chatMessages.map { it.text }

    @Test
    fun `a solo failure is answered with the solo track`() {
        persistence.incrementBossFailures(BossHintDefinitions.Track.SOLO)
        persistence.setPendingBossHint(BossHintDefinitions.Track.SOLO)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 1200)
        drainConversation()

        val expected = BossHintDefinitions.hintFor(BossHintDefinitions.Track.SOLO, 1)
        assertTrue("Expected the solo hint, got ${texts()}", expected in texts())
    }

    @Test
    fun `an Astro failure is answered with the endurance track`() {
        persistence.incrementBossFailures(BossHintDefinitions.Track.ASTRO)
        persistence.setPendingBossHint(BossHintDefinitions.Track.ASTRO)

        chatSystem.onDeathReturn(state, "pilot_astro", yenEarned = 1200)
        drainConversation()

        val expected = BossHintDefinitions.hintFor(BossHintDefinitions.Track.ASTRO, 1)
        assertTrue("Expected the endurance hint, got ${texts()}", expected in texts())
    }

    @Test
    fun `the hint escalates with the failure count for its own track`() {
        repeat(3) { persistence.incrementBossFailures(BossHintDefinitions.Track.SOLO) }
        persistence.setPendingBossHint(BossHintDefinitions.Track.SOLO)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 0)
        drainConversation()

        assertTrue(
            "Third failure should get the third, most explicit hint",
            BossHintDefinitions.hintFor(BossHintDefinitions.Track.SOLO, 3) in texts()
        )
    }

    @Test
    fun `the crew reacts and the deja vu line steps aside`() {
        persistence.incrementBossFailures(BossHintDefinitions.Track.SOLO)
        persistence.setPendingBossHint(BossHintDefinitions.Track.SOLO)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 0)
        drainConversation()

        assertTrue(
            "The returning pilot reacts in their own voice, got ${texts()}",
            BossHintDefinitions.reactionFor("MEDIC") in texts()
        )
        assertFalse("The reaction replaces the déjà vu line", medicDejaVu in texts())
    }

    @Test
    fun `a hint is spent once and does not follow the player around`() {
        persistence.incrementBossFailures(BossHintDefinitions.Track.SOLO)
        persistence.setPendingBossHint(BossHintDefinitions.Track.SOLO)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 0)
        drainConversation()
        state.chatMessages.clear()

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 0)
        drainConversation()

        val hint = BossHintDefinitions.hintFor(BossHintDefinitions.Track.SOLO, 1)
        assertFalse("The hint was already given, got ${texts()}", hint in texts())
        assertTrue("The ordinary return resumes", medicDejaVu in texts())
    }

    @Test
    fun `an ordinary return is untouched`() {
        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 500)
        drainConversation()

        assertTrue("The déjà vu line still runs when nothing is owed", medicDejaVu in texts())
        assertTrue(
            "No hint should appear out of nowhere",
            texts().none { it in BossHintDefinitions.linesFor(BossHintDefinitions.Track.SOLO) }
        )
    }

    @Test
    fun `onboarding wins the slot and the hint waits its turn`() {
        // Contrived but reachable: a player good enough to reach ten minutes on an early run.
        persistence.resetAllProgress()
        persistence.incrementBossFailures(BossHintDefinitions.Track.SOLO)
        persistence.setPendingBossHint(BossHintDefinitions.Track.SOLO)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 0)
        drainConversation()

        val hint = BossHintDefinitions.hintFor(BossHintDefinitions.Track.SOLO, 1)
        assertFalse("Onboarding owns the first returns, got ${texts()}", hint in texts())
        assertEquals(
            "and the hint must still be owed afterwards",
            BossHintDefinitions.Track.SOLO, persistence.getPendingBossHint()
        )
    }
}
