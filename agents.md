# AGENTS.md — Astro-Loop Compose Multiplatform Porting Guidelines

## Primary Objective & Target
**Repository:** `PubDeer/astro-loop` (100% Kotlin, GPLv3)  
**Goal:** Complete a 1:1 Kotlin Multiplatform (CMP) port of the Android game *Astro-Loop* to Desktop (Windows JVM / Skia via Compose Multiplatform) and mobile from shared code (`commonMain`).  
**Objective:** Maintain 100% parity with existing gameplay, vector visuals, sound design, and meta-progression loops (Ship Selection → Survival Run → Death/Game Over → Upgrade/Shop).

---

## STRICT OPERATIONAL RULES FOR AI AGENTS

### 1. Absolute Prohibition of Mocking & Stubs
* **Port the ENTIRE game.** Do NOT write placeholder game engines, fake state machines, dummy ship entities, or simplified UI overlays to force a quick build pass.
* Do NOT ask to port individual systems or features piecemeal. The entire codebase in `app/src/main/java/com/astroloop/` MUST be migrated into `shared/src/commonMain/kotlin/com/astroloop/`.
* **Zero Rewrites:** Preserve existing game loops, vector math, collision math, ship/weapon stats, wave algorithms, and state management intact in `commonMain`.

### 2. Architectural Principles & Platform Abstraction
* **Vector Graphics Engine:** Preserve custom vector drawing logic. Do NOT replace vector shapes with bitmap sprites or external game engines (Godot, LibGDX, Unity). All visuals must remain programmatic vector primitives on Canvas.
* **Canvas Porting:** Replace `android.graphics.Canvas` and `android.graphics.Paint` drawing calls directly with Compose Multiplatform's `androidx.compose.foundation.Canvas` `DrawScope` API.
* **Platform Abstraction (`expect`/`actual`):** Decouple native Android OS interfaces behind platform-agnostic Kotlin `expect`/`actual` declarations in `commonMain`:
  * **Storage:** Abstract `SharedPreferences` / file I/O using multiplatform storage or `expect`/`actual` key-value pairs (using Java Preferences / File I/O on desktop).
  * **Audio:** Abstract Android `SoundPool` / `MediaPlayer` audio wrappers so sound effects and background audio play natively on PC (via Java Sound / Clip) and mobile.
  * **Hardware Context:** Remove all `android.content.Context`, `SurfaceHolder`, `Vibrator`, and native Android View references from `commonMain`.

### 3. PC Input & Desktop Runner Requirements (`shared/src/desktopMain/`)
* **Dual Control Mapping:** Map input handlers in `commonMain` to accept both Touch Drag (Mobile) and Mouse/Pointer Drag (Desktop PC).
* **Keyboard Bindings:** Map WASD / Arrow Keys for steering/thrust, Space/Z for actions, and Esc/Enter for menu/shop navigation directly into `InputController`.
* **Desktop Runner:** `Main.kt` and `DesktopSharedCanvas` MUST be structured to execute the full, migrated `GameLoop` / state machine natively on Windows.

### 4. Bulk Migration & Verification Protocol
* **Bulk Porting First:** Complete the file migration and Android API stripping across the ENTIRE codebase before running any build commands. Do NOT pause to fix intermediate compilation errors by creating fake stubs or dummy classes.
* **Single Compile Check:** Once all original files are migrated into `commonMain`, run `.\gradlew.bat :shared:compileKotlinDesktop` ONCE to identify true missing multiplatform dependencies or platform type mismatches.
* **No Automated App Execution:** Do NOT run `:shared:run`. Once compilation succeeds with zero errors, notify the developer to launch and test the application manually.

### 5. Cleanup Protocol
* Delete any temporary or generated stub files (e.g., placeholder `GameEngine.kt` or `TypesStubs.kt`) once the original codebase is fully migrated into `commonMain`.

---

## Technical Stack

* **Language:** Kotlin 2.x
* **UI Framework:** Compose Multiplatform (`androidx.compose`)
* **Graphics Rendering:** `androidx.compose.foundation.Canvas` (powered by Skia on Desktop)
* **Build System:** Gradle (`build.gradle.kts`) with Multiplatform Plugin (`org.jetbrains.kotlin.multiplatform`)
* **Targets:**
  * `commonMain` (Shared game logic, vector rendering, math, and UI)
  * `desktopMain` / `jvmMain` (Windows `.exe` / `.msi`, Linux `.AppImage`, macOS `.dmg`)
  * `androidMain` (Android APK)

---

## AI Agent Directives & Guardrails

* **Preserve Vector Math:** Do not replace custom vector drawing logic with bitmap sprites. All visuals must remain programmatic vector primitives on Canvas.
* **Avoid Engine Bloat:** Do not introduce external game engines (Godot, LibGDX, Unity) or heavy third-party game frameworks. Keep the app lightweight and native to Compose.
* **Strict Expect/Actual Isolation:** Any code requiring platform-specific handles (e.g., Android `Context` or Desktop Window Managers) must be isolated behind standard Kotlin `expect`/`actual` declarations.
* **Clean Code Style:** Follow standard Kotlin coding conventions, utilizing functional idioms and immutable state models where applicable.
* **Prioritize PC Execution:** Ensure all shared code builds cleanly for the desktop JVM target.

---

## Documentation & Reference Links

* **JetBrains Compose Multiplatform Overview:**  
  [Kotlin Multiplatform First App Guide](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-create-first-app.html)  

* **Compose Canvas & Graphics Guide:**  
  [Android Developer Compose Graphics Draw Overview](https://developer.android.com/develop/ui/compose/graphics/draw/overview)  

* **Compose Pointer Input & Gestures:**  
  [Android Developer Compose Gestures Guide](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)  

* **Expect/Actual Mechanism (Sound & Storage):**  
  [Kotlin Multiplatform Connect to Platform APIs](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html)