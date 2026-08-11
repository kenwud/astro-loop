package com.astroloop.game.data

import com.astroloop.game.render.CrystalPalette

object DesertDefinitions {

    // Speaker constants
    const val TB = "TOBAR"
    const val ASTRO = "ASTRO"
    const val COMMAND = "COMMAND"
    const val CRYSTAL = "CRYSTAL"

    // Colors
    val TB_COLOR = 0xFF88AACC.toInt()
    val ASTRO_COLOR = 0xFFDD3333.toInt()
    val COMMAND_COLOR = 0xFF999999.toInt()
    val CRYSTAL_COLOR = CrystalPalette.MID

    data class DesertLine(
        val speaker: String,
        val text: String,
        val color: Int,
        val trigger: DesertTrigger = DesertTrigger.TIMER,
        val delay: Float = 3f,
        val interrupt: Boolean = false
    )

    enum class DesertTrigger {
        TIMER,
        SCENE_START,
        FIRST_ENEMIES,
        FIRST_KILL,
        SECOND_WAVE,
        AFTER_KILLS,
        PHASE2_START,
        AMBIGUOUS_TARGET,
        NEAR_SETTLEMENT,
        PLAYER_SHOOTS,
        PLAYER_CONTINUES,
        STOP_CHECK,
        PLAYER_FIRES_AFTER_STOP,
        ALL_CLEARED,
        AFTERMATH,
        AUTO,
        CIVILIANS_VISIBLE,
        SIGNATURES_VISIBLE,
        PLAYER_FIRES_AT_CIVILIANS,
        PLAYER_HIT_WALL,
        BOMBARDMENT_DELAY
    }

    val phase1Lines = listOf(
        DesertLine(TB, "Eyes on the road, Lieutenant.", TB_COLOR, DesertTrigger.SCENE_START, 2f),
        DesertLine(ASTRO, "You call this a road, Tobar?", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "Fair point.", TB_COLOR, DesertTrigger.TIMER, 3f),
        DesertLine(TB, "Contact north. Light armor.", TB_COLOR, DesertTrigger.FIRST_ENEMIES, 1f),
        DesertLine(ASTRO, "Copy that, Tobar.", ASTRO_COLOR, DesertTrigger.TIMER, 3f),
        DesertLine(TB, "Nice shot.", TB_COLOR, DesertTrigger.FIRST_KILL, 1f),
        DesertLine(TB, "Command: target, two clicks north.", TB_COLOR, DesertTrigger.TIMER, 11f),
        DesertLine(ASTRO, "Command says a lot of things.", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "Can't argue that.", TB_COLOR, DesertTrigger.TIMER, 3f),
        DesertLine(TB, "You ever think about after?", TB_COLOR, DesertTrigger.TIMER, 13f),
        DesertLine(ASTRO, "After what?", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "The war. All of it.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(ASTRO, "Sometimes.", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "I keep thinking about after.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(ASTRO, "Yeah?", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "A bar. Somewhere quiet.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(ASTRO, "You? Running a bar, Tobar?", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "Know every name. Good drinks.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "No orders. No commands.", TB_COLOR, DesertTrigger.TIMER, 3f),
        DesertLine(ASTRO, "...Yeah. Maybe.", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "More contacts. Twelve o'clock.", TB_COLOR, DesertTrigger.SECOND_WAVE, 1f),
        DesertLine(ASTRO, "Getting busy out here.", ASTRO_COLOR, DesertTrigger.AFTER_KILLS, 1f),
        DesertLine(TB, "Just like the old days.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(ASTRO, "These ARE the old days, Tobar.", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "Ha. Fair point, Lieutenant.", TB_COLOR, DesertTrigger.TIMER, 3f)
    )

    val phase2Lines = listOf(
        DesertLine(COMMAND, "Target located. Settlement center.", COMMAND_COLOR, DesertTrigger.PHASE2_START, 1f),
        DesertLine(TB, "Copy, command.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "Lieutenant... these readings.", TB_COLOR, DesertTrigger.AMBIGUOUS_TARGET, 1f),
        DesertLine(TB, "Signatures are light. Too light.", TB_COLOR, DesertTrigger.SIGNATURES_VISIBLE, 4f),
        DesertLine(TB, "Those don't look like combatants.", TB_COLOR, DesertTrigger.CIVILIANS_VISIBLE, 1f),
        DesertLine(TB, "Lieutenant.", TB_COLOR, DesertTrigger.TIMER, 6f),
        DesertLine(TB, "They're running.", TB_COLOR, DesertTrigger.PLAYER_FIRES_AT_CIVILIANS, 1f),
        DesertLine(TB, "We should stop.", TB_COLOR, DesertTrigger.STOP_CHECK, 4f)
    )

    val goodEndingLines = listOf(
        DesertLine(TB, "...You stopped.", TB_COLOR, DesertTrigger.AUTO, 6f),
        DesertLine(ASTRO, "They weren't soldiers.", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "No. They weren't.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(ASTRO, "Command said\u2014", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "I know what command said.", TB_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "Let's go home, Lieutenant.", TB_COLOR, DesertTrigger.TIMER, 5f),
        DesertLine(ASTRO, "The bar?", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "The bar.", TB_COLOR, DesertTrigger.TIMER, 4f)
    )

    val horrorLines = listOf(
        DesertLine(ASTRO, "Can't push through.", ASTRO_COLOR, DesertTrigger.PLAYER_HIT_WALL, 1f),
        DesertLine(COMMAND, "Proceed with bombardment.", COMMAND_COLOR, DesertTrigger.NEAR_SETTLEMENT, 1f),
        DesertLine(ASTRO, "Command, I'm not sure these are\u2014", ASTRO_COLOR, DesertTrigger.TIMER, 3f),
        DesertLine(COMMAND, "You have your orders, Lieutenant.", COMMAND_COLOR, DesertTrigger.BOMBARDMENT_DELAY, 1.2f, interrupt = true),
        DesertLine(TB, "...What did we do?", TB_COLOR, DesertTrigger.AFTERMATH, 6f),
        DesertLine(ASTRO, "...What did I do?", ASTRO_COLOR, DesertTrigger.TIMER, 4f)
    )

    val crystalLines = listOf(
        DesertLine(CRYSTAL, "I can undo this.", CRYSTAL_COLOR, DesertTrigger.AUTO, 3f),
        DesertLine(ASTRO, "I can't\u2014 just do it. Fix this.", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(TB, "Astro, get away from it!", TB_COLOR, DesertTrigger.TIMER, 3f),
        DesertLine(CRYSTAL, "A price must be paid.", CRYSTAL_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(ASTRO, "No. NO!", ASTRO_COLOR, DesertTrigger.TIMER, 3f),
        DesertLine(TB, "It hurts\u2014 it hurts, Astro.", TB_COLOR, DesertTrigger.TIMER, 5f),
        // The reveal. The old name is reached for and the new one arrives in the same breath —
        // this is where the player learns that TOBAR is TB-26, rather than inferring it later
        // from a bartender's voice.
        DesertLine(ASTRO, "...Tobar? TB?", ASTRO_COLOR, DesertTrigger.TIMER, 4f),
        DesertLine(CRYSTAL, "The price is paid.", CRYSTAL_COLOR, DesertTrigger.TIMER, 5f)
    )

    val allLines: List<DesertLine> = phase1Lines + phase2Lines + goodEndingLines + horrorLines + crystalLines

    /**
     * Whether this pass through the desert withholds the choice.
     *
     * The first pass is on rails: Tobar never says "We should stop.", the branch never arms, and
     * the village is destroyed. The choice returns on every later pass, so reaching the good
     * ending is something the loop grants on a second run at the same moment rather than
     * something available the first time you are there.
     *
     * Keyed on the story loop because the desert recurs with it: the horror path calls
     * `incrementStoryLoop()` and sends the player back around the arc, while the good ending is
     * terminal and enters ASTRO_LOOP. In normal play `incrementStoryLoop()` is only ever called
     * at the end of the horror path, so loop 1 is exactly "has never finished the desert".
     */
    fun isForcedHorror(storyLoop: Int): Boolean = storyLoop <= 1

    /**
     * The phase-1 script, minus Tobar's stop line when the pass is forced.
     *
     * Removing the line removes the branch: its `STOP_CHECK` trigger is what arms the stop check,
     * so a script without it can only end one way. Filtered by trigger rather than by index or
     * text so re-authoring the surrounding lines cannot silently re-arm the choice.
     */
    fun phase2LinesFor(forcedHorror: Boolean): List<DesertLine> =
        if (forcedHorror) phase2Lines.filter { it.trigger != DesertTrigger.STOP_CHECK }
        else phase2Lines
}
