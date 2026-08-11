package com.astroloop.game.hangar

import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.StoryStage
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.data.TutorialDefinitions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TB-26 reports the run's takings on a normal return, mirroring the survived-time report Tobar
 * gives in Astro Loop mode. It rides on the greeting rather than taking a beat of its own, so the
 * return stays as short as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatSystemYenReportTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var state: HangarState
    private lateinit var chatSystem: ChatSystem

    @Before
    fun setup() {
        persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        state = HangarState(persistence)
        chatSystem = ChatSystem()
    }

    /** Onboarding owns the first two returns; most of these tests are about what comes after. */
    private fun skipOnboarding() {
        repeat(TutorialDefinitions.beats.size) { persistence.incrementTutorialsShown() }
    }

    private fun drainConversation() {
        var guard = 0
        while (state.activeConversation != null && guard++ < 40) {
            chatSystem.update(ChatSystem.LINE_PAUSE + 0.1f, state)
        }
    }

    private fun greeting() = state.chatMessages.first { it.text.startsWith("Welcome back, commander.") }

    @Test
    fun `the greeting carries the run's yen instead of adding a line`() {
        skipOnboarding()

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        assertTrue(
            "The takings belong on the greeting itself: ${greeting().text}",
            greeting().text.contains(GameConfig.formatYen(2400))
        )
    }

    @Test
    fun `a first run is its own best and is called out`() {
        skipOnboarding()

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        assertTrue(
            "A new best should be celebrated on the greeting line",
            state.chatMessages.any { it.text.contains("new best") }
        )
    }

    @Test
    fun `a new best gets a reaction, on the report line itself`() {
        skipOnboarding()

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        val report = state.chatMessages.single { it.text.contains("new best") }
        assertTrue(
            "The reaction rides on the report rather than taking a line of its own. " +
                "Got: ${state.chatMessages.map { it.text }}",
            ChatSystem.newBestYenReactions.any { report.text.endsWith(it) }
        )
    }

    @Test
    fun `the reactions are TB-26's and fit his budget`() {
        for (line in ChatSystem.newBestYenReactions) {
            assertTrue("A blank reaction", line.isNotBlank())
            assertTrue("${line.length} chars, over TB-26's 58: $line", line.length <= 58)
        }
        assertEquals(
            "Duplicated reactions would defeat the variety",
            ChatSystem.newBestYenReactions.size, ChatSystem.newBestYenReactions.toSet().size
        )
    }

    @Test
    fun `an ordinary run gets no reaction`() {
        skipOnboarding()
        persistence.updateBestRunYen(5100)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        assertTrue(
            "A reaction answers a record, not an ordinary haul",
            state.chatMessages.none { m -> ChatSystem.newBestYenReactions.any { m.text.endsWith(it) } }
        )
    }

    @Test
    fun `a lesser run reports the standing best on the same line`() {
        skipOnboarding()
        persistence.updateBestRunYen(5100)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        assertTrue(
            "2400 does not beat 5100",
            state.chatMessages.none { it.text.contains("new best") }
        )
        val report = state.chatMessages.single { it.text.startsWith("Welcome back, commander.") }
        assertTrue(
            "This run's takings and the standing best share one line",
            report.text.contains(GameConfig.formatYen(2400)) &&
                report.text.contains(GameConfig.formatYen(5100))
        )
    }

    @Test
    fun `an empty-handed run keeps the bare greeting`() {
        skipOnboarding()

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 0)
        drainConversation()

        assertEquals(
            "Nothing earned, nothing to report — and no rubbing it in",
            "Welcome back, commander.", greeting().text
        )
        assertTrue(
            "There is no standing best to quote against zero",
            state.chatMessages.none { it.text.contains("Best") }
        )
        assertEquals("A blank run must never become the best", 0, persistence.getBestRunYen())
    }

    @Test
    fun `a better run raises the stored best`() {
        skipOnboarding()
        persistence.updateBestRunYen(1000)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)

        assertEquals(2400, persistence.getBestRunYen())
    }

    @Test
    fun `astro loop keeps its survived-time report and never mentions yen`() {
        persistence.setStoryStageCode(StoryStage.ASTRO_LOOP.code)
        persistence.setLastAstroRunSeconds(125f)
        persistence.updateAstroLoopBestSeconds(125f)

        chatSystem.onDeathReturn(state, "pilot_astro", yenEarned = 9999)
        drainConversation()

        assertTrue(state.chatMessages.any { it.text.contains("survived for") })
        assertTrue(
            "Astro Loop scores time, not takings",
            state.chatMessages.none { it.text.contains("¥") }
        )
    }

    @Test
    fun `the takings go unreported while onboarding is still owed`() {
        // No skipOnboarding(): the first return owes a tutorial beat, and the report stands aside.
        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        assertEquals(
            "The first return belongs to onboarding, so the greeting stays bare",
            "Welcome back, commander.", greeting().text
        )
        assertTrue(
            "Neither yen line may fire: ${state.chatMessages.map { it.text }}",
            state.chatMessages.none { m ->
                m.text.contains("new best") ||
                    m.text.contains("Best ") ||
                    ChatSystem.newBestYenReactions.any { m.text.endsWith(it) }
            }
        )
        assertTrue(
            "And onboarding still runs underneath it",
            state.chatMessages.any { it.text.contains("Green is health") }
        )
    }

    @Test
    fun `the second return is onboarding's too`() {
        persistence.incrementTutorialsShown()   // beat one already delivered

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        assertEquals(
            "Welcome back, commander.", greeting().text
        )
        assertTrue(
            "Beat two should be the one running",
            state.chatMessages.any { it.text.contains("You don't hire pilots") }
        )
    }

    @Test
    fun `reporting starts on the third return`() {
        // Driven through the real returns rather than the counter, so the boundary is the one
        // the player actually walks into.
        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 100)
        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 100)

        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        assertTrue(
            "The third return is the first one that reports: ${greeting().text}",
            greeting().text.contains(GameConfig.formatYen(2400))
        )
    }

    @Test
    fun `an unreported run still counts toward the best`() {
        // The player earned it and the HUD showed it during the run — staying quiet about the
        // takings must not also forget them, or the first report would quote too small a figure.
        chatSystem.onDeathReturn(state, "pilot_medic", yenEarned = 5000)

        assertEquals(5000, persistence.getBestRunYen())
    }

    @Test
    fun `resetting progress restores onboarding and clears the best`() {
        persistence.incrementTutorialsShown()
        persistence.updateBestRunYen(5000)

        persistence.resetAllProgress()

        assertEquals("A fresh start should be taught again", 0, persistence.getTutorialsShown())
        assertEquals("A fresh start has no takings to beat", 0, persistence.getBestRunYen())
    }
}
