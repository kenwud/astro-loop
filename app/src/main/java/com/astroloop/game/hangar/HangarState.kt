package com.astroloop.game.hangar

import android.graphics.RectF
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.data.PilotDef
import com.astroloop.game.data.PilotDefinitions
import com.astroloop.game.data.PilotUnlockType
import com.astroloop.game.data.ShipDefinitions
import com.astroloop.game.data.StoreUpgradeDefinitions
import com.astroloop.game.core.AudioMode
import com.astroloop.game.core.StoryStateManager
import java.util.concurrent.CopyOnWriteArrayList

enum class HangarPhase {
    BROWSING,           // Normal state, scrolling ships/pilots
    LAUNCHING,          // Launch sequence playing
    CODEX               // Viewing codex
}

data class ChatMessage(
    val speaker: String,
    val text: String,
    val color: Int
)

data class WalkerNPC(
    val pilotIndex: Int,
    val color: Int,
    var x: Float,          // Normalized 0..1 within bar walkway
    var targetX: Float,
    var walking: Boolean,
    var idleTimer: Float,  // Seconds until picking new target
    var armRaiseTimer: Float = 0f,
    var seated: Boolean = false,      // parked and sitting at a stool (ASTRO_LOOP)
    var seatedStool: Int = -1,        // stool index 2/3/5/7 when seated, else -1
    var pendingStool: Int = -1        // stool index this walker is en route to, else -1
)

class HangarState(internal val persistence: PersistenceManager) {

    companion object {
        /** Long enough to be seen as a change, short enough not to be an animation. */
        const val HINT_NOTE_REVEAL_SECONDS = 0.8f
    }

    @Volatile var phase: HangarPhase = HangarPhase.BROWSING

    // --- Page navigation ---
    @Volatile var currentPage: Int = 1            // 0=bar, 1=shipyard, 2=store
    @Volatile var pageScrollOffset: Float = 0f    // Pixel offset for smooth page swiping
    @Volatile var pageVelocity: Float = 0f        // For momentum page swiping
    @Volatile var swayMomentum: Float = 0f         // Decaying momentum for lamp sway effect

    // --- Ship selection (shipyard page) ---
    @Volatile var selectedShipIndex: Int = 0
    @Volatile var shipScrollOffset: Float = 0f   // Pixel offset for smooth ship switching

    // --- Drag-to-launch ---
    @Volatile var shipDragY: Float = 0f           // Current Y of ship being dragged
    @Volatile var isDraggingShip: Boolean = false
    @Volatile var shipRestingY: Float = 0f        // Resting position below walkway

    // --- Pilot selection (bar page grid) ---
    @Volatile var selectedPilotIndex: Int = 0
    @Volatile var pendingRecruitPilotIndex: Int = -1
    val pilotCardFades: FloatArray = FloatArray(12) { 1f }  // Per-card alpha (1=full, dim otherwise)
    // Pilot card flip animation (tap selected pilot to reveal passive effect)
    @Volatile var pilotFlipIndex: Int = -1
    @Volatile var pilotFlipTimer: Float = 0f
    @Volatile var pilotFlipProgress: Float = 0f
    @Volatile var pilotFlipShowBack: Boolean = false
    // No scroll needed — tappable grid on bar page

    /**
     * Seconds left on each store tile's flip, indexed by its position in
     * `StoreUpgradeDefinitions.tiles`. Zero means the tile is showing its front.
     *
     * **One clock per tile, not one clock.** The first cut kept a single index, so turning over a
     * second card snapped the first back to its front with no fade — which broke the rule that
     * nothing on screen may simply vanish and, more to the point, made the back useless for what it
     * exists to do: comparing two upgrades before spending. Owner, 2026-08-09: "no need for flipped
     * pages to flip back when you select another upgrade."
     *
     * A plain array rather than volatile scalars, matching `pilotCardFades` above: written by the
     * UI thread on tap, advanced and read by the game thread. A torn read costs one frame of a
     * fade, never a purchase.
     */
    private val storeFlipTimers = FloatArray(StoreUpgradeDefinitions.tiles.size)

    // Hold-to-buy, mirrored out of HoldToBuy each frame so the renderer can draw the fill without
    // reaching into the view's input state.
    @Volatile var storeHoldIndex: Int = -1
    @Volatile var storeHoldProgress: Float = 0f

    // Hold-fill exit — a completion flash or an early-release fade, so the fill drawn above never
    // simply vanishes — nothing on screen may go without a visible exit. HoldToBuy stays pure and
    // zeroes its own progress the instant it cancels or completes; HangarSurfaceView tracks the
    // decay and mirrors it here the same way it mirrors storeHoldIndex/Progress above.
    @Volatile var storeHoldExitIndex: Int = -1
    @Volatile var storeHoldExitProgress: Float = 0f  // fill width to hold through the fade, 0..1
    @Volatile var storeHoldExitAlpha: Float = 0f      // 1 = fully visible, decaying to 0
    @Volatile var storeHoldExitSuccess: Boolean = false // true = completion flash, false = plain fade

    /**
     * Show [index]'s back for [duration] seconds, leaving every other turned-over card alone.
     *
     * Re-tapping a card that is already showing its back restarts its peek rather than ending it —
     * there is deliberately no dismiss gesture, so a player rereading a card should not have to
     * wait it out.
     */
    fun flipStoreCard(index: Int, duration: Float) {
        if (index !in storeFlipTimers.indices) return
        storeFlipTimers[index] = duration
    }

    /**
     * A tap on [index]: turn it over if it is showing its front, turn it back if it is not.
     *
     * Owner, 2026-08-09. A second tap always means "put it back" — including one that lands during
     * the opening fade, where the front is technically still the visible face. Guessing at intent
     * from how far through the animation the player happened to tap would be worse than a rule they
     * can hold in their head.
     */
    fun toggleStoreCard(index: Int, duration: Float) {
        if (index !in storeFlipTimers.indices) return
        if (isStoreCardFlipped(index)) closeStoreCard(index) else flipStoreCard(index, duration)
    }

    /**
     * Dismiss [index] through its closing fade.
     *
     * Pulled down to one fade leg rather than zeroed: zeroing would make the back vanish between
     * frames, and this peek cross-fades in precisely because nothing may. A card already
     * inside its last leg is left alone rather than restarted.
     */
    fun closeStoreCard(index: Int) {
        if (index !in storeFlipTimers.indices) return
        storeFlipTimers[index] =
            storeFlipTimers[index].coerceAtMost(HangarSurfaceView.STORE_FLIP_FADE)
    }

    /**
     * A tap on pilot [index]'s card: turn it over, turn it back, or move the peek to a new card.
     *
     * The bar grid shows one back at a time — unlike the store, where comparing two upgrades is the
     * point — so tapping a different pilot moves the peek rather than adding to it.
     */
    fun togglePilotFlip(index: Int) {
        if (pilotFlipIndex == index && pilotFlipTimer > 0f) {
            pilotFlipTimer = pilotFlipTimer.coerceAtMost(HangarSurfaceView.PILOT_FLIP_FADE)
            return
        }
        pilotFlipIndex = index
        pilotFlipTimer = HangarSurfaceView.PILOT_FLIP_DURATION
        pilotFlipProgress = 1f    // start fully visible; 0f caused a one-frame invisible flash
        pilotFlipShowBack = false
    }

    /**
     * Seconds left of the cross-fade that turns a locked pilot's `?` into the bar's note on them.
     *
     * Armed the moment the hint is spoken, which is the moment the player is watching the bar — so
     * without it the `?` would simply cease to exist mid-conversation. Nothing on screen may go
     * without a visible exit, and that covers UI elements as much as ships and rocks.
     *
     * **Not persisted, deliberately.** On a later launch the note is already known and should just
     * be there; replaying the reveal every time the bar opens would make a one-off moment into a
     * recurring animation.
     */
    @Volatile var hintNoteReveal: Float = 0f

    /** Start the `?`-to-note cross-fade. */
    fun beginHintNoteReveal() {
        hintNoteReveal = HINT_NOTE_REVEAL_SECONDS
    }

    /** Tick the reveal. Safe to call every frame whether or not one is running. */
    fun advanceHintNoteReveal(deltaTime: Float) {
        if (hintNoteReveal > 0f) hintNoteReveal = (hintNoteReveal - deltaTime).coerceAtLeast(0f)
    }

    /** How visible the note is: 0 at the start of the reveal, 1 once it is done or never ran. */
    fun hintNoteAlpha(): Float =
        if (hintNoteReveal <= 0f) 1f
        else (1f - hintNoteReveal / HINT_NOTE_REVEAL_SECONDS).coerceIn(0f, 1f)

    /** Tick every turned-over card's clock. Called once per frame from the game thread. */
    fun advanceStoreFlips(deltaTime: Float) {
        for (i in storeFlipTimers.indices) {
            if (storeFlipTimers[i] > 0f) {
                storeFlipTimers[i] = (storeFlipTimers[i] - deltaTime).coerceAtLeast(0f)
            }
        }
    }

    /** Whether [index] is mid-peek — either face may be on screen, see [storeFlipShowBack]. */
    fun isStoreCardFlipped(index: Int): Boolean =
        index in storeFlipTimers.indices && storeFlipTimers[index] > 0f

    /**
     * Whether [index] has finished turning and is showing its back.
     *
     * The cycle is the pilot card's: fade the front out over one leg, hold the back, fade it out
     * again. So this is false for the first leg even though the peek has started.
     */
    fun storeFlipShowBack(index: Int): Boolean {
        if (!isStoreCardFlipped(index)) return false
        val elapsed = HangarSurfaceView.STORE_FLIP_DURATION - storeFlipTimers[index]
        return elapsed >= HangarSurfaceView.STORE_FLIP_FADE
    }

    /** Alpha of whatever face [index] is currently showing: 1 fully visible, 0 invisible. */
    fun storeFlipProgress(index: Int): Float {
        if (!isStoreCardFlipped(index)) return 0f
        val remaining = storeFlipTimers[index]
        val elapsed = HangarSurfaceView.STORE_FLIP_DURATION - remaining
        val fade = HangarSurfaceView.STORE_FLIP_FADE
        return when {
            elapsed < fade -> 1f - elapsed / fade   // front fading out
            remaining < fade -> remaining / fade    // back fading out
            else -> 1f                              // back fully visible
        }
    }

    // --- Pilot walker (world-space pixel coordinates spanning 3 pages) ---
    @Volatile var pilotX: Float = 0f              // World X position in pixels
    @Volatile var pilotTargetX: Float = 0f
    @Volatile var pilotWalking: Boolean = false
    var pilotScreenWidth: Float = 0f              // Set before initialize()
    // Width of one hangar room in design units — also the page stride. Set alongside
    // pilotScreenWidth before initialize(). Equals pilotScreenWidth below sw600dp.
    var roomWidth: Float = 0f

    // --- NPC walkers (bar page) ---
    var npcWalkers: CopyOnWriteArrayList<WalkerNPC> = CopyOnWriteArrayList()
    val pendingNPCAdds: MutableList<WalkerNPC> = java.util.Collections.synchronizedList(mutableListOf())
    val pendingNPCRemoves: MutableList<Int> = java.util.Collections.synchronizedList(mutableListOf())

    // --- Seated crew (bar stools, ASTRO_LOOP only) ---
    private val SEATABLE_STOOLS = listOf(2, 3, 5, 7)

    /** Lowest stool in the seatable set not present in [occupied]; -1 if all taken. */
    fun lowestFreeStool(occupied: Set<Int>): Int =
        SEATABLE_STOOLS.firstOrNull { it !in occupied } ?: -1

    /** Screen x of stool [stool] mapped into the walker's normalized 0..1 band. */
    fun stoolNormalizedX(stool: Int): Float {
        val w = HangarMetrics.effectiveRoomWidth(roomWidth, pilotScreenWidth)
        val stoolScreenX = HangarMetrics.stoolCenterX(w, stool)
        return (stoolScreenX - w * 0.1f) / (w * 0.8f)
    }

    // --- Chat log (bar page) ---
    val chatMessages: CopyOnWriteArrayList<ChatMessage> = CopyOnWriteArrayList()
    @Volatile var chatTimer: Float = 0f

    fun addChatMessage(speaker: String, text: String, color: Int) {
        val recent = chatMessages.takeLast(10)
        if (recent.none { it.text == text }) {
            chatMessages.add(ChatMessage(speaker, text, color))
            while (chatMessages.size > 20) chatMessages.removeAt(0)
        }
    }

    // --- Conversation queue ---
    var activeConversation: List<ChatMessage>? = null
    var conversationLineIndex: Int = 0
    var conversationLineTimer: Float = 0f
    var conversationCooldown: Float = 15f  // Start with 15s delay before first conversation
    // Cooldown applied when the CURRENT conversation finishes. Scripted bursts (the post-run
    // return, reckoning chatter, a recruitment) drop this to a short tail so the bar doesn't
    // go silent for a full CONVERSATION_COOLDOWN right after the scripted lines land.
    // Reset to the default every time a conversation ends.
    var conversationEndCooldown: Float = ChatSystem.CONVERSATION_COOLDOWN

    // --- Discovered evolutions (for codex book in bar) ---
    @Volatile var hasDiscoveredEvolutions: Boolean = false

    // --- Slot machine (store page) ---
    @Volatile var isSpinning: Boolean = false
    var reelValues: IntArray = IntArray(3)          // Target symbol index per reel
    var reelStopTimes: LongArray = LongArray(3)     // Stagger timestamps
    @Volatile var spinResultYen: Int = 0            // Payout amount for display
    @Volatile var spinResultUpgrade: String? = null  // Upgrade name if jackpot
    // The upgrade a jackpot has promised but not yet handed over. Chosen when the roll happens so
    // the reels and the save can never disagree about which one it was; written when they stop, so
    // the tile does not gain a level before the third reel has landed on it.
    @Volatile var pendingSpinUpgradeId: String? = null
    @Volatile var spinResultSymbol: Int = -1         // Symbol that landed (SYM_* constant)
    @Volatile var spinResultTime: Long = 0          // When result landed (for fade-out)
    @Volatile var reelPhases: FloatArray = FloatArray(3) // Animation scroll offset per reel

    // --- TB-26 bartender bar movement ---
    @Volatile var tb26BarX: Float = 0f
    @Volatile var tb26BarTargetX: Float = 0f
    @Volatile var tb26BarMoving: Boolean = false
    @Volatile var tb26BarPauseTimer: Float = 0f

    // --- Beer sliding ---
    @Volatile var beerActive: Boolean = false
    @Volatile var beerX: Float = 0f
    @Volatile var beerTargetX: Float = 0f
    @Volatile var beerTimer: Float = 0f
    @Volatile var beerInterval: Float = 12f
    @Volatile var beerTargetPilotIndex: Int = -1
    @Volatile var beerFading: Boolean = false
    @Volatile var beerFadeAlpha: Float = 1f

    // --- Astro hints (TB-26 hints after all non-Astro pilots recruited) ---
    @Volatile var astroHintCount: Int = 0
    @Volatile var astroHinted: Boolean = false
    @Volatile var allEvolutionsHinted: Boolean = false

    // --- Pilot hint tracking (guaranteed first show) ---
    var hintShownForPilotIndex: Int = -1

    // --- Corrupted Astro at slot machine ---
    @Volatile var astroAtSlotMachine: Boolean = false
    var astroAutoSpinTimer: Float = 0f
    var astroAutoSpinInterval: Float = 5f + kotlin.random.Random.nextFloat() * 3f

    // --- Crystal reveal animation (store page, after 11th corrupted pilot dies) ---
    enum class CrystalRevealPhase { NONE, GLOW, ORB_TRAVEL, FLASH, DONE }
    @Volatile var crystalRevealPhase = CrystalRevealPhase.NONE
    var crystalRevealTimer = 0f
    @Volatile var awaitingCrystalReveal = false

    // --- Codex secret (maintenance hatch on slot machine) ---
    @Volatile var codexDiscovered: Boolean = false
    @Volatile var codexHintGiven: Boolean = false
    @Volatile var hatchTapCount: Int = 0
    @Volatile var hatchOpen: Boolean = false
    @Volatile var showCodex: Boolean = false
    var hatchRect: RectF? = null
    var paperRect: RectF? = null
    var audioMode: AudioMode = AudioMode.ALL
    var vibrationMuted: Boolean = false
    var audioMuteButtonRect: RectF? = null
    var vibrationMuteButtonRect: RectF? = null

    // --- Slot readout messages (audio / vibration button feedback) ---
    // The buttons flank the machine's CRT readout, so the readout is what tells the player which
    // state a press just arrived at. A message takes the screen outright: see showReadoutMessage.
    @Volatile var readoutMessage: String? = null
    @Volatile var readoutMessageTime: Long = 0L

    /**
     * Put [text] on the slot machine's readout for the next three seconds.
     *
     * Clears any spin result at the same time. The message is drawn ahead of a result anyway, so
     * without this a jackpot interrupted early would reappear for its remainder once the message
     * faded, which reads as a glitch. The payout is already banked by the time either is drawn.
     */
    fun showReadoutMessage(text: String) {
        readoutMessage = text
        readoutMessageTime = System.currentTimeMillis()
        spinResultTime = 0L
    }

    // --- Fade from black (corruption death return) ---
    @Volatile var fadeFromBlackTimer: Float = 0f
    @Volatile var glitchTimer: Float = 0f

    // --- Desert farewell → Astro Loop first entry ---
    @Volatile var pendingTbWelcome: Boolean = false

    // --- First-launch intro cinematic ---
    @Volatile var introCinematic: Boolean = false   // True only during the first-ever launch intro
    @Volatile var introTitleTimer: Float = 0f        // Seconds the ASTRO LOOP title has been fading in

    // --- Launch sequence ---
    @Volatile var launchProgress: Float = 0f
    @Volatile var launchPhase: Int = 0

    // --- Yen display (for animation) ---
    @Volatile var displayedYen: Int = 0
    @Volatile var actualYen: Int = 0
    private var yenAnimStartValue: Int = 0
    private var yenAnimTargetValue: Int = 0
    private var yenAnimStartTime: Long = 0L
    private val yenAnimDuration: Float = 3f

    fun updatePilotWalker(deltaTime: Float) {
        val walkWidth = HangarMetrics.effectiveRoomWidth(roomWidth, pilotScreenWidth)
        val baseSpeed = walkWidth * 0.70f  // Pixels per second
        val walkSpeed = when {
            // Intro cinematic: deliberately slow so the walk reads as a cinematic beat (~3.4s)
            introCinematic -> walkWidth * 0.20f
            StoryStateManager.isCorrupted(persistence) -> baseSpeed * 0.5f
            else -> baseSpeed
        }
        if (pilotWalking) {
            val diff = pilotTargetX - pilotX
            if (kotlin.math.abs(diff) < walkSpeed * deltaTime) {
                pilotX = pilotTargetX
                pilotWalking = false
            } else {
                pilotX += if (diff > 0) walkSpeed * deltaTime else -walkSpeed * deltaTime
            }
        }
    }

    fun updateNPCWalkers(deltaTime: Float) {
        // Drain pending changes from UI thread before iterating
        if (pendingNPCAdds.isNotEmpty()) {
            npcWalkers.addAll(pendingNPCAdds)
            pendingNPCAdds.clear()
        }
        if (pendingNPCRemoves.isNotEmpty()) {
            npcWalkers.removeAll { it.pilotIndex in pendingNPCRemoves }
            pendingNPCRemoves.clear()
        }

        val baseNpcSpeed = 0.105f
        val corruptedNow = StoryStateManager.isCorrupted(persistence)
        val seatingNow = BarDressing.forStage(StoryStateManager.stage(persistence)).seatedCrew
        val walkSpeed = if (corruptedNow) baseNpcSpeed * 0.5f else baseNpcSpeed
        for (npc in npcWalkers) {
            if (!seatingNow) {
                // No seating this stage (corruption); clear any stale seated state.
                npc.seated = false; npc.seatedStool = -1; npc.pendingStool = -1
            }
            if (npc.walking) {
                val diff = npc.targetX - npc.x
                if (kotlin.math.abs(diff) < walkSpeed * deltaTime) {
                    npc.x = npc.targetX
                    npc.walking = false
                    npc.idleTimer = kotlin.random.Random.nextFloat() * 3f + 1.5f
                    if (npc.pendingStool >= 0) {           // arrived at a stool → sit
                        npc.seated = true
                        npc.seatedStool = npc.pendingStool
                        npc.pendingStool = -1
                    }
                } else {
                    npc.x += if (diff > 0) walkSpeed * deltaTime else -walkSpeed * deltaTime
                }
            } else {
                npc.idleTimer -= deltaTime
                if (npc.idleTimer <= 0f) {
                    // Choosing a new destination: stand up and clear this walker's claim first.
                    npc.seated = false
                    npc.seatedStool = -1
                    npc.pendingStool = -1
                    val stool = if (seatingNow) {
                        val occupied = npcWalkers.mapNotNull { other ->
                            when {
                                other === npc -> null
                                other.seated -> other.seatedStool
                                other.pendingStool >= 0 -> other.pendingStool
                                else -> null
                            }
                        }.toSet()
                        lowestFreeStool(occupied)
                    } else -1
                    if (stool >= 0) {
                        npc.pendingStool = stool
                        npc.targetX = stoolNormalizedX(stool)
                    } else {
                        npc.targetX = kotlin.random.Random.nextFloat() * 0.8f + 0.1f
                    }
                    npc.walking = true
                }
            }
        }
    }

    /** World X target for each page (pilot stands near the archway connecting to shipyard) */
    fun getPilotWorldTarget(page: Int): Float {
        // Pages tile at roomWidth, so world positions must step by the same stride.
        val stride = HangarMetrics.effectiveRoomWidth(roomWidth, pilotScreenWidth)
        val margin = stride * 0.1f
        val walkable = stride * 0.8f
        return when (page) {
            0 -> margin + 0.9f * walkable                       // Right side of bar
            1 -> stride + margin + 0.5f * walkable              // Center of shipyard
            2 -> {
                val base = 2f * stride + margin + 0.1f * walkable   // Left side of store
                if (astroAtSlotMachine) base - 35f
                else base
            }
            else -> stride + margin + 0.5f * walkable
        }
    }

    fun setPageTarget(page: Int) {
        currentPage = page
        pilotTargetX = getPilotWorldTarget(page)
        if (pilotTargetX != pilotX) {
            pilotWalking = true
        }
    }

    fun updateYenDisplay(deltaTime: Float) {
        if (displayedYen != actualYen) {
            if (yenAnimStartTime == 0L || actualYen != yenAnimTargetValue) {
                // Start new animation (or restart if target changed mid-animation)
                yenAnimStartValue = displayedYen
                yenAnimTargetValue = actualYen
                yenAnimStartTime = System.currentTimeMillis()
            }
            val elapsed = (System.currentTimeMillis() - yenAnimStartTime) / 1000f
            val progress = (elapsed / yenAnimDuration).coerceIn(0f, 1f)
            val totalDiff = actualYen - yenAnimStartValue
            displayedYen = yenAnimStartValue + (totalDiff * progress).toInt()
            if (progress >= 1f) {
                displayedYen = actualYen
                yenAnimStartTime = 0L
            }
        }
    }

    fun initialize() {
        // First-launch intro cinematic is active until the first launch ever commits.
        introCinematic = !persistence.isIntroDone()
        introTitleTimer = 0f

        // Load from persistence
        val savedShipId = persistence.getSelectedShipId()
        selectedShipIndex = ShipDefinitions.ships.indexOfFirst { it.id == savedShipId }.coerceAtLeast(0)

        val savedPilotId = persistence.getSelectedPilotId()
        selectedPilotIndex = PilotDefinitions.pilots.indexOfFirst { it.id == savedPilotId }.coerceAtLeast(0)

        // Corruption state: auto-select Astro+Specter when crystal unlocked and all crew dead
        if (StoryStateManager.isCorrupted(persistence)) {
            if (persistence.isCrystalUnlocked() && StoryStateManager.allCrewDead(persistence)) {
                if (persistence.isAwaitingCrystalReveal()) {
                    // Crystal reveal not yet played — no pilot, Specter on carousel
                    selectedPilotIndex = -1
                    awaitingCrystalReveal = true
                    crystalRevealPhase = CrystalRevealPhase.GLOW
                    val specterIndex = ShipDefinitions.ships.indexOfFirst { it.id == "ship_white" }
                    if (specterIndex >= 0) selectedShipIndex = specterIndex
                } else {
                    val astroIndex = PilotDefinitions.pilots.indexOfFirst { it.id == "pilot_astro" }
                    val specterIndex = ShipDefinitions.ships.indexOfFirst { it.id == "ship_white" }
                    if (astroIndex >= 0) selectedPilotIndex = astroIndex
                    if (specterIndex >= 0) selectedShipIndex = specterIndex
                }
            } else {
                // If currently selected pilot/ship is dead, find first available
                if (!isPilotUnlocked(selectedPilotIndex)) {
                    val firstAvailable = (0 until PilotDefinitions.getPilotCount()).firstOrNull { isPilotUnlocked(it) }
                    if (firstAvailable != null) selectedPilotIndex = firstAvailable
                }
                if (!isShipUnlocked(selectedShipIndex)) {
                    val firstAvailable = (0 until ShipDefinitions.ships.size).firstOrNull { isShipUnlocked(it) }
                    if (firstAvailable != null) selectedShipIndex = firstAvailable
                }
            }
        }

        // Astro Loop Mode: default to Astro + Specter only on first entry;
        // otherwise keep the saved (last-flown) selection loaded above.
        if (StoryStateManager.isAstroLoop(persistence) && persistence.isAstroLoopFirstEntry()) {
            val astroIndex = PilotDefinitions.pilots.indexOfFirst { it.id == "pilot_astro" }
            val specterIndex = ShipDefinitions.ships.indexOfFirst { it.id == "ship_white" }
            if (astroIndex >= 0) selectedPilotIndex = astroIndex
            if (specterIndex >= 0) selectedShipIndex = specterIndex
            // Boot-path twin of resetForReturn's fadeFromWhite branch: if the app was
            // restarted between the timeline shift and the hangar, TB's "Welcome back."
            // must still fire — and the first-entry flag is consumed so it fires once.
            pendingTbWelcome = true
            persistence.clearAstroLoopFirstEntry()
        }

        // Initialize NPC walkers for all unlocked pilots except selected
        rebuildNpcWalkers()

        actualYen = persistence.getYen()
        displayedYen = actualYen

        hasDiscoveredEvolutions = persistence.getDiscoveredEvolutions().isNotEmpty()

        // Initialize TB-26 bartender position at center of counter
        tb26BarX = HangarMetrics.effectiveRoomWidth(roomWidth, pilotScreenWidth) / 2f
        tb26BarTargetX = tb26BarX
        tb26BarMoving = false
        tb26BarPauseTimer = 1f + kotlin.random.Random.nextFloat() * 1.5f

        // Load codex discovery state
        codexDiscovered = persistence.isCodexDiscovered()
        hatchOpen = persistence.isCodexDiscovered()  // Stay open if already discovered
        codexHintGiven = persistence.isCodexHintGiven()

        // Load mute state
        audioMode = persistence.getAudioMode()
        vibrationMuted = persistence.isVibrationMuted()

        // Load Astro hint state
        astroHintCount = persistence.getAstroHintCount()
        astroHinted = persistence.isAstroHinted()
        allEvolutionsHinted = persistence.isAllEvolutionsHinted()

        // Astro hangs at slot machine in corruption (before all crew are dead, or during crystal reveal)
        astroAtSlotMachine = StoryStateManager.isCorrupted(persistence) &&
            (!StoryStateManager.allCrewDead(persistence) || awaitingCrystalReveal)

        // Start page: the bar during the intro cinematic, otherwise the shipyard.
        // Forcing the bar here keeps the opening robust whenever initialize() re-runs on
        // a full view rebuild (config change / process death). An ordinary pause/resume
        // does not re-run initialize(), so it leaves an in-progress cinematic in place.
        val startPage = if (introCinematic) 0 else 1
        currentPage = startPage
        pilotX = getPilotWorldTarget(startPage)
        pilotTargetX = pilotX
        pilotWalking = false
    }

    fun getSelectedShip() = ShipDefinitions.getShipByIndex(selectedShipIndex)
    fun getSelectedPilot() = PilotDefinitions.getPilotByIndex(selectedPilotIndex)

    fun isShipUnlocked(index: Int): Boolean {
        val ship = ShipDefinitions.getShipByIndex(index) ?: return false
        if (!persistence.isShipUnlocked(ship.id)) return false
        // Corruption gating: dead ships and Specter (until crystal unlocked)
        if (StoryStateManager.isCorrupted(persistence)) {
            if (ship.id == "ship_white" && !persistence.isCrystalUnlocked()) return false
            if (StoryStateManager.isShipDead(persistence, ship.id)) return false
        }
        return true
    }

    fun canUnlockShip(index: Int): Boolean {
        // First ship is always unlockable (starter ship)
        if (index == 0) return true
        // In corruption, Specter is gated by crystal, not purchase
        val ship = ShipDefinitions.getShipByIndex(index)
        if (ship != null && StoryStateManager.isCorrupted(persistence) && ship.id == "ship_white" && !persistence.isCrystalUnlocked()) return false
        // Can only unlock if previous ship is unlocked (sequential)
        return isShipUnlocked(index - 1)
    }

    fun isPilotUnlocked(index: Int): Boolean {
        val pilot = PilotDefinitions.getPilotByIndex(index) ?: return false
        if (!persistence.isPilotUnlocked(pilot.id)) return false
        // Corruption gating: dead pilots and Astro (until crystal unlocked)
        if (StoryStateManager.isCorrupted(persistence)) {
            if (pilot.id == "pilot_astro" && (!persistence.isCrystalUnlocked() || awaitingCrystalReveal)) return false
            if (StoryStateManager.isPilotDead(persistence, pilot.id)) return false
        }
        return true
    }

    fun isPilotDeadInCorruption(index: Int): Boolean {
        val pilot = PilotDefinitions.getPilotByIndex(index) ?: return false
        if (!StoryStateManager.isCorrupted(persistence)) return false
        if (!persistence.isPilotUnlocked(pilot.id)) return false
        return StoryStateManager.isPilotDead(persistence, pilot.id)
    }

    fun canUnlockPilot(index: Int): Boolean {
        if (index == 0) return true
        if (index != persistence.getNextPilotIndex()) return false
        return isPilotUnlocked(index - 1)
    }

    fun checkPilotUnlockCondition(): Boolean {
        val nextIndex = persistence.getNextPilotIndex()
        if (nextIndex >= PilotDefinitions.getPilotCount()) return false

        val pilot = PilotDefinitions.getPilotByIndex(nextIndex) ?: return false

        // Post-desert: one pilot per run, except Astro keeps ALL_OTHERS + 2-run cooldown
        if (StoryStateManager.hasLoopedBefore(persistence)) {
            if (pilot.unlockType != PilotUnlockType.ALL_OTHERS) {
                return persistence.getRunsSincePilotUnlock() >= 1
            }
        }

        val runsSinceUnlock = persistence.getRunsSincePilotUnlock()
        if (runsSinceUnlock < 2) return false

        if (runsSinceUnlock >= 17
            && pilot.unlockType != PilotUnlockType.ALL_OTHERS
            && pilot.unlockType != PilotUnlockType.JACKPOT) return true
        return when (pilot.unlockType) {
            PilotUnlockType.FREE -> true
            PilotUnlockType.TOTAL_YEN_EARNED -> persistence.getTotalYenEarned() >= pilot.unlockThreshold
            PilotUnlockType.TOTAL_DAMAGE_TAKEN -> persistence.getTotalDamageTaken() >= pilot.unlockThreshold
            PilotUnlockType.SURVIVE_SECONDS -> persistence.getBestSurvivalSeconds() >= pilot.unlockThreshold
            PilotUnlockType.KILL_STREAK -> persistence.getBestKillStreak() >= pilot.unlockThreshold
            PilotUnlockType.TOTAL_DEATHS -> persistence.getTotalDeaths() >= pilot.unlockThreshold
            PilotUnlockType.KILLS_IN_SINGLE_RUN -> persistence.getBestSingleRunKills() >= pilot.unlockThreshold
            PilotUnlockType.WEAPONS_DISCOVERED -> persistence.getWeaponsDiscovered().size >= pilot.unlockThreshold
            PilotUnlockType.EVOLUTIONS_DISCOVERED -> persistence.getDiscoveredEvolutions().size >= pilot.unlockThreshold
            PilotUnlockType.TOTAL_KILLS -> persistence.getTotalKills() >= pilot.unlockThreshold
            PilotUnlockType.JACKPOT -> false  // Unlocked via slot machine jackpot
            PilotUnlockType.CONTINUOUS_FLIGHT_SECONDS -> persistence.getBestContinuousFlightSeconds() >= pilot.unlockThreshold
            PilotUnlockType.ALL_OTHERS -> {
                for (i in 0 until PilotDefinitions.getPilotCount() - 1) {
                    if (!isPilotUnlocked(i)) return false
                }
                for (i in 0 until ShipDefinitions.getShipCount()) {
                    if (!isShipUnlocked(i)) return false
                }
                true
            }
        }
    }

    fun recruitNextPilot(): PilotDef? {
        val nextIndex = persistence.getNextPilotIndex()
        val pilot = PilotDefinitions.getPilotByIndex(nextIndex) ?: return null
        persistence.unlockPilot(pilot.id)
        persistence.setNextPilotIndex(nextIndex + 1)
        persistence.resetRunsSincePilotUnlock()
        return pilot
    }

    fun isWhiskersJackpotEligible(): Boolean {
        val nextIndex = persistence.getNextPilotIndex()
        val pilot = PilotDefinitions.getPilotByIndex(nextIndex) ?: return false
        if (pilot.unlockType != PilotUnlockType.JACKPOT) return false
        return persistence.getRunsSincePilotUnlock() >= 1
    }

    fun shouldShowHints(): Boolean {
        val nextIndex = persistence.getNextPilotIndex()
        if (nextIndex >= PilotDefinitions.getPilotCount()) return false
        return persistence.getRunsSincePilotUnlock() >= 1
    }

    fun getNextPilotIndex(): Int = persistence.getNextPilotIndex()

    fun isReadyToLaunch(): Boolean {
        return isShipUnlocked(selectedShipIndex) && isPilotUnlocked(selectedPilotIndex)
    }

    /**
     * (Re)build the walker roster from currently unlocked pilots, minus the selected
     * one (corruption also filters dead pilots and Astro). Stale pending walker
     * mutations are dropped alongside the list they were meant for. Must be called
     * with selectedPilotIndex already final. Runs at initialize(), on every
     * corruption return (via initCorruptionState) and on every astro-loop return —
     * without the astro-loop rebuild, the empty end-of-corruption roster leaks into
     * the freshly-revived bar and pilots only reappear as the player cycles them.
     */
    fun rebuildNpcWalkers() {
        val newWalkers = mutableListOf<WalkerNPC>()
        val npcRandom = kotlin.random.Random(System.currentTimeMillis())
        val isCorrupted = StoryStateManager.isCorrupted(persistence)
        for (i in 0 until PilotDefinitions.getPilotCount()) {
            if (i != selectedPilotIndex && isPilotUnlocked(i)) {
                val pilot = PilotDefinitions.getPilotByIndex(i) ?: continue
                // Skip dead pilots in corruption state
                if (isCorrupted && StoryStateManager.isPilotDead(persistence, pilot.id)) continue
                // Skip Astro in corruption (he's at the slot machine / unavailable until crystal)
                if (isCorrupted && pilot.id == "pilot_astro") continue
                newWalkers.add(WalkerNPC(
                    pilotIndex = i,
                    color = pilot.color,
                    x = npcRandom.nextFloat() * 0.8f + 0.1f,
                    targetX = npcRandom.nextFloat() * 0.8f + 0.1f,
                    walking = false,
                    idleTimer = npcRandom.nextFloat() * 2.5f + 1f
                ))
            }
        }
        npcWalkers = CopyOnWriteArrayList(newWalkers)
        pendingNPCAdds.clear()
        pendingNPCRemoves.clear()
    }

    /**
     * Reinitialize hangar state for corruption phase.
     * Called on return from the boss-victory run (first time corruption activates)
     * and on subsequent corruption-phase returns.
     * Rebuilds NPC walkers to exclude dead pilots and auto-selects Astro+Specter
     * when the crystal is unlocked and all crew are dead.
     */
    fun initCorruptionState(persistence: PersistenceManager) {
        // Astro hangs at slot machine in corruption (before all crew are dead, or during crystal reveal)
        astroAtSlotMachine = !StoryStateManager.allCrewDead(persistence) || persistence.isAwaitingCrystalReveal()

        // Auto-select Astro+Specter when crystal unlocked and all crew dead
        if (persistence.isCrystalUnlocked() && StoryStateManager.allCrewDead(persistence)) {
            if (persistence.isAwaitingCrystalReveal()) {
                // Crystal reveal not yet played — no pilot, Specter on carousel
                selectedPilotIndex = -1
                awaitingCrystalReveal = true
                crystalRevealPhase = CrystalRevealPhase.GLOW
                val specterIndex = ShipDefinitions.ships.indexOfFirst { it.id == "ship_white" }
                if (specterIndex >= 0) selectedShipIndex = specterIndex
            } else {
                // Reveal already played — select Astro+Specter normally
                val astroIndex = PilotDefinitions.pilots.indexOfFirst { it.id == "pilot_astro" }
                val specterIndex = ShipDefinitions.ships.indexOfFirst { it.id == "ship_white" }
                if (astroIndex >= 0) selectedPilotIndex = astroIndex
                if (specterIndex >= 0) selectedShipIndex = specterIndex
            }
        } else {
            // If currently selected pilot/ship is now dead, find first available
            if (!isPilotUnlocked(selectedPilotIndex)) {
                val firstAvailable = (0 until PilotDefinitions.getPilotCount()).firstOrNull { isPilotUnlocked(it) }
                if (firstAvailable != null) selectedPilotIndex = firstAvailable
            }
            if (!isShipUnlocked(selectedShipIndex)) {
                val firstAvailable = (0 until ShipDefinitions.ships.size).firstOrNull { isShipUnlocked(it) }
                if (firstAvailable != null) selectedShipIndex = firstAvailable
            }
        }

        // After selection is final — dead pilots and Astro filtered out by isPilotUnlocked()
        rebuildNpcWalkers()
    }

    fun saveAstroHintState() {
        persistence.setAstroHintCount(astroHintCount)
        persistence.setAstroHinted(astroHinted)
    }

    fun saveSelection() {
        getSelectedShip()?.let { persistence.setSelectedShipId(it.id) }
        getSelectedPilot()?.let { persistence.setSelectedPilotId(it.id) }
    }
}
