package com.astroloop.game

import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.astroloop.game.core.BrickScreenView
import com.astroloop.game.core.GameSurfaceView
import com.astroloop.game.core.SoundManager
import com.astroloop.game.core.StoryStateManager
import com.astroloop.game.data.PersistenceManager
import com.astroloop.game.hangar.HangarSurfaceView
import com.astroloop.game.render.FontManager

class MainActivity : ComponentActivity() {

    private var hangarView: HangarSurfaceView? = null
    private var gameView: GameSurfaceView? = null
    private var currentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Black background to prevent white flash on view transitions
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        // Load fonts BEFORE any view/renderer is constructed — renderer Paint
        // fields capture the typeface at construction time.
        FontManager.initialize(this)

        // Initialize sound system
        SoundManager.init(this)

        // Load persisted sound set before any ambient playback
        val persistence = com.astroloop.game.data.PersistenceManager(this)
        // Fold any legacy story flags into the new story_stage / story_loop keys
        // before anything reads them. Idempotent — runs at most once per save.
        persistence.migrateStoryState()
        // Backfill desert_good_ending on astro-loop saves that predate the good
        // ending writing it (it only ever entered the stage) — see healDesertGoodEnding.
        persistence.healDesertGoodEnding()
        SoundManager.activeSet = StoryStateManager.stageMusicSet(persistence)

        // Route to the appropriate screen based on persistence state
        when {
            persistence.isCrystalBroken() -> showBrickScreen()
            else -> showHangar()
        }

        // Fullscreen immersive mode (must be after setContentView)
        setupFullscreen()
    }

    private fun showHangar() {
        gameView?.pause()
        gameView = null

        hangarView = HangarSurfaceView(this) { shipId, pilotId ->
            runOnUiThread {
                launchGame(shipId, pilotId)
            }
        }
        setContentView(hangarView)
        wireInsets(hangarView!!)
        currentView = hangarView
        hangarView?.resume()
        // The first-launch intro cinematic opens in silence — no hangar BGM until the swell.
        if (com.astroloop.game.data.PersistenceManager(this).isIntroDone()) {
            SoundManager.playAmbient("bgm_${SoundManager.activeSet}_hangar")
        }
    }

    private fun showBrickScreen() {
        gameView?.pause()
        gameView = null
        hangarView?.pause()
        hangarView = null

        val brickView = BrickScreenView(this)
        setContentView(brickView)
        currentView = brickView
    }

    private fun launchGame(shipId: String, pilotId: String) {
        hangarView?.pause()

        gameView = GameSurfaceView(this, shipId, pilotId) { yenEarned, fadeFromWhite ->
            // Bank the payout HERE, before the hop to the UI thread.
            //
            // This runs on the game thread, inside GameThread's catch(Throwable), whereas
            // returnToHangar below runs on the UI thread with no such net — so anything that
            // throws while rebuilding the hangar used to cost the player the run's earnings
            // while still counting the run toward pilot unlocks. Two separate player reports
            // describe exactly that: one froze on the way back, one crashed on the death
            // cutscene, and both lost the money.
            //
            // Banking the amount passed rather than state.goldCollected is deliberate: five of
            // the eight onGameOver call sites pass 0 on purpose — the reckoning win, the
            // timeline shift and the debug exits grant nothing however much was collected.
            if (yenEarned > 0) PersistenceManager(this).addYen(yenEarned)
            runOnUiThread {
                returnToHangar(yenEarned, fadeFromWhite)
            }
        }
        setContentView(gameView)
        wireInsets(gameView!!)
        currentView = gameView
        gameView?.resume()
        SoundManager.startCombatMusic(this)
        setupFullscreen()
    }

    private fun returnToHangar(yenEarned: Int, fadeFromWhite: Boolean = false) {
        // Stop the producer first, and synchronously. pause() joins the game thread, so once this
        // returns no further frame can be posted for the outgoing surface.
        gameView?.pause()
        gameView = null

        // Then let the current frame retire before swapping. setContentView destroys the outgoing
        // SurfaceView's surface, and a draw still sitting in HWUI's queue for that surface aborts
        // the process on some drivers rather than failing softly: on a reported Samsung Android 9
        // stack it asserts in EglManager::damageFrame with EGL_BAD_ACCESS.
        //
        // decorView rather than the outgoing view: it stays attached, so the post is guaranteed to
        // run. Costs one frame on the way back to the bar.
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                swapToHangar(yenEarned, fadeFromWhite)
            }
        }
    }

    private fun swapToHangar(yenEarned: Int, fadeFromWhite: Boolean) {
        if (hangarView == null) {
            hangarView = HangarSurfaceView(this) { shipId, pilotId ->
                runOnUiThread {
                    launchGame(shipId, pilotId)
                }
            }
        }

        setContentView(hangarView)
        wireInsets(hangarView!!)
        currentView = hangarView
        // resetForReturn BEFORE resume — state.phase must be BROWSING before the thread starts,
        // otherwise the thread's first frame sees LAUNCHING with launchProgress>=1 and fires onLaunch again.
        // surfaceCreated() is deferred (fires after this method returns), and state.initialize()
        // is guarded by stateInitialized so it won't overwrite resetForReturn's work.
        hangarView?.addYenFromRun(yenEarned)
        hangarView?.resetForReturn(fadeFromWhite)
        hangarView?.resume()
        SoundManager.stopCombatMusic()
        SoundManager.playAmbient("bgm_${SoundManager.activeSet}_hangar")
        setupFullscreen()
    }

    private fun wireInsets(target: View) {
        target.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Cutout is the binding constraint in immersive mode; combine with system bars
                // so transient (swipe-revealed) bars never hide combat HUD.
                val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val l = maxOf(cutout.left, bars.left).toFloat()
                val t = maxOf(cutout.top, bars.top).toFloat()
                val r = maxOf(cutout.right, bars.right).toFloat()
                val b = maxOf(cutout.bottom, bars.bottom).toFloat()
                (v as? GameSurfaceView)?.applyInsets(l, t, r, b)
                (v as? HangarSurfaceView)?.applyInsets(l, t, r, b)
            }
            insets
        }
        target.requestApplyInsets()
    }

    private fun setupFullscreen() {
        // Hide system bars for immersive experience
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onPause() {
        super.onPause()
        hangarView?.pause()
        gameView?.pause()
        SoundManager.pause()   // catch-all for phases not handled inside gameView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }

    override fun onResume() {
        super.onResume()
        SoundManager.resume()
        if (currentView == hangarView) {
            hangarView?.resume()
        } else {
            gameView?.resume()
        }
        setupFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreen()
        }
    }
}
