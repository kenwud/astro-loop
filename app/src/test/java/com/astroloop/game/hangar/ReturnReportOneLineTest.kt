package com.astroloop.game.hangar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.core.GameConfig
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.data.TutorialDefinitions
import com.astroloop.game.core.StoryStage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The run report is one line, in both modes.
 *
 * It used to take two on every return that had anything to say: the takings, then "Best is still
 * X" underneath, or the record line and a reaction underneath that. Two lines of the same fact is
 * a readout, not a bartender talking, and it pushed the rest of the bar's chatter down the column.
 *
 * The greeting survives on every return including records — "Welcome back, commander." is TB-26's
 * signature, and it is both greeting and curse, so a personal best is not the one
 * return that goes ungreeted. That is what squeezes the reaction pool: the whole line has to fit
 * the speaker's budget with the figure already in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReturnReportOneLineTest {

    /** TB-26's and TOBAR's shared worst-case bar-chatter budget. */
    private val budget = 58

    private lateinit var persistence: PersistenceManager
    private lateinit var state: HangarState
    private lateinit var chat: ChatSystem

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        persistence = PersistenceManager(context)
        persistence.resetAllProgress()
        state = HangarState(persistence)
        chat = ChatSystem()
        // Past onboarding: the first two returns stay silent by design.
        repeat(TutorialDefinitions.beats.size) { persistence.incrementTutorialsShown() }
    }

    /** Return lines are queued as a conversation — tick update() until they have all landed. */
    private fun drainConversation() {
        var guard = 0
        while (state.activeConversation != null && guard++ < 40) {
            chat.update(ChatSystem.LINE_PAUSE + 0.1f, state)
        }
    }

    private fun reportLines() = state.chatMessages
        .filter { it.text.contains("¥") || it.text.contains("Best") || it.text.contains("survived") }

    @Test
    fun `an ordinary run reports takings and best on one line`() {
        persistence.updateBestRunYen(5100)

        chat.onDeathReturn(state, "pilot_medic", yenEarned = 2400)
        drainConversation()

        val report = state.chatMessages.filter { it.text.contains("Welcome back, commander.") }
        assertEquals("the report is one line", 1, report.size)
        assertTrue("it carries this run's takings", report[0].text.contains(GameConfig.formatYen(2400)))
        assertTrue("and the best, on the same line", report[0].text.contains(GameConfig.formatYen(5100)))
        assertTrue("nothing is left behind on a second line",
            state.chatMessages.none { it.text.startsWith("Best is still") })
    }

    @Test
    fun `a record run reports and reacts on one line`() {
        persistence.updateBestRunYen(1000)

        chat.onDeathReturn(state, "pilot_medic", yenEarned = 5200)
        drainConversation()

        val report = state.chatMessages.filter { it.text.contains("Welcome back, commander.") }
        assertEquals("a record is still one line", 1, report.size)
        assertTrue(report[0].text.contains("new best"))
        assertTrue(
            "the reaction rides on the same line rather than taking its own",
            ChatSystem.newBestYenReactions.any { report[0].text.endsWith(it) }
        )
    }

    @Test
    fun `a record run is still greeted`() {
        persistence.updateBestRunYen(1000)

        chat.onDeathReturn(state, "pilot_medic", yenEarned = 5200)
        drainConversation()

        assertTrue(
            "TB-26's signature line is not dropped on the player's best run",
            state.chatMessages.first().text.startsWith("Welcome back, commander.")
        )
    }

    @Test
    fun `every yen reaction fits the line with a worst-case figure`() {
        // formatYen abbreviates, so the longest realistic figure is a six-character one.
        val worst = GameConfig.formatYen(999_900)
        for (reaction in ChatSystem.newBestYenReactions) {
            val line = "Welcome back, commander. $worst - new best! $reaction"
            assertTrue(
                "'$line' is ${line.length} chars, over TB-26's $budget budget",
                line.length <= budget
            )
        }
    }

    @Test
    fun `every Astro Loop reaction fits the line with a worst-case time`() {
        // Six characters of clock — a 100-minute-plus run, well past where 1.2's health ramp
        // ends one. This is the longest stem in the game, which is why these reactions are the
        // shortest ones.
        for (reaction in ChatSystem.newBestTimeReactions) {
            val line = "Welcome back! You survived for 120:00 - new best! $reaction"
            assertTrue(
                "'$line' is ${line.length} chars, over TOBAR's $budget budget",
                line.length <= budget
            )
        }
    }

    @Test
    fun `both Astro Loop shapes use the same phrasing`() {
        // Two wordings for one report would read as two different messages.
        persistence.setStoryStageCode(StoryStage.ASTRO_LOOP.code)
        persistence.setLastAstroRunSeconds(221f)
        persistence.updateAstroLoopBestSeconds(221f)

        chat.onDeathReturn(state, "pilot_astro", yenEarned = 0)
        drainConversation()

        val record = state.chatMessages.single { it.text.contains("new best") }
        assertTrue("a record still says it the same way", record.text.contains("You survived for"))
    }

    @Test
    fun `an ordinary Astro Loop run reports survived time and best on one line`() {
        persistence.setStoryStageCode(StoryStage.ASTRO_LOOP.code)
        persistence.setLastAstroRunSeconds(130f)
        persistence.updateAstroLoopBestSeconds(221f)

        chat.onDeathReturn(state, "pilot_astro", yenEarned = 0)
        drainConversation()

        val report = state.chatMessages.filter { it.text.contains("survived") }
        assertEquals("the report is one line", 1, report.size)
        assertTrue("it carries both figures", report[0].text.contains("Best"))
        assertTrue(state.chatMessages.none { it.text.startsWith("Best is still") })
    }

    @Test
    fun `an empty-handed run still says nothing about takings`() {
        chat.onDeathReturn(state, "pilot_medic", yenEarned = 0)
        drainConversation()

        assertTrue("a run that took nothing has nothing to report", reportLines().isEmpty())
    }
}
