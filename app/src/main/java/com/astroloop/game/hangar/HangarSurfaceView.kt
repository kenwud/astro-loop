package com.astroloop.game.hangar

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.astroloop.game.MainActivity
import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.ScreenLayout
import com.astroloop.game.core.SoundManager
import com.astroloop.game.core.StoryStateManager
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.data.PilotDefinitions
import com.astroloop.game.data.TelemetryManager
import com.astroloop.game.data.ShipDefinitions
import com.astroloop.game.data.StoreUpgradeDefinitions
import com.astroloop.game.render.CrystalOrbPath
import com.astroloop.game.render.FontManager
import com.astroloop.game.render.IconCache
import kotlin.math.abs
import kotlin.math.pow

class HangarSurfaceView(
    context: Context,
    private val onLaunch: (shipId: String, pilotId: String) -> Unit
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    companion object {
        /**
         * Haptics, in one place so they can be tuned as a set rather than one call site at a time.
         *
         * Three strengths, and the distinction is what each one is *for*:
         * - **Button tap** — a control acknowledging a press. Short and light, the feel of a small
         *   physical button. Every button in the hangar gets this; card flips deliberately do not,
         *   because a flip is reading rather than acting.
         * - **Hold hum** — the store tile filling under a held finger. The only repeating one.
         * - **Purchase knock** — money actually left the wallet. Upgrades and ships share it, so
         *   a spend feels the same wherever it happens.
         */
        const val HAPTIC_BUTTON_MS = 12L
        const val HAPTIC_BUTTON_AMPLITUDE = 70

        /**
         * Hold-fill pulse, on/off in milliseconds.
         *
         * The fill sweeps from `TAP_SECONDS` to the purchase — 750ms — so the period sets how many
         * pulses that sweep contains. At the original 90/90 it was about four, which read as a slow
         * tick running alongside the fill rather than as the tile charging. At 35/35 it is about
         * eleven: fast enough to feel continuous, slow enough that the motor still articulates each
         * pulse. This is the number to turn if it wants more or less urgency.
         */
        const val HAPTIC_HOLD_ON_MS = 35L
        const val HAPTIC_HOLD_OFF_MS = 35L
        const val HAPTIC_HOLD_AMPLITUDE = 22   // raised with the rate; short pulses read weaker

        const val HAPTIC_PURCHASE_MS = 45L
        const val HAPTIC_PURCHASE_AMPLITUDE = 110

        /**
         * The eight permanent store upgrades, in tile order, with the names the slot machine
         * announces them by. One list rather than two parallel ones so a jackpot can name the
         * upgrade at the reveal and grant that same upgrade when the reels stop.
         */
        val SLOT_UPGRADES: List<Pair<String, String>> = listOf(
            "health" to "SALVAGE PLATE",
            "shields" to "DEFLECTOR RIG",
            "speed" to "NITRO BOOST",
            "damage" to "HOT ROUNDS",
            "crit" to "LUCKY ROUNDS",
            "magnet" to "HAUL LINE",
            "yen_bonus" to "FINDER'S FEE",
            "salvage" to "SCAVENGER RIG"
        )

        fun upgradeDisplayName(id: String): String? =
            SLOT_UPGRADES.firstOrNull { it.first == id }?.second

        // Pilot card flip (tap the selected portrait to read its passive).
        // The cycle is: FADE out the portrait, hold the passive face, FADE it back out.
        // So the passive is on screen for (DURATION - FADE) — keep that at the number
        // you actually want players to have for reading it.
        const val PILOT_FLIP_FADE = 0.225f      // one cross-fade leg
        const val PILOT_FLIP_DURATION = 2.225f  // → passive readable for 2.0s

        // Store card flip (tap a tile to read its back). Same cycle as the pilot flip — FADE the
        // front out, hold the back, FADE it out — but a stat block is a denser read than one line
        // of passive text, so the back gets ~4s rather than 2s.
        const val STORE_FLIP_FADE = 0.225f      // one cross-fade leg, matched to the pilot card
        const val STORE_FLIP_DURATION = 4.225f  // → back readable for 4.0s

        // Hold-to-buy fill exit. Nothing on screen may go without a visible exit, UI elements
        // included, and HoldToBuy.advance() zeroes its own progress the instant it cancels or
        // completes — without this the fill would snap to nothing on an early release, or never
        // be seen at 100% before vanishing on a successful purchase. Short: it only covers the
        // one-frame gap HoldToBuy leaves behind, not a deliberate animation beat of its own.
        const val STORE_HOLD_EXIT_DURATION = 0.3f

        // The Time Crystal tile — 9th tile, index 8, auto-equipped rather than purchased. Named
        // here so later work (drawing its card back) can reference the tile without a bare 8.
        const val CRYSTAL_TILE_INDEX = 8
    }

    private var gameThread: Thread? = null
    @Volatile private var running = false

    val persistence = PersistenceManager(context)
    private val telemetryManager = TelemetryManager(context)
    // internal (not private): the touch handlers can't be driven through real MotionEvent
    // dispatch in a unit test — upgradeRects is only populated by a live Canvas draw pass, which
    // Robolectric can't provide a valid Surface for. Tests inject a rect directly and call the
    // handlers, which needs a way to read state/renderer back out. Same convention as
    // HangarState's own `internal val persistence` and ChatSystem's internal test members.
    internal val state = HangarState(persistence)
    internal val renderer = HangarRenderer(persistence)
    private val chatSystem = ChatSystem()

    private var screenWidth = 0f
    private var screenHeight = 0f
    private var renderScale: Float = 1f
    private var roomWidth: Float = 0f
    private var crystalGlowSoundPlayed = false

    // System-cutout insets in physical px, forwarded by MainActivity. Divided by
    // renderScale into design units when building the ScreenLayout.
    private var insetLeftPx = 0f
    private var insetTopPx = 0f
    private var insetRightPx = 0f
    private var insetBottomPx = 0f

    // @Volatile: written on the UI thread (applyInsets/surfaceChanged), read by the render thread.
    @Volatile var layout: ScreenLayout = ScreenLayout.compute(GameConfig.DESIGN_WIDTH, GameConfig.DESIGN_HEIGHT)
        private set

    /** Called by MainActivity when display-cutout insets become known or change. */
    fun applyInsets(left: Float, top: Float, right: Float, bottom: Float) {
        // Android delivers identical insets repeatedly; ignore no-op deliveries so the
        // steady state never re-touches state the render thread reads. Real work happens
        // only on an actual cutout change (e.g. fold/unfold), which also fires surfaceChanged.
        if (left == insetLeftPx && top == insetTopPx &&
            right == insetRightPx && bottom == insetBottomPx
        ) return
        insetLeftPx = left; insetTopPx = top; insetRightPx = right; insetBottomPx = bottom
        if (width > 0 && height > 0) {
            applyScreenDimensions(width, height)
            renderer.initialize(layout, roomWidth)
            state.pilotScreenWidth = screenWidth
            state.roomWidth = roomWidth
            initShipPositions()
        }
    }

    // Vibration
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private var isVibratingHalo = false
    private var isVibratingHold = false

    /**
     * The platform's own drag threshold, in real pixels for this screen's density (8dp).
     *
     * Read from ViewConfiguration rather than hard-coded so it means the same thing on a phone and
     * a tablet. The previous 15f was raw pixels — roughly 5dp on a modern phone, tighter than
     * Android's minimum for calling a movement a drag at all, which is why holding a store tile
     * asked for more steadiness than it should have.
     */
    private val swipeSlop: Float =
        android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var isVibrationMuted: Boolean = context.getSharedPreferences("astrohunt_save", Context.MODE_PRIVATE)
        .getBoolean("vibration_muted", false)

    // Touch handling
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastTouchTime: Long = 0
    private var isDragging = false
    /**
     * Set when a gesture is abandoned, and consumed by the ACTION_UP that may still follow.
     *
     * ACTION_CANCEL ends a gesture outright, but ACTION_POINTER_UP does not — the last finger
     * lifting still delivers an ACTION_UP. Abandoning clears [isDragging], so without this flag
     * that release would take the `!isDragging` branch and be dispatched as a *tap*, turning a
     * deliberate drag into a press. In this hangar a press buys things: on the shipyard it can
     * unlock a ship outright for up to ¥100,000 with no confirmation.
     */
    private var gestureAbandoned = false
    private var activeSwipe: SwipeTarget = SwipeTarget.NONE
    private var shipDragPossible = false
    // internal (not private): test seam, same convention as heldUpgradeIndex below — lets a test
    // confirm the spin button was (or wasn't) grabbed from ACTION_DOWN without a live draw pass.
    internal var spinButtonHeld = false
    private var stateInitialized = false
    /**
     * internal (not private): test seam, same convention as [heldUpgradeIndex] below.
     *
     * The clock is normally ticked by `updateBrowsing` on the render thread, which a unit test has
     * no way to run — so without this a test could dispatch a press and a release but never age the
     * press in between, which is precisely the distinction the release path now turns on.
     */
    internal val storeHold = HoldToBuy()
    /**
     * Which tile the in-flight hold belongs to; survives HoldToBuy going idle on completion.
     *
     * internal (not private): test seam, same convention as state/renderer above — lets a test
     * confirm whether ACTION_DOWN started a hold without driving the render thread's update loop,
     * which is the only other place this is otherwise read.
     */
    internal var heldUpgradeIndex = -1
    /**
     * Seconds left in the hold-fill's exit fade — a completion flash on a finished purchase, a
     * plain fade on an early release or cancel. Lives here rather than in [HoldToBuy] because it
     * is a rendering concern, not part of the buy/no-buy decision; [HangarState]'s
     * `storeHoldExit*` fields are what the renderer actually reads, mirrored from this each frame
     * the same way `storeHoldIndex`/`storeHoldProgress` mirror [storeHold] itself.
     *
     * `@Volatile` because it is armed on the UI thread (via touch handling and `surfaceDestroyed`)
     * and decayed on the game thread. A missed write would leave the decay block never running and
     * `storeHoldExitAlpha` stuck at 1f — a fill drawn on a tile nobody is touching.
     */
    @Volatile private var storeHoldExitTimer = 0f
    /**
     * Tile a completed hold just bought. The finger is still down when a hold completes, so the
     * ACTION_UP that follows falls through to handleStoreTap on that same tile — this tells that
     * call to skip the flip exactly once. Consumed (cleared) the moment it's checked, so it can
     * never leak into a later, genuine tap on the same tile.
     */
    private var suppressFlipIndex = -1

    private enum class SwipeTarget { NONE, PAGE, SHIP_DRAG }

    // Page scroll physics — all values are per-second, applied via deltaTime
    private val pageFrictionPerSecond = 0.05f     // 5% velocity remains after 1s (iOS paging feel)
    private val pageSnapDecay = 1e-10f            // snap settles in ~0.3s
    private val pageVelocityThreshold = 250f      // px/s — lower = responds to gentle flicks

    init {
        holder.addCallback(this)
        isFocusable = true
        FontManager.initialize(context)
    }

    private fun applyScreenDimensions(physW: Int, physH: Int) {
        renderScale = minOf(physW / GameConfig.DESIGN_WIDTH, physH / GameConfig.DESIGN_HEIGHT)
        screenWidth = physW / renderScale
        screenHeight = physH / renderScale
        layout = ScreenLayout.compute(
            width = screenWidth,
            height = screenHeight,
            insetLeft = insetLeftPx / renderScale,
            insetTop = insetTopPx / renderScale,
            insetRight = insetRightPx / renderScale,
            insetBottom = insetBottomPx / renderScale
        )
        roomWidth = HangarMetrics.roomWidth(
            screenWidth = screenWidth,
            contentWidth = layout.content.width,
            smallestScreenWidthDp = context.resources.configuration.smallestScreenWidthDp
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        IconCache.preload(context)

        applyScreenDimensions(width, height)

        renderer.initialize(layout, roomWidth)
        state.pilotScreenWidth = screenWidth
        state.roomWidth = roomWidth
        // Only initialize state on first surface creation — on re-attach after
        // a run, resetForReturn() has already set the correct state (e.g. bar page)
        if (!stateInitialized) {
            state.initialize()
            stateInitialized = true
        }
        SoundManager.applyAudioMode(state.audioMode)
        isVibrationMuted = state.vibrationMuted
        initShipPositions()

        if (persistence.isFirstLaunch()) {
            // Start on bar page
            state.currentPage = 0
            state.pilotX = state.getPilotWorldTarget(0)
            state.pilotTargetX = state.pilotX
            state.pilotWalking = false

            // Start with no yen — the counter is hidden during the intro cinematic anyway.
            persistence.setYen(0)
            state.actualYen = 0
            state.displayedYen = 0

            // Queue intro messages
            chatSystem.onFirstLaunch(state)

            persistence.setFirstLaunchComplete()
        }

        // Refresh the music set from current story stage before any ambient playback
        SoundManager.activeSet = StoryStateManager.stageMusicSet(persistence)

        // Start ambient for the initial page — but the intro cinematic plays the drone bed.
        if (state.introCinematic) {
            SoundManager.playAmbient("sfx_intro_drone")   // looping bed under the silent bar
        } else {
            SoundManager.playAmbient(getAmbientForPage(state.currentPage))
        }

        if (!running) {
            running = true
            gameThread = Thread(this)
            gameThread?.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val prevWidth = screenWidth
        applyScreenDimensions(width, height)
        renderer.initialize(layout, roomWidth)
        state.pilotScreenWidth = screenWidth
        state.roomWidth = roomWidth
        initShipPositions()
        // Re-anchor pilot / TB-26 to the new layout whenever the surface dimensions change
        // (a live fold/unfold) or on the first valid layout (screenWidth was NaN — 0/0 in IEEE
        // 754 — when surfaceCreated() fired before this.width/height were known). A same-size
        // resume (prevWidth == screenWidth) is left alone so an in-progress walk isn't snapped.
        // Position fields are @Volatile and written together, so the render thread never reads a
        // torn/inconsistent pair; a fold is disruptive anyway, so we snap rather than animate.
        if (stateInitialized && (state.pilotX.isNaN() || screenWidth != prevWidth)) {
            state.pilotX = state.getPilotWorldTarget(state.currentPage)
            state.pilotTargetX = state.pilotX
            state.pilotWalking = false
            state.tb26BarX = HangarMetrics.effectiveRoomWidth(roomWidth, screenWidth) / 2f
            state.tb26BarTargetX = state.tb26BarX
        }
    }

    private fun initShipPositions() {
        val walkwayY = screenHeight * 0.60f
        state.shipRestingY = walkwayY + (screenHeight - walkwayY) * 0.4f
        if (!state.isDraggingShip) {
            state.shipDragY = state.shipRestingY
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopHaloRumble()
        stopHoldRumble()
        // A hold or a held spin button otherwise survives the surface being destroyed: this only
        // stops the render thread, so on resume the retained elapsed/held state would pick right
        // back up and a purchase (or an auto-spin) could fire with no finger on screen.
        cancelHold()
        spinButtonHeld = false
        running = false
        gameThread?.join()
    }

    override fun run() {
        var lastTime = System.nanoTime()

        while (running) {
            val currentTime = System.nanoTime()
            val deltaTime = (currentTime - lastTime) / 1_000_000_000f
            lastTime = currentTime

            // Mirror GameThread's resilience: a throwable from a single update/render
            // frame (e.g. on resume from background) must not kill the process.
            try {
                update(deltaTime)
                render()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun update(deltaTime: Float) {
        when (state.phase) {
            HangarPhase.BROWSING -> updateBrowsing(deltaTime)
            HangarPhase.LAUNCHING -> updateLaunching(deltaTime)
            else -> {}
        }

        // Update pilot walker
        state.updatePilotWalker(deltaTime)

        // Update NPC walkers
        state.updateNPCWalkers(deltaTime)

        // Animate yen counter
        state.updateYenDisplay(deltaTime)

        // Tick fade-from-black timer (corruption death return)
        if (state.fadeFromBlackTimer > 0f) {
            state.fadeFromBlackTimer = (state.fadeFromBlackTimer - deltaTime).coerceAtLeast(0f)
        }
        if (state.glitchTimer > 0f) {
            state.glitchTimer = (state.glitchTimer - deltaTime).coerceAtLeast(0f)
        }
    }

    private fun updateBrowsing(deltaTime: Float) {
        // --- Page scroll physics ---
        if (activeSwipe != SwipeTarget.PAGE) {
            if (abs(state.pageVelocity) > 0.1f) {
                state.pageScrollOffset += state.pageVelocity * deltaTime
                state.pageVelocity *= pageFrictionPerSecond.pow(deltaTime)
            }
            if (abs(state.pageVelocity) < 50f) {
                val diff = -state.pageScrollOffset
                if (abs(diff) > 1f) {
                    state.pageScrollOffset += diff * (1f - pageSnapDecay.pow(deltaTime))
                } else {
                    state.pageScrollOffset = 0f
                    state.pageVelocity = 0f
                }
            }
        }

        // Intro cinematic: advance the ASTRO LOOP title fade-in once on the launchpad.
        if (state.introCinematic && state.currentPage == 1) {
            state.introTitleTimer += deltaTime
        }

        // Crystal reveal: play the glow shimmer once when the GLOW phase begins.
        if (state.crystalRevealPhase == HangarState.CrystalRevealPhase.GLOW) {
            if (!crystalGlowSoundPlayed) {
                crystalGlowSoundPlayed = true
                SoundManager.playSFX("sfx_crystal_glow")
            }
        } else if (state.crystalRevealPhase == HangarState.CrystalRevealPhase.NONE) {
            crystalGlowSoundPlayed = false
        }

        // Crystal reveal: trigger orb travel when store page settles
        if (state.awaitingCrystalReveal && state.currentPage == 2
            && state.crystalRevealPhase == HangarState.CrystalRevealPhase.GLOW
            && abs(state.pageScrollOffset) < 2f && abs(state.pageVelocity) < 50f) {
            state.crystalRevealPhase = HangarState.CrystalRevealPhase.ORB_TRAVEL
            state.crystalRevealTimer = 0f
            SoundManager.playSFX("sfx_crystal_orb")
        }

        // Crystal reveal animation
        if (state.crystalRevealPhase == HangarState.CrystalRevealPhase.ORB_TRAVEL) {
            state.crystalRevealTimer += deltaTime
            if (state.crystalRevealTimer >= CrystalOrbPath.TRAVEL_DURATION) {
                state.crystalRevealPhase = HangarState.CrystalRevealPhase.FLASH
                state.crystalRevealTimer = 0f
                SoundManager.playSFX("sfx_crystal_activate")
            }
        } else if (state.crystalRevealPhase == HangarState.CrystalRevealPhase.FLASH) {
            state.crystalRevealTimer += deltaTime
            if (state.crystalRevealTimer >= CrystalOrbPath.FLASH_DURATION) {
                // Reveal complete — select Astro
                // Compute Astro's actual slot machine position (base, without walker offset)
                val stride = HangarMetrics.effectiveRoomWidth(roomWidth, state.pilotScreenWidth)
                val margin = stride * 0.1f
                val walkable = stride * 0.8f
                val slotMachinePos = 2f * stride + margin + 0.1f * walkable
                state.crystalRevealPhase = HangarState.CrystalRevealPhase.DONE
                state.awaitingCrystalReveal = false
                state.astroAtSlotMachine = false
                persistence.setAwaitingCrystalReveal(false)
                val astroIndex = PilotDefinitions.pilots.indexOfFirst { it.id == "pilot_astro" }
                if (astroIndex >= 0) state.selectedPilotIndex = astroIndex
                // Place pilot walker exactly where Astro was at the slot machine
                state.pilotX = slotMachinePos
                state.pilotTargetX = slotMachinePos
                state.pilotWalking = false
            }
        }

        // --- Lamp sway momentum (feeds from velocity, decays over time) ---
        if (abs(state.pageVelocity) > 50f) {
            state.swayMomentum = state.pageVelocity
        }
        state.swayMomentum *= 0.93f
        if (abs(state.swayMomentum) < 1f) state.swayMomentum = 0f

        // --- Ship scroll animation ---
        if (kotlin.math.abs(state.shipScrollOffset) > 1f) {
            state.shipScrollOffset += (0f - state.shipScrollOffset) * 0.12f
        } else {
            state.shipScrollOffset = 0f
        }

        // --- Ship drag snap-back animation ---
        if (!state.isDraggingShip && abs(state.shipDragY - state.shipRestingY) > 1f) {
            state.shipDragY += (state.shipRestingY - state.shipDragY) * 0.15f
        } else if (!state.isDraggingShip) {
            state.shipDragY = state.shipRestingY
        }

        // Halo vibration — light rumble while ship is held in halo zone
        val haloCenter = screenHeight / 2f
        val inHalo = state.isDraggingShip && abs(state.shipDragY - haloCenter) < 60f
        if (inHalo) startHaloRumble() else stopHaloRumble()

        // Update slot machine animation
        if (state.isSpinning) {
            val now = System.currentTimeMillis()
            if (now >= state.reelStopTimes[2]) completeSpin(now)
        }

        // Auto-spin when holding the spin button (disabled after jackpot)
        if (spinButtonHeld && !state.isSpinning && state.spinResultTime > 0 && state.spinResultUpgrade == null) {
            val sinceResult = System.currentTimeMillis() - state.spinResultTime
            if (sinceResult > 600L) {
                handleSlotSpin()
            }
        }

        // Astro auto-gambling at slot machine in corruption
        if (state.astroAtSlotMachine && state.currentPage == 2) {
            state.astroAutoSpinTimer += deltaTime
            if (state.astroAutoSpinTimer >= state.astroAutoSpinInterval && !state.isSpinning) {
                if (state.actualYen >= 100) {
                    handleSlotSpin()
                }
                state.astroAutoSpinTimer = 0f
                state.astroAutoSpinInterval = 5f + kotlin.random.Random.nextFloat() * 3f
            }
        }

        // Pilot card fade — selected card full opacity, every other card dims to 35% whether it is
        // locked or not. The next recruit's card briefly had a brighter floor here to rescue its
        // silhouette, which made it outshine an unlocked crewmate sitting next to it: with MEDIC
        // selected, the locked BRUTUS read as more prominent than the recruited RASCAL. A locked
        // card differs by what it contains, not by how brightly the grid draws it.
        val lerpSpeed = 16f * deltaTime
        for (i in state.pilotCardFades.indices) {
            val target = if (i == state.selectedPilotIndex) 1f else 0.35f
            state.pilotCardFades[i] += (target - state.pilotCardFades[i]) * lerpSpeed
        }

        // Pilot card fade animation (tap selected pilot to reveal passive effect)
        if (state.pilotFlipTimer > 0f) {
            state.pilotFlipTimer -= deltaTime
            if (state.pilotFlipTimer <= 0f) {
                state.pilotFlipTimer = 0f
                state.pilotFlipIndex = -1
                state.pilotFlipProgress = 0f
                state.pilotFlipShowBack = false
            } else {
                val elapsed = PILOT_FLIP_DURATION - state.pilotFlipTimer
                state.pilotFlipShowBack = elapsed >= PILOT_FLIP_FADE
                // pilotFlipProgress = alpha of current visible content (1=fully visible, 0=invisible)
                state.pilotFlipProgress = when {
                    elapsed < PILOT_FLIP_FADE -> 1f - elapsed / PILOT_FLIP_FADE          // fading out: 1→0
                    state.pilotFlipTimer < PILOT_FLIP_FADE -> state.pilotFlipTimer / PILOT_FLIP_FADE  // fading in: 0→1
                    else -> 1f                                                            // back fully visible
                }
            }
        }

        // Store card flips — same cycle as the pilot flip above, its own duration, and one clock
        // per tile so turning over a second card leaves the first one turned over.
        state.advanceStoreFlips(deltaTime)
        state.advanceHintNoteReveal(deltaTime)

        // Hold-to-buy clock. Completing buys exactly one level; the machine goes idle on its own.
        if (storeHold.advance(deltaTime)) {
            // HoldToBuy.advance() has already reset its own progress to 0 by the time it returns
            // true, so without capturing the completed index first and freezing the fill full,
            // the bar would never be seen at 100% — it would just disappear.
            // The success flash is armed before the purchase runs, so it depends on canStartHold()
            // having already refused holds on maxed and unaffordable tiles. If those guards ever
            // move or loosen, a declined purchase would flash as though it had succeeded.
            beginHoldExit(heldUpgradeIndex, progress = 1f, success = true)
            purchaseHeldUpgrade(heldUpgradeIndex)
            stopHoldRumble()
            purchasePulse()
        }
        // The hum tracks the fill exactly — see HoldToBuy.isFilling.
        if (storeHold.isFilling) startHoldRumble() else stopHoldRumble()
        state.storeHoldIndex = storeHold.index
        state.storeHoldProgress = storeHold.progress

        // Decay the hold-fill's exit — a completion flash or an early-release fade, never a snap.
        if (storeHoldExitTimer > 0f) {
            storeHoldExitTimer = (storeHoldExitTimer - deltaTime).coerceAtLeast(0f)
            state.storeHoldExitAlpha = (storeHoldExitTimer / STORE_HOLD_EXIT_DURATION).coerceIn(0f, 1f)
            if (storeHoldExitTimer <= 0f) {
                state.storeHoldExitIndex = -1
            }
        }

        // Update TB-26 bartender movement and beer sliding on bar page
        updateTb26Bar(deltaTime)

        // Update chat system
        chatSystem.update(deltaTime, state)
    }

    private fun updateLaunching(deltaTime: Float) {
        state.launchProgress += deltaTime / 2f  // 2 seconds total

        when {
            state.launchProgress < 0.20f -> state.launchPhase = 0   // Pilot boards (0-0.8s)
            state.launchProgress < 0.375f -> state.launchPhase = 1  // Engine charge (0.8-1.5s)
            state.launchProgress < 0.55f -> state.launchPhase = 2   // Liftoff (1.5-2.2s)
            else -> state.launchPhase = 3                           // Hyperspace (2.2-4.0s)
        }

        // Trigger game launch
        if (state.launchProgress >= 1f) {
            val ship = state.getSelectedShip()
            val pilot = state.getSelectedPilot()
            if (ship != null && pilot != null) {
                state.saveSelection()
                onLaunch(ship.id, pilot.id)
            }
        }
    }

    private fun updateTb26Bar(deltaTime: Float) {
        // Only update on bar page
        if (state.currentPage != 0) return

        // TB-26 not present in corruption state — no pacing, no beers
        if (StoryStateManager.isCorrupted(persistence)) return

        // Counter and beer band are room-local (BarPageRenderer's barLeft = 10f,
        // barRight = roomWidth - 10f), so pacing and beer targets must be too or TB-26 paces
        // past the counter and beers slide into the neighbouring room on wide screens.
        val rw = HangarMetrics.effectiveRoomWidth(state.roomWidth, screenWidth)

        val counterLeft = 30f
        val counterRight = rw - 30f

        // TB-26 pacing
        if (state.tb26BarMoving) {
            val dx = state.tb26BarTargetX - state.tb26BarX
            if (kotlin.math.abs(dx) < 3f) {
                state.tb26BarX = state.tb26BarTargetX
                state.tb26BarMoving = false
                state.tb26BarPauseTimer = 1f + kotlin.random.Random.nextFloat() * 1.5f
            } else {
                state.tb26BarX += kotlin.math.sign(dx) * 40f * deltaTime
            }
        } else {
            state.tb26BarPauseTimer -= deltaTime
            if (state.tb26BarPauseTimer <= 0f) {
                state.tb26BarTargetX = counterLeft + kotlin.random.Random.nextFloat() * (counterRight - counterLeft)
                state.tb26BarMoving = true
            }
        }

        // Beer timer
        state.beerTimer += deltaTime
        if (state.beerTimer >= state.beerInterval && !state.beerActive) {
            val walkers = state.npcWalkers
            if (walkers.isNotEmpty()) {
                val target = walkers[kotlin.random.Random.nextInt(walkers.size)]
                state.beerTargetPilotIndex = target.pilotIndex
                state.beerX = state.tb26BarX
                val margin = rw * 0.1f
                val walkableWidth = rw - 2 * margin
                state.beerTargetX = margin + target.x * walkableWidth
                state.beerFading = false
                state.beerFadeAlpha = 1f
                state.beerActive = true
                state.tb26BarMoving = false
                state.tb26BarPauseTimer = 0.25f
                state.beerTimer = 0f
                state.beerInterval = 5f + kotlin.random.Random.nextFloat() * 2.5f
            }
        }

        // Beer movement
        if (state.beerActive) {
            if (state.beerFading) {
                state.beerFadeAlpha -= deltaTime * 4f
                if (state.beerFadeAlpha <= 0f) {
                    state.beerActive = false
                    state.beerFading = false
                }
            } else {
                val dx = state.beerTargetX - state.beerX
                if (kotlin.math.abs(dx) < 5f) {
                    val margin = rw * 0.1f
                    val walkableWidth = rw - 2 * margin
                    val targetWalker = state.npcWalkers.find { it.pilotIndex == state.beerTargetPilotIndex }
                    if (targetWalker != null) {
                        val walkerScreenX = margin + targetWalker.x * walkableWidth
                        if (kotlin.math.abs(walkerScreenX - state.beerTargetX) < 20f) {
                            // Beer grabbed!
                            state.beerActive = false
                            targetWalker.armRaiseTimer = 0.5f
                        } else {
                            // Walker moved away — unclaimed
                            state.beerFading = true
                        }
                    } else {
                        // Target walker gone — unclaimed
                        state.beerFading = true
                    }
                } else {
                    state.beerX += kotlin.math.sign(dx) * 300f * deltaTime
                }
            }
        }

        // Arm raise tick
        for (npc in state.npcWalkers) {
            if (npc.armRaiseTimer > 0f) {
                npc.armRaiseTimer = (npc.armRaiseTimer - deltaTime).coerceAtLeast(0f)
            }
        }
    }

    private fun render() {
        val canvas: Canvas? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { holder.lockHardwareCanvas() } catch (e: Exception) { holder.lockCanvas() }
        } else {
            holder.lockCanvas()
        }
        canvas ?: return
        try {
            canvas.save()
            canvas.scale(renderScale, renderScale)
            renderer.render(canvas, state)
        } finally {
            canvas.restore()
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                // Surface was destroyed mid-frame
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ex = event.x / renderScale
        val ey = event.y / renderScale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = ex
                touchStartY = ey
                lastTouchX = ex
                lastTouchY = ey
                lastTouchTime = event.eventTime
                isDragging = false
                gestureAbandoned = false
                activeSwipe = SwipeTarget.NONE
                shipDragPossible = false

                if (state.phase == HangarPhase.BROWSING) {
                    // Block interaction during crystal reveal animation
                    if (state.crystalRevealPhase == HangarState.CrystalRevealPhase.ORB_TRAVEL
                        || state.crystalRevealPhase == HangarState.CrystalRevealPhase.FLASH) {
                        return true
                    }
                    // Any touch below walkway on shipyard page can drag the ship
                    val walkwayY = screenHeight * 0.60f
                    if (state.currentPage == 1 && ey > walkwayY &&
                        state.isShipUnlocked(state.selectedShipIndex)) {
                        shipDragPossible = true
                    }
                    state.pageVelocity = 0f
                    state.pageScrollOffset = 0f
                    // Check if spin button is being held. Must resolve against the rest position
                    // (pageScrollOffset == 0), same as the release path in handleStoreTap: its own
                    // reset above the switch statement already zeroes pageScrollOffset before it
                    // calls roomX(), so DOWN and UP have to agree on the same rest-position offset
                    // or a press during a post-swipe settle can hold against one room-local X and
                    // release against another.
                    if (state.currentPage == 2 && renderer.spinButtonRect.contains(roomX(ex), ey)) {
                        spinButtonHeld = true
                        // Press only. Auto-spin re-enters handleSlotSpin from updateBrowsing while
                        // the finger stays down, and a tap per spin would turn a held button into a
                        // continuous rattle.
                        buttonTap()
                    }
                    // Store tiles: the press starts a hold. Resolve against the rest position for
                    // the same reason the spin button does — DOWN and UP must agree on the offset
                    // or a press during a post-swipe settle hits a different tile than it releases
                    // on. Release under the threshold falls through to handleStoreTap as a tap.
                    if (state.currentPage == 2) {
                        val rx = roomX(ex)
                        for ((index, rect) in renderer.upgradeRects.withIndex()) {
                            if (rect.contains(rx, ey)) {
                                // Maxed and unaffordable tiles start no fill at all — the press
                                // falls through to a plain tap on release instead, which flips
                                // the card, and the card is what explains the cost or the cap.
                                if (canStartHold(index)) {
                                    storeHold.start(index)
                                    heldUpgradeIndex = index
                                }
                                break
                            }
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state.phase == HangarPhase.BROWSING) {
                    if (!isDragging) {
                        val totalDx = abs(ex - touchStartX)
                        val totalDy = abs(ey - touchStartY)

                        // Slop comes from the platform so it scales with the screen — see
                        // HangarGestures for why the old raw 15f was the wrong shape entirely.
                        val shipDrag = HangarGestures.startsShipDrag(
                            totalDx, totalDy, swipeSlop, shipDragPossible)
                        val pageSwipe = HangarGestures.startsPageSwipe(totalDx, totalDy, swipeSlop)

                        if (shipDrag || pageSwipe) {
                            // A swipe is not a hold. Note this is now reached only by a gesture the
                            // page can actually perform: vertical drift on the store page commits
                            // to nothing and leaves the fill running.
                            cancelHold()
                            isDragging = true
                            activeSwipe = if (shipDrag) SwipeTarget.SHIP_DRAG else SwipeTarget.PAGE
                            if (shipDrag) state.isDraggingShip = true
                        } else if (storeHold.isActive) {
                            // Drift within the tile is free; leaving it is letting go of the
                            // button, which is what every platform control does.
                            val rect = renderer.upgradeRects.getOrNull(storeHold.index)
                            if (rect != null && !HangarGestures.holdSurvivesDrift(
                                    roomX(ex), ey, rect.left, rect.top, rect.right, rect.bottom)) {
                                cancelHold()
                            }
                        }
                    }

                    if (isDragging) {
                        val dx = ex - lastTouchX
                        val dy = ey - lastTouchY

                        when (activeSwipe) {
                            SwipeTarget.PAGE -> {
                                state.pageScrollOffset -= dx

                                // Add resistance at edges (page 0 left edge, page 2 right edge).
                                // During the intro cinematic the launchpad (page 1) is locked both
                                // ways — you may only arrive there from the bar.
                                if ((state.currentPage == 0 && state.pageScrollOffset < 0) ||
                                    (state.currentPage == 2 && state.pageScrollOffset > 0) ||
                                    (state.introCinematic && state.currentPage == 1)) {
                                    state.pageScrollOffset *= 0.3f
                                }

                                val touchDt = (event.eventTime - lastTouchTime).coerceAtLeast(1L).toFloat() / 1000f
                                state.pageVelocity = -dx / touchDt
                                lastTouchTime = event.eventTime
                            }
                            SwipeTarget.SHIP_DRAG -> {
                                state.shipDragY += dy
                                // Clamp: ship stops exactly at halo center, can't go above
                                val targetZoneY = screenHeight / 2f
                                state.shipDragY = state.shipDragY.coerceIn(
                                    targetZoneY,
                                    state.shipRestingY + 20f
                                )
                            }
                            else -> {}
                        }
                    }
                }

                lastTouchX = ex
                lastTouchY = ey
                return true
            }
            MotionEvent.ACTION_UP -> {
                // A press that outlived the tap window was an upgrade being started, not a tap.
                // Releasing it abandons the purchase and must NOT fall through to a flip: treating
                // the two as one event is exactly what made holding-then-letting-go still turn the
                // card over. The tail below cancels the hold, which arms the fill's fade, so the
                // player sees the attempt end rather than nothing happening.
                val abandonedPurchase = storeHold.isActive && !HoldToBuy.isTap(storeHold.heldSeconds)

                // gestureAbandoned, not just isDragging: an abandoned gesture has already cleared
                // isDragging, and without this it would be dispatched below as a tap.
                if (!isDragging && !gestureAbandoned && !abandonedPurchase) {
                    // Reset scroll offsets to prevent jitter on tap
                    if (shipDragPossible) {
                        // Was a tap on ship, not a drag — don't reset page scroll
                    } else {
                        state.pageScrollOffset = 0f
                        state.pageVelocity = 0f
                    }
                    handleTap(ex, ey)
                } else if (activeSwipe == SwipeTarget.PAGE) {
                    val stride = HangarMetrics.effectiveRoomWidth(roomWidth, screenWidth)
                    val shouldSwitch = abs(state.pageVelocity) > pageVelocityThreshold ||
                            abs(state.pageScrollOffset) > stride * 0.25f

                    val oldPage = state.currentPage
                    if (shouldSwitch) {
                        // Intro cinematic only permits the single forward hop bar(0) → launchpad(1).
                        val cinematicLocked = state.introCinematic
                        if (state.pageScrollOffset > 0 && state.currentPage < 2 &&
                            !(cinematicLocked && state.currentPage >= 1)) {
                            state.currentPage++
                            state.pageScrollOffset -= stride
                        } else if (state.pageScrollOffset < 0 && state.currentPage > 0 && !cinematicLocked) {
                            state.currentPage--
                            state.pageScrollOffset += stride
                        }
                    }
                    // Play swipe sound and crossfade ambient on page change.
                    if (state.currentPage != oldPage) {
                        if (state.introCinematic) {
                            // The one allowed swipe (bar → launchpad): swell rises while drone fades out.
                            SoundManager.playIntroSwell(context)
                            SoundManager.stopAmbient(fadeOutMillis = 1200)
                        } else {
                            SoundManager.playSFX("sfx_ui_swipe")
                            SoundManager.playAmbient(getAmbientForPage(state.currentPage))
                        }
                    }
                    // Pilot walks to new page target (no teleporting)
                    state.setPageTarget(state.currentPage)
                    state.pageVelocity = 0f
                } else if (activeSwipe == SwipeTarget.SHIP_DRAG) {
                    // Check if ship is in the launch zone
                    val targetZoneY = screenHeight / 2f
                    val inZone = abs(state.shipDragY - targetZoneY) < 45f

                    stopHaloRumble()
                    if (inZone && state.isReadyToLaunch() && !state.pilotWalking) {
                        // Snap ship to center and launch
                        state.shipDragY = targetZoneY
                        state.isDraggingShip = false
                        state.phase = HangarPhase.LAUNCHING
                        state.launchProgress = 0f
                        state.launchPhase = 0
                        SoundManager.playSFX("sfx_launch", 0.25f)
                        SoundManager.startCombatMusicEarly(context)
                        // First launch ever: the intro cinematic is over for good.
                        if (state.introCinematic) {
                            persistence.setIntroDone()
                            state.introCinematic = false
                        }
                    } else {
                        // Release → snap back to resting position
                        state.isDraggingShip = false
                    }
                }

                isDragging = false
                gestureAbandoned = false
                activeSwipe = SwipeTarget.NONE
                shipDragPossible = false
                spinButtonHeld = false
                cancelHold()
                // Safety net: the tile-match branch in handleStoreTap already consumes this on a
                // matching release, but a release that lands off every rect (or a drag that
                // starts only after a hold completed) would otherwise leave it set for a later,
                // unrelated tap on the same tile to consume instead.
                suppressFlipIndex = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                // The OS can deliver CANCEL instead of UP mid-hold (a parent intercepting the
                // gesture, e.g.). Mirrors the ACTION_UP tail's resets, minus the tap dispatch —
                // a cancel must never buy or flip anything — plus the ACTION_UP SHIP_DRAG release
                // branch's stopHaloRumble()/isDraggingShip reset just above, which this needs too:
                // a cancel mid ship-drag is a release that skips the launch check, not a no-op.
                // Without both halves, the snap-back animation (gated on !isDraggingShip) never
                // fires, the ship hangs in mid-air, and the halo check keeps re-arming the rumble
                // — the phone vibrates continuously until the player drags the ship again.
                abandonGesture()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // A second finger touching down anywhere mid-gesture, then the first lifting,
                // delivers POINTER_UP rather than UP or CANCEL. This view tracks one logical
                // touch, so — same as CANCEL — treat any additional-finger transition as an
                // abandon rather than let a hold's clock keep ticking with only a phantom finger
                // on the tile it started on, which could otherwise complete a purchase (or a ship
                // launch) the player never asked for.
                abandonGesture()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Reset every gesture-tracking field with no purchase, no launch, no flip — shared by
     * ACTION_CANCEL and ACTION_POINTER_UP, both of which must abandon whatever is in flight
     * rather than complete it. Mirrors the ACTION_UP tail's resets, plus the ACTION_UP SHIP_DRAG
     * release branch's stopHaloRumble()/isDraggingShip reset, minus the tap dispatch and the
     * launch check — an abandoned gesture must never buy, launch, or flip anything.
     */
    private fun abandonGesture() {
        cancelHold()
        suppressFlipIndex = -1
        gestureAbandoned = true
        isDragging = false
        activeSwipe = SwipeTarget.NONE
        shipDragPossible = false
        spinButtonHeld = false
        stopHaloRumble()
        stopHoldRumble()
        state.isDraggingShip = false
    }

    /**
     * Abandon the in-flight hold with no purchase. Every release path — a plain release, a
     * drag past slop, [abandonGesture], and the surface being destroyed — funnels through here
     * so the fill's exit fade starts from whatever width it had actually reached, rather than
     * each call site reimplementing "was there even a hold to cancel".
     */
    private fun cancelHold() {
        if (storeHold.isActive) {
            beginHoldExit(storeHold.index, storeHold.progress, success = false)
        }
        storeHold.cancel()
        heldUpgradeIndex = -1
    }

    /**
     * Whether tile [index] can still be bought — a maxed or unaffordable tile starts no fill.
     * The designed behaviour: maxed, unaffordable and the NG+ tile start no fill at all, and
     * tapping still flips them — a press that cannot buy falls through to a plain tap. The NG+
     * tile never reaches this — it is tracked separately as `crystalTileRect` and is never in
     * `renderer.upgradeRects` — so this only needs the same guards `handleUpgradeTap` applies at
     * purchase time, checked here too so a hold never starts on a tile it cannot complete.
     */
    private fun canStartHold(index: Int): Boolean {
        val ids = StoreUpgradeDefinitions.purchasableIds
        if (index !in ids.indices) return false
        val currentLevel = persistence.getUpgradeLevel(ids[index])
        if (currentLevel >= 5) return false
        return state.actualYen >= PersistenceManager.getUpgradeCost(currentLevel)
    }

    /**
     * Start the hold-fill's exit, so it is never seen to vanish. [progress] is the
     * width the fade holds through its decay: 1f (full) on a completed purchase, whatever the
     * fill had actually reached on an early release or a cancel. [success] tints the exit as a
     * brief completion flash rather than a plain fade. A no-op below zero width — nothing was
     * ever visible, so there is nothing to fade.
     */
    private fun beginHoldExit(index: Int, progress: Float, success: Boolean) {
        if (index < 0 || progress <= 0f) return
        state.storeHoldExitIndex = index
        state.storeHoldExitProgress = progress
        state.storeHoldExitAlpha = 1f
        state.storeHoldExitSuccess = success
        storeHoldExitTimer = STORE_HOLD_EXIT_DURATION
    }

    /**
     * Touch X → the current room's local X.
     *
     * The bar and shop pages draw inside `canvas.translate(-xOffset, 0f)` and publish their tap
     * rects (codex book, upgrade tiles, spin button, hatch, paper, mute toggles) in that
     * room-local space, so every hit test against one of them goes through here. Below the gate
     * the room origin is 0 and the page is at rest whenever a tap is dispatched, so this is the
     * identity — phone hit testing is bit-for-bit unchanged.
     *
     * The shipyard page deliberately does NOT use this: its hit tests are all measured from
     * screenWidth / 2, and the room block is centred on the screen, so the room's centre and the
     * screen's centre are the same point. Converting there would be a no-op on both sides.
     */
    private fun roomX(screenX: Float): Float =
        HangarMetrics.toRoomX(screenX, roomWidth, screenWidth, state.pageScrollOffset)

    private fun handleTap(x: Float, y: Float) {
        when (state.phase) {
            HangarPhase.BROWSING -> {
                // Check nav label taps: [CREW] [LAUNCH] [SHOP] at bottom
                val labelY = screenHeight * 0.95f
                // Nav labels are hidden during the intro cinematic — ignore their tap zone.
                if (!state.introCinematic && y > labelY - 30f && y < labelY + 15f) {
                    val centerX = screenWidth / 2f
                    // Must match HangarRenderer.drawPageIndicator's content-anchored spacing,
                    // or the side labels' tap zones drift off the drawn labels on wide screens.
                    val spacing = layout.content.width * 0.25f
                    for (i in 0..2) {
                        val labelCenterX = centerX + (i - 1) * spacing
                        if (abs(x - labelCenterX) < spacing * 0.4f && i != state.currentPage) {
                            navigateToPage(i)
                            return
                        }
                    }
                }
                when (state.currentPage) {
                    0 -> handleBarTap(x, y)
                    1 -> handleShipyardTap(x, y)
                    2 -> handleStoreTap(x, y)
                }
            }
            HangarPhase.CODEX -> {
                // Tap anywhere closes codex
                state.phase = HangarPhase.BROWSING
            }
            else -> {}
        }
    }

    private fun navigateToPage(targetPage: Int) {
        val oldPage = state.currentPage
        state.pageScrollOffset = (oldPage - targetPage) *
            HangarMetrics.effectiveRoomWidth(roomWidth, screenWidth)
        state.currentPage = targetPage
        state.pageVelocity = 0f
        SoundManager.playSFX("sfx_ui_swipe")
        SoundManager.playAmbient(getAmbientForPage(targetPage))
        state.setPageTarget(targetPage)
    }

    internal fun handleBarTap(x: Float, y: Float) {
        SoundManager.playSFX("sfx_ui_tap")

        // The bar page draws — and publishes its rects — in room-local space.
        val rx = roomX(x)

        // The codex book on the counter is set dressing only. The maintenance hatch on the slot
        // machine is the one way in (owner, 2026-08-10) — it is the secret the bar's own hints
        // point at, and a second door on the counter gave it away.

        val pilotIndex = renderer.getPilotGridIndex(rx, y)
        if (pilotIndex != null) {
            val pilot = PilotDefinitions.getPilotByIndex(pilotIndex)
            if (pilot != null && state.isPilotUnlocked(pilotIndex)) {
                if (pilotIndex == state.selectedPilotIndex) {
                    // Already selected — turn the card over to show the passive, or turn it back
                    // if it is already over. A second tap means "put it back", same as the store.
                    state.togglePilotFlip(pilotIndex)
                } else {
                    selectPilotAndStartWalk(pilotIndex)
                }
            }
        }
    }

    private fun selectPilotAndStartWalk(pilotIndex: Int) {
        val oldSelectedIndex = state.selectedPilotIndex
        // Must land in the same room-local band as HangarState.getPilotWorldTarget's page-0
        // target, or the pilot walker jumps to a screen-width position outside the bar's stride
        // on wide screens.
        val rw = HangarMetrics.effectiveRoomWidth(roomWidth, screenWidth)
        val margin = rw * 0.1f
        val walkable = rw * 0.8f
        val npcWalker = state.npcWalkers.find { it.pilotIndex == pilotIndex }
        if (npcWalker != null) {
            state.pilotX = margin + npcWalker.x * walkable
        } else {
            state.pilotX = state.getPilotWorldTarget(state.currentPage)
        }
        state.pilotTargetX = state.getPilotWorldTarget(state.currentPage)
        state.pilotWalking = state.pilotX != state.pilotTargetX
        state.selectedPilotIndex = pilotIndex
        if (oldSelectedIndex != pilotIndex) {
            val oldPilot = PilotDefinitions.getPilotByIndex(oldSelectedIndex)
            if (oldPilot != null && state.isPilotUnlocked(oldSelectedIndex)) {
                state.pendingNPCAdds.add(WalkerNPC(
                    pilotIndex = oldSelectedIndex,
                    color = oldPilot.color,
                    x = 0.9f,
                    targetX = kotlin.random.Random.nextFloat() * 0.8f + 0.1f,
                    walking = true,
                    idleTimer = 0f
                ))
            }
            state.pendingNPCRemoves.add(pilotIndex)
        }
    }

    private fun handleShipyardTap(x: Float, y: Float) {
        SoundManager.playSFX("sfx_ui_tap")

        // Ship taps — tap on peek ships to switch, tap on selected to purchase
        val shipY = state.shipRestingY
        val shipHitSize = 70f
        if (y > shipY - shipHitSize && y < shipY + shipHitSize) {
            val centerX = screenWidth / 2
            val spacing = renderer.shipSpacing

            // Build visible ship list — mirrors HangarRenderer; corruption = no dead ships
            val isCorrupted = StoryStateManager.isCorrupted(persistence)
            val visibleShips = (0 until ShipDefinitions.getShipCount()).filter { i ->
                val s = ShipDefinitions.getShipByIndex(i) ?: return@filter false
                if (isCorrupted && StoryStateManager.isShipDead(persistence, s.id)) return@filter false
                // Intro cinematic: only Scout is visible, so it's the only tappable ship.
                if (state.introCinematic && i != state.selectedShipIndex) return@filter false
                true
            }
            val currentPos = visibleShips.indexOf(state.selectedShipIndex)
                .let { if (it < 0) 0 else it }

            // Tap left/right peek ship to swap. Both get the button tap: moving the carousel is a
            // control answering a press, same as the spin or audio buttons, and it feels the same
            // whether the ship you land on is owned or still locked — the swap happened either
            // way. Fired inside the guards, so a tap at either end of the list, where nothing
            // moves, stays silent.
            if (x < centerX - shipHitSize && currentPos > 0) {
                state.shipScrollOffset = -spacing
                state.selectedShipIndex = visibleShips[currentPos - 1]
                buttonTap()
                return
            }
            if (x > centerX + shipHitSize && currentPos < visibleShips.size - 1) {
                state.shipScrollOffset = spacing
                state.selectedShipIndex = visibleShips[currentPos + 1]
                buttonTap()
                return
            }
            // Tap center ship (purchase)
            if (x > centerX - shipHitSize && x < centerX + shipHitSize) {
                val ship = state.getSelectedShip()
                if (ship != null && !state.isShipUnlocked(state.selectedShipIndex)) {
                    if (state.canUnlockShip(state.selectedShipIndex) &&
                        state.actualYen >= ship.cost) {
                        persistence.addYen(-ship.cost)
                        persistence.unlockShip(ship.id)
                        state.actualYen = persistence.getYen()
                        telemetryManager.logPurchase("ship_purchase", ship.id, 0, ship.cost, state.actualYen)
                        SoundManager.playSFX("sfx_ui_purchase")
                        // The same knock a held upgrade gives. The gesture differs — the shipyard
                        // buys on a tap — but what happened to the wallet does not.
                        purchasePulse()
                    }
                }
                return
            }
        }
    }

    // internal: see the comment on `state`/`renderer` above — this is the test seam for the
    // suppression decision StoreHoldSuppressesFlipTest exercises.
    internal fun handleStoreTap(x: Float, y: Float) {
        // The shop page draws — and publishes its rects — in room-local space.
        val rx = roomX(x)

        // Mute toggle buttons (checked before generic tap sound)
        val audioRect = state.audioMuteButtonRect
        if (audioRect != null && audioRect.contains(rx, y)) {
            // One button, four states: all → none → combat muted → music muted → all.
            state.audioMode = state.audioMode.next()
            persistence.setAudioMode(state.audioMode)
            SoundManager.applyAudioMode(state.audioMode)
            state.showReadoutMessage(state.audioMode.readoutLabel)
            // The confirmation tap is an effect like any other, so it simply goes quiet in the
            // states that silence effects. No guard needed: silence confirms itself by being silent.
            SoundManager.playSFX("sfx_ui_tap")
            // The haptic is the reason this button still answers in the states that silence it.
            buttonTap()
            return
        }

        val vibRect = state.vibrationMuteButtonRect
        if (vibRect != null && vibRect.contains(rx, y)) {
            state.vibrationMuted = !state.vibrationMuted
            persistence.setVibrationMuted(state.vibrationMuted)
            isVibrationMuted = state.vibrationMuted
            state.showReadoutMessage(if (state.vibrationMuted) "VIBRATE OFF" else "VIBRATE ON")
            SoundManager.playSFX("sfx_ui_tap")
            // Only on the way back on. Turning vibration off and then buzzing to confirm it would
            // be the button disobeying itself; `buttonTap` would no-op anyway, since
            // isVibrationMuted is already updated above, but the intent is worth being explicit
            // about. This is the one button whose haptic is conditional.
            if (!state.vibrationMuted) buttonTap()
            return
        }

        SoundManager.playSFX("sfx_ui_tap")

        // Maintenance hatch tap (codex secret)
        val hatchRect = state.hatchRect
        if (hatchRect != null && hatchRect.contains(rx, y) && !state.hatchOpen) {
            state.hatchTapCount++
            if (state.hatchTapCount >= 5) {
                state.hatchOpen = true
                if (!state.codexDiscovered) {
                    state.codexDiscovered = true
                    persistence.setCodexDiscovered()
                }
            }
            return
        }

        // Paper tap — open codex
        val paperRect = state.paperRect
        if (state.hatchOpen && paperRect != null && paperRect.contains(rx, y)) {
            state.phase = HangarPhase.CODEX
            return
        }

        for ((index, rect) in renderer.upgradeRects.withIndex()) {
            if (rect.contains(rx, y)) {
                // Tap reads, hold buys — but the finger is still down when a hold completes, so
                // the release that follows lands here on the very tile that was just bought.
                // suppressFlipIndex is consumed on read so it can't leak into a later, genuine
                // tap on the same tile. The unconditional sfx_ui_tap near the top of this
                // function already covers this call; no second play here.
                if (suppressFlipIndex == index) {
                    suppressFlipIndex = -1
                    return
                }
                state.toggleStoreCard(index, STORE_FLIP_DURATION)
                return
            }
        }

        // Tile 9 is deliberately absent from upgradeRects — it is tracked separately and cannot be
        // bought in any of its four states, which is what makes "not purchasable" structural rather
        // than a guard someone can delete. Only its two live faces turn over; both "?" states do
        // nothing, because the source requires the two mystery branches stay identical.
        // No second sfx_ui_tap here — same as the upgradeRects loop above, the unconditional play
        // near the top of this function already covers every tap that reaches this point.
        val crystalRect = renderer.crystalTileRect
        if (crystalRect.contains(rx, y) && renderer.isCrystalTileRevealed(persistence, state)) {
            state.toggleStoreCard(CRYSTAL_TILE_INDEX, STORE_FLIP_DURATION)
            return
        }

        // Slot machine spin button
        if (renderer.spinButtonRect.contains(rx, y)) {
            handleSlotSpin()
            return
        }
    }

    /**
     * The reels have all stopped: reveal the result and pay it out.
     *
     * internal: the test seam for the deferred payout. Everything the player is owed by a spin
     * lands here rather than at roll time, so the reels are never showing one thing while the
     * save already holds another.
     */
    internal fun completeSpin(now: Long) {
        state.isSpinning = false
        state.spinResultTime = now
        val isJackpot = state.reelValues[0] == StorePageRenderer.SYM_ROCKET &&
            state.reelValues[1] == StorePageRenderer.SYM_ROCKET &&
            state.reelValues[2] == StorePageRenderer.SYM_ROCKET
        if (isJackpot) {
            SoundManager.playSFX("sfx_slot_jackpot")
            spinButtonHeld = false  // Let jackpot animation play before resuming auto-spin
        }
        // The free upgrade the jackpot promised at roll time, handed over now the reels have
        // shown it. Read-to-clear so a later spin can never re-grant it.
        state.pendingSpinUpgradeId?.let { id ->
            state.pendingSpinUpgradeId = null
            synchronized(upgradeLock) {
                persistence.setUpgradeLevel(id, persistence.getUpgradeLevel(id) + 1)
            }
        }
        if (state.spinResultYen > 0) {
            if (!isJackpot) SoundManager.playSFX("sfx_slot_win")
            synchronized(upgradeLock) {
                val newYen = state.actualYen + state.spinResultYen
                persistence.setYen(newYen)
                state.actualYen = newYen
            }
        }
    }

    /**
     * @param roll the outcome draw. internal with a default so a test can force a jackpot
     *   without spinning until one turns up.
     */
    internal fun handleSlotSpin(roll: Float = kotlin.random.Random.nextFloat()) {
        if (state.isSpinning) return
        if (state.actualYen < 100) return

        synchronized(upgradeLock) {
            if (state.actualYen < 100) return
            // Deduct cost immediately
            val newYen = state.actualYen - 100
            persistence.setYen(newYen)
            state.actualYen = newYen
        }

        var outcome: Triple<Int, Int, String?> = Triple(-1, 0, null) // default: loss
        val rascalRigged = StoryStateManager.hasLoopedBefore(persistence)
                && persistence.isPilotUnlocked("pilot_rascal")
                && !StoryStateManager.isAstroLoop(persistence)  // Astro Loop is never rigged
        val isCorrupted = StoryStateManager.isCorrupted(persistence)
        // Note: isCorrupted branches below are dead — the if (!isCorrupted) guard above skips
        // the entire when block in corruption runs (outcome stays at the default loss Triple).
        // Thresholds kept here so the non-corruption paths remain readable in one place.
        val jackpotThreshold = when {
            isCorrupted -> 0.005f
            rascalRigged -> 0.15f
            state.isWhiskersJackpotEligible() -> 0.10f
            else -> 0.005f
        }
        val diamondThreshold = when {
            isCorrupted -> 0.02f
            rascalRigged -> 0.15f
            else -> 0.02f
        }
        if (!isCorrupted) when {
            roll < jackpotThreshold -> {
                // Jackpot — free random upgrade or 10k yen
                val chosen = chooseRandomUpgrade()
                if (chosen != null) {
                    state.pendingSpinUpgradeId = chosen.first
                    outcome = Triple(StorePageRenderer.SYM_ROCKET, 0, chosen.second)
                } else {
                    outcome = Triple(StorePageRenderer.SYM_ROCKET, 10000, null)
                }
                // Recruit Whiskers if eligible
                if (state.isWhiskersJackpotEligible()) {
                    val pilot = state.recruitNextPilot()
                    if (pilot != null) {
                        telemetryManager.logPurchase("pilot_jackpot", pilot.id, 0, 0, persistence.getYen())
                        val pilotIndex = PilotDefinitions.pilots.indexOf(pilot)
                        val npcRandom = kotlin.random.Random(System.currentTimeMillis())
                        state.pendingNPCAdds.add(WalkerNPC(
                            pilotIndex = pilotIndex,
                            color = pilot.color,
                            x = npcRandom.nextFloat() * 0.8f + 0.1f,
                            targetX = npcRandom.nextFloat() * 0.8f + 0.1f,
                            walking = false,
                            idleTimer = 1f
                        ))
                        chatSystem.onPilotRecruited(state, pilot.callsign)
                        SoundManager.playSFX("sfx_pilot_recruit", 0.25f)
                    }
                }
            }
            roll < jackpotThreshold + diamondThreshold -> outcome = Triple(StorePageRenderer.SYM_DIAMOND, 2000, null)
            roll < jackpotThreshold + diamondThreshold + 0.06f -> outcome = Triple(StorePageRenderer.SYM_STAR, 700, null)
            roll < jackpotThreshold + diamondThreshold + 0.12f -> outcome = Triple(StorePageRenderer.SYM_YEN, 100, null)
            roll < jackpotThreshold + diamondThreshold + 0.20f -> outcome = Triple(StorePageRenderer.SYM_BOLT, 75, null)
            roll < jackpotThreshold + diamondThreshold + 0.30f -> outcome = Triple(StorePageRenderer.SYM_WRENCH, 50, null)
            else -> outcome = Triple(-1, 0, null)
        }

        val (symbol, yenPayout, upgradeName) = outcome

        // Set reel values — all three show the winning symbol (or mixed for loss)
        if (symbol == -1) {
            // Mixed — random non-matching symbols
            val rng = kotlin.random.Random
            state.reelValues[0] = rng.nextInt(StorePageRenderer.SYMBOL_COUNT)
            state.reelValues[1] = rng.nextInt(StorePageRenderer.SYMBOL_COUNT)
            do {
                state.reelValues[2] = rng.nextInt(StorePageRenderer.SYMBOL_COUNT)
            } while (state.reelValues[0] == state.reelValues[1] && state.reelValues[1] == state.reelValues[2])
        } else {
            state.reelValues[0] = symbol
            state.reelValues[1] = symbol
            state.reelValues[2] = symbol
        }

        // Stagger stop times: left first, then middle, then right
        val now = System.currentTimeMillis()
        state.reelStopTimes[0] = now + 800L
        state.reelStopTimes[1] = now + 1100L
        state.reelStopTimes[2] = now + 1400L

        state.spinResultYen = yenPayout
        state.spinResultUpgrade = upgradeName
        state.spinResultSymbol = symbol
        state.spinResultTime = 0  // Set when animation completes
        state.isSpinning = true
        SoundManager.playSFX("sfx_slot_spin")
        persistence.incrementCasinoSpins()

        val symbolNames = state.reelValues.map { StorePageRenderer.getSymbolName(it) }
        telemetryManager.logCasinoSpin(symbolNames, yenPayout, state.actualYen)
    }

    /**
     * Pick the upgrade a jackpot will hand over, or null if all eight are maxed.
     *
     * Chooses only — `completeSpin` does the granting. A jackpot that picked *and* wrote here
     * would raise the tile's level while the reels were still turning, which is the machine
     * answering before it has finished asking.
     */
    private fun chooseRandomUpgrade(): Pair<String, String>? {
        val nonMaxed = SLOT_UPGRADES.filter { persistence.getUpgradeLevel(it.first) < 5 }
        if (nonMaxed.isEmpty()) return null
        return nonMaxed[kotlin.random.Random.nextInt(nonMaxed.size)]
    }

    private val upgradeLock = Any()

    /**
     * Buy one level of [index], the completion of a hold.
     *
     * `handleUpgradeTap` keeps the money handling it always had, including the maxed and
     * unaffordable guards, so a completed hold on a tile that cannot be bought is silently free.
     *
     * internal: see the comment on `state`/`renderer` above — this is the test seam for the
     * suppression decision StoreHoldSuppressesFlipTest exercises.
     */
    internal fun purchaseHeldUpgrade(index: Int) {
        if (index < 0) return
        handleUpgradeTap(index)
        // The finger is still down here — the ACTION_UP that follows falls through to
        // handleStoreTap on this same tile, since the press never crossed the drag slop. Record
        // it unconditionally (even if handleUpgradeTap's guards silently declined the purchase):
        // a completed 0.5s hold is not a tap either way, so its release must not flip the tile.
        suppressFlipIndex = index
        heldUpgradeIndex = -1
    }

    private fun handleUpgradeTap(index: Int) {
        val upgradeIds = listOf("health", "shields", "speed", "damage", "crit", "magnet", "yen_bonus", "salvage")

        // Time Crystal — 9th tile (index 8), auto-equipped when unlocked — no purchase needed
        if (index == 8) return

        if (index >= upgradeIds.size) return

        synchronized(upgradeLock) {
            val id = upgradeIds[index]
            val currentLevel = persistence.getUpgradeLevel(id)
            if (currentLevel >= 5) return  // Already maxed

            val cost = PersistenceManager.getUpgradeCost(currentLevel)
            if (state.actualYen >= cost) {
                val newYen = state.actualYen - cost
                persistence.setYen(newYen)
                persistence.setUpgradeLevel(id, currentLevel + 1)
                state.actualYen = newYen
                telemetryManager.logPurchase("store_upgrade", id, currentLevel + 1, cost, newYen)
                SoundManager.playSFX("sfx_ui_purchase")
            }
        }
    }

    private fun getAmbientForPage(page: Int): String = "bgm_${SoundManager.activeSet}_hangar"

    private fun startHaloRumble() {
        if (isVibrationMuted) return
        if (isVibratingHalo) return
        isVibratingHalo = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 50, 50), intArrayOf(0, 40, 0), 0
            ))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 50), 0)
        }
    }

    private fun stopHaloRumble() {
        if (!isVibratingHalo) return
        isVibratingHalo = false
        vibrator.cancel()
    }

    /**
     * The hum under a filling store tile — deliberately fainter than the halo rumble.
     *
     * The halo announces a thing you have found; this one only confirms a thing you are already
     * watching happen, and it sits under a thumb resting on the tile for a full second. Amplitude
     * 18 against the halo's 40, and a slow 90/90 pulse rather than the halo's 50/50 flutter, so it
     * reads as the tile charging rather than as an alert.
     */
    private fun startHoldRumble() {
        if (isVibrationMuted) return
        if (isVibratingHold) return
        isVibratingHold = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, HAPTIC_HOLD_ON_MS, HAPTIC_HOLD_OFF_MS),
                intArrayOf(0, HAPTIC_HOLD_AMPLITUDE, 0), 0
            ))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, HAPTIC_HOLD_ON_MS, HAPTIC_HOLD_OFF_MS), 0)
        }
    }

    /**
     * A control acknowledging a press — the feel of a small physical button.
     *
     * Deliberately not wired to the pilot or store card flips: turning a card over is reading, not
     * acting on anything, and a buzz on every browse would wear the gesture out.
     */
    private fun buttonTap() {
        if (isVibrationMuted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_BUTTON_MS, HAPTIC_BUTTON_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(HAPTIC_BUTTON_MS)
        }
    }

    private fun stopHoldRumble() {
        if (!isVibratingHold) return
        isVibratingHold = false
        vibrator.cancel()
    }

    /**
     * Money left the wallet: one short, definite knock, clearly above the hum it replaces.
     *
     * Shared by upgrades and ships, so a spend feels the same wherever the player makes it — the
     * shipyard buys on a tap rather than a hold, but the thing that happened is identical.
     */
    private fun purchasePulse() {
        if (isVibrationMuted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_PURCHASE_MS, HAPTIC_PURCHASE_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(HAPTIC_PURCHASE_MS)
        }
    }

    private fun checkPilotRecruitment() {
        if (state.checkPilotUnlockCondition()) {
            val pilot = state.recruitNextPilot()
            if (pilot != null) {
                telemetryManager.logPurchase("pilot_unlock", pilot.id, 0, 0, persistence.getYen())
                val pilotIndex = PilotDefinitions.pilots.indexOf(pilot)
                val npcRandom = kotlin.random.Random(System.currentTimeMillis())
                state.pendingNPCAdds.add(WalkerNPC(
                    pilotIndex = pilotIndex,
                    color = pilot.color,
                    x = npcRandom.nextFloat() * 0.8f + 0.1f,
                    targetX = npcRandom.nextFloat() * 0.8f + 0.1f,
                    walking = false,
                    idleTimer = 1f
                ))
                chatSystem.onPilotRecruited(state, pilot.callsign)
                SoundManager.playSFX("sfx_pilot_recruit", 0.25f)
            }
        }
    }

    fun pause() {
        stopHaloRumble()
        stopHoldRumble()
        running = false
        gameThread?.join()
    }

    fun resume() {
        if (!running) {
            running = true
            gameThread = Thread(this)
            gameThread?.start()
        }
    }

    fun addYenFromRun(amount: Int) {
        // The payout is already banked — MainActivity credits it on the game thread before
        // handing back here, so a failure during the return cannot cost the player the run.
        // This only syncs the display, which is what drives the count-up: displayedYen still
        // holds the pre-run balance, and updateYenDisplay lerps it up to actualYen over 3s.
        state.actualYen = persistence.getYen()
        persistence.incrementRunsSincePilotUnlock()
        if (!StoryStateManager.isCorrupted(persistence)) chatSystem.resetUsedLines()
        val pilotId = persistence.getSelectedPilotId()
        if (persistence.isFreshLoopStart()) {
            persistence.clearFreshLoopStart()
            chatSystem.onFirstLaunch(state)
        } else {
            chatSystem.onDeathReturn(state, pilotId, amount)
        }

        // Corruption: check if all crew are dead and crystal should unlock
        if (StoryStateManager.isCorrupted(persistence)) {
            if (StoryStateManager.shouldUnlockCrystal(persistence)) {
                persistence.setCrystalUnlocked(true)
            }

            // Remove dead pilots from NPC walkers
            val deadPilots = persistence.getDeadPilots()
            if (deadPilots.isNotEmpty()) {
                for (i in 0 until PilotDefinitions.getPilotCount()) {
                    val pilot = PilotDefinitions.getPilotByIndex(i) ?: continue
                    if (deadPilots.contains(pilot.id)) {
                        state.pendingNPCRemoves.add(i)
                    }
                }
            }

            // Crystal reveal: set up pending reveal when crystal unlocked and all crew dead
            if (persistence.isCrystalUnlocked() && StoryStateManager.allCrewDead(persistence)) {
                if (!persistence.isAwaitingCrystalReveal()) {
                    // First time all crew dead + crystal unlocked — set up reveal
                    persistence.setAwaitingCrystalReveal(true)
                }
                // Don't auto-select Astro. initCorruptionState() will handle the rest.
                state.selectedPilotIndex = -1
                val specterIndex = ShipDefinitions.ships.indexOfFirst { it.id == "ship_white" }
                if (specterIndex >= 0) state.selectedShipIndex = specterIndex
            } else {
                // If currently selected pilot/ship is now dead, find first available
                if (!state.isPilotUnlocked(state.selectedPilotIndex)) {
                    val firstAvailable = (0 until PilotDefinitions.getPilotCount()).firstOrNull { state.isPilotUnlocked(it) }
                    if (firstAvailable != null) state.selectedPilotIndex = firstAvailable
                }
                if (!state.isShipUnlocked(state.selectedShipIndex)) {
                    val firstAvailable = (0 until ShipDefinitions.ships.size).firstOrNull { state.isShipUnlocked(it) }
                    if (firstAvailable != null) state.selectedShipIndex = firstAvailable
                }
            }
        }

        checkPilotRecruitment()
    }

    fun resetForReturn(fadeFromWhite: Boolean = false) {
        state.phase = HangarPhase.BROWSING
        state.launchProgress = 0f
        state.launchPhase = 0
        // Reset scroll states
        state.pageScrollOffset = 0f
        state.pageVelocity = 0f
        // Refresh the music set from current story stage
        SoundManager.activeSet = StoryStateManager.stageMusicSet(persistence)
        // Reset to bar page and start ambient
        state.currentPage = 0
        SoundManager.playAmbient(getAmbientForPage(0))
        val barTarget = state.getPilotWorldTarget(0)
        state.pilotX = barTarget
        state.pilotTargetX = barTarget
        state.pilotWalking = false

        // Reset slot machine result state
        state.spinResultYen = 0
        state.spinResultUpgrade = null
        state.pendingSpinUpgradeId = null
        state.spinResultSymbol = -1
        state.spinResultTime = 0

        // Reset ship drag
        state.isDraggingShip = false
        state.shipDragY = state.shipRestingY

        // Initialize corruption state if we're in the corruption phase
        // This rebuilds NPC walkers (filtering dead pilots) and auto-selects Astro+Specter
        if (StoryStateManager.isCorrupted(persistence)) {
            state.initCorruptionState(persistence)
            state.fadeFromBlackTimer = 3.0f
        } else if (StoryStateManager.isAstroLoop(persistence)) {
            if (persistence.isAstroLoopFirstEntry()) {
                // First entry: default to Astro + Specter
                val astroIndex = (0 until PilotDefinitions.getPilotCount()).firstOrNull { i ->
                    PilotDefinitions.getPilotByIndex(i)?.id == "pilot_astro"
                } ?: -1
                val specterIndex = (0 until ShipDefinitions.getShipCount()).firstOrNull { i ->
                    ShipDefinitions.getShipByIndex(i)?.id == "ship_white"
                } ?: -1
                if (astroIndex >= 0) state.selectedPilotIndex = astroIndex
                if (specterIndex >= 0) state.selectedShipIndex = specterIndex
            } else {
                // Later entries: restore last-flown selection
                val savedShipId = persistence.getSelectedShipId()
                val savedPilotId = persistence.getSelectedPilotId()
                state.selectedShipIndex = (0 until ShipDefinitions.getShipCount())
                    .firstOrNull { ShipDefinitions.getShipByIndex(it)?.id == savedShipId } ?: 0
                state.selectedPilotIndex = (0 until PilotDefinitions.getPilotCount())
                    .firstOrNull { PilotDefinitions.getPilotByIndex(it)?.id == savedPilotId } ?: 0
            }

            // Black fade-in whenever the game already faded itself to black before the
            // handoff (desert farewell on first entry, reckoning win). First entry
            // additionally fires TB's one-shot welcome.
            if (fadeFromWhite) {
                state.fadeFromBlackTimer = 2.0f
                if (persistence.isAstroLoopFirstEntry()) {
                    state.pendingTbWelcome = true
                    persistence.clearAstroLoopFirstEntry()
                }
            }

            // Rebuild the bar roster (mirrors the corruption branch above). Without this,
            // first entry inherits the empty end-of-corruption walker list and the revived
            // crew stays invisible until each pilot is cycled through the grid.
            state.rebuildNpcWalkers()
        } else {
            val savedShipId = persistence.getSelectedShipId()
            val savedPilotId = persistence.getSelectedPilotId()
            val shipIdx = (0 until ShipDefinitions.getShipCount())
                .firstOrNull { ShipDefinitions.getShipByIndex(it)?.id == savedShipId } ?: 0
            val pilotIdx = (0 until PilotDefinitions.getPilotCount())
                .firstOrNull { PilotDefinitions.getPilotByIndex(it)?.id == savedPilotId } ?: 0
            state.selectedShipIndex = shipIdx
            state.selectedPilotIndex = pilotIdx
            state.glitchTimer = 1.0f
        }
    }
}
