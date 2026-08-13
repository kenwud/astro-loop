# AGENTS.md — Astro-Loop Compose Multiplatform Porting Guidelines

## Primary Objective & Target
**Repository:** `kenwud/astro-loop` (Fork of `PubDeer/astro-loop`)  
**Architecture Base:** 100% pure Kotlin, no external engine, vector Canvas graphics.
**Goal:** Complete a 1:1 Kotlin Multiplatform (CMP) port of the Android game *Astro-Loop* to Desktop (Windows JVM / Skia via Compose Multiplatform) and mobile from shared code (`commonMain`).  
**Objective:** Maintain 100% parity with existing gameplay, vector visuals, sound design, and meta-progression loops (Ship Selection → Survival Run → Death/Game Over → Upgrade/Shop).

---
## STRICT OPERATIONAL RULES FOR AI AGENTS

### 1. Absolute Prohibition of Mocking & Stubs
* **Port the ENTIRE game.** Do NOT write placeholder game engines, fake state machines, dummy ship entities, or simplified UI overlays to force a quick build pass.
* **Zero Rewrites:** Preserve existing game loops, vector math, collision math, ship/weapon stats, wave algorithms, and state management intact in `commonMain`.

### 2. Incremental Migration Protocol (Do Not Bulk Port)
To avoid AI context degradation and untraceable compilation errors, you MUST migrate the codebase incrementally, verifying compilation at each step before moving to the next:
1. **Math & Models:** Move pure Kotlin math, vectors, and stateless entity classes first.
2. **State & Logic:** Move wave logic and game state managers.
3. **Platform Abstractions:** Implement `expect`/`actual` for Audio and Storage.
4. **Rendering & Controls:** Port UI loops, Compose `Canvas`, and Input handlers.

### 3. Architectural Principles & Platform Abstraction
* **Vector Graphics Engine:** Preserve custom vector drawing logic. Do NOT replace vector shapes with bitmap sprites or external game engines (Godot, LibGDX, Unity).
* **Canvas Porting:** Replace `android.graphics.Canvas` and `android.graphics.Paint` drawing calls directly with Compose Multiplatform's `androidx.compose.foundation.Canvas` `DrawScope` API.
* **Platform Abstraction (`expect`/`actual`):** Decouple native Android OS interfaces in `commonMain`:
  * **Storage:** Abstract `SharedPreferences` / file I/O using multiplatform storage or `expect`/`actual` key-value pairs.
  * **Audio:** Abstract Android `SoundPool` / `MediaPlayer`.
  * **Hardware Context:** Remove all `android.content.Context`, `SurfaceHolder`, `Vibrator`, and native Android View references.

### 4. Desktop Audio & Resource Management
* **Bundled Fonts:** The original game utilizes custom fonts (Exo 2, Orbitron). You MUST move these `.ttf` or `.otf` assets into the `composeResources/font` directory to utilize them across platforms natively via the `Res.font` API.
* **Audio Pooling on PC:** You MUST implement an audio pooling mechanism (similar to Android's `SoundPool`) in the `desktopMain` audio implementation. Do NOT spawn raw `javax.sound.sampled.Clip` instances per sound event, as rapid-fire arcade sounds will cause thread locks and audio clipping on the JVM.

### 5. PC Input & Desktop Runner Requirements (`shared/src/desktopMain/`)
* **Dual Control Mapping:** Map input handlers in `commonMain` to accept both Touch Drag (Mobile) and Mouse/Pointer Drag (Desktop PC).
* **Keyboard Bindings:** Map WASD / Arrow Keys for steering/thrust, Space/Z for actions, and Esc/Enter for menu/shop navigation directly into `InputController`.
* **Desktop Runner:** `Main.kt` and `DesktopSharedCanvas` MUST execute the migrated `GameLoop` natively on Windows.

---
## Technical Stack

* **Language:** Kotlin 2.x
* **UI Framework:** Compose Multiplatform (`androidx.compose`)
* **Graphics Rendering:** `androidx.compose.foundation.Canvas` (powered by Skia on Desktop)
* **Build System:** Gradle (`build.gradle.kts`) with Multiplatform Plugin (`org.jetbrains.kotlin.multiplatform`)
* **Targets:**
  * `commonMain` (Shared game logic, vector rendering, math, and UI)
  * `desktopMain` / `jvmMain` (Windows `.exe`)
  * `androidMain` (Android APK)

---
## Pragmatic 1:1 KMP Desktop Migration Plan

### Step 1: Project Hierarchy & Gradle Setup
1. Refactor the Gradle build configuration to support Kotlin Multiplatform and JetBrains Compose plugins.
2. Establish the standard KMP module structure (`commonMain`, `desktopMain`, `androidMain`).
3. Verify Gradle sync completes cleanly before proceeding to code relocation.

### Step 2: Extract Core Math & Engine Logic to `commonMain`
1. Identify and audit pure Kotlin files (e.g., entity vectors, collision algorithms, wave generators).
2. Ensure target files contain **zero** imports from `android.graphics.*`, `android.view.*`, or `android.content.Context`.
3. Relocate and verify compilation for both Desktop and Android.

### Step 3: Isolate Native Platform Services (`expect` / `actual`)
1. **Audio System:**
   * `commonMain`: Define an `AudioPlayer` interface.
   * `androidMain`: Implement `actual` using Android `SoundPool`.
   * `desktopMain`: Implement `actual` using standard Java Sound API, but strictly wrapped in an **audio pre-loader/pool manager** to handle polyphony.
2. **Storage & Preferences:**
   * `commonMain`: Define a key-value high-score storage interface.
   * `desktopMain`: Implement local file-based JSON / properties persistence.

### Step 4: Port Graphics Rendering (`commonMain`)
1. Wrap game rendering logic inside a Compose `Canvas` composable.
2. Map legacy Android `Canvas` rendering routines to Compose `DrawScope` primitives (`drawLine`, `drawPath`, `drawCircle`).
3. Implement `composeResources` for all text drawing to load embedded fonts seamlessly.

### Step 5: High-Performance Game Loop & Controls
1. **60 FPS Recomposition optimization:** Execute game state frame updates inside a `LaunchedEffect` using `withFrameNanos`. Do NOT tie rapidly mutating coordinate structures (like individual bullet positions) to individual Compose `State` variables, as this will crush the recomposer. Maintain a standard Kotlin object for physics state, and use a single tick state to trigger Canvas redraws.
2. **Input Handling:** Use `Modifier.pointerInput` with `detectDragGestures` for mouse/touch, and `Modifier.onKeyEvent` (with `FocusRequester` attached to the Canvas) for keyboard movement.

### Step 6: Desktop Launcher Setup (`desktopMain`)
Create the native desktop runner entry point inside `desktopMain/kotlin/main.kt`:

```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Astro Loop"
    ) {
        AstroLoopGameScreen()
    }
}
```
---
## Technical Implementation Directives (Compose Canvas & Input)

1. **DrawScope Adaptation:**
   - Eliminate all references to `android.graphics.Paint`.
   - Convert stroke and fill properties to Compose `Stroke(width = ...)` and `Fill` parameters directly inside `DrawScope` calls.

2. **60 FPS Game Loop:**
   - Execute game state frame updates inside a `LaunchedEffect` using `withFrameNanos`.
   - Avoid triggering whole-screen UI recompositions for per-frame particle or entity coordinate updates; limit mutations to the active drawing state.

3. **Desktop Keyboard Focus:**
   - Always attach `FocusRequester` and `.focusable()` alongside `.onKeyEvent` on the main game canvas composable, requesting focus on initial composition.


---
## Implementation Snippets & Patterns

Do not hallucinate APIs or external documentation. Use the following exact patterns for the multiplatform implementation:

### 1. Custom Fonts (Compose Resources)

Place font assets in `composeResources/font/`. Access them using the generated `Res` class:

```kotlin
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import astroloop.shared.generated.resources.Res
import astroloop.shared.generated.resources.orbitron

val orbitronFamily = FontFamily(Font(Res.font.orbitron))
```

### 2. Unified Input Handling (Touch, Mouse & Keyboard)

Attach these modifiers to the primary Canvas to capture both drag gestures and WASD/Arrow keys:

```kotlin
Modifier
    .focusable()
    .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            // Feed dragAmount into ship steering coordinates
        }
    }
    .onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.W) {
            // Trigger thrust
            true
        } else false
    }

```

### 3. Vector Drawing on Canvas

Map Android `Paint` strokes directly to Compose `Stroke`:

```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    drawPath(
        path = vectorPath,
        color = Color.Cyan,
        style = Stroke(
            width = 2f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

```

### 4. JVM Audio Pool (Desktop Polyphony)

To prevent thread locks and audio dropouts on Desktop JVM, pre-load sounds into a `Clip` pool:

```kotlin
class DesktopAudioPool(resourcePath: String, poolSize: Int = 5) {
    private val clips = Array(poolSize) { loadClip(resourcePath) }
    private var currentIndex = 0

    fun play() {
        val clip = clips[currentIndex]
        if (clip.isRunning) clip.stop()
        clip.framePosition = 0
        clip.start()
        currentIndex = (currentIndex + 1) % poolSize
    }
}
```