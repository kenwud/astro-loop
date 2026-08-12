# Astro Loop: Compose Multiplatform Adaptation Project Overview & Mission Target

**Repository:** `PubDeer/astro-loop` (100% Kotlin, GPLv3)  
**Goal:** Refactor the open-source Android roguelike shooter *Astro Loop* to Compose Multiplatform (CMP).  
**Objective:** Maintain 100% parity with existing gameplay while enabling native desktop builds (`.exe`, Linux, macOS) alongside Android. The final adaptation will be formatted as a clean, non-disruptive Pull Request (PR) for the original author (`PubDeer`). If declined or unmerged, the fork will serve as the standalone foundation for a custom, moddable PC/Mobile roguelike.

---

## Architectural Principles

* **Zero Logic Rewrites:** Retain existing game loops, collision math, ship/weapon stats, wave algorithms, and state management intact in `commonMain`.
* **Platform Abstraction:** Decouple native Android APIs (`android.graphics.Canvas`, `android.view.View`, Android `Context`) in favor of platform-agnostic Kotlin/Compose primitives and Kotlin `expect`/`actual` declarations.
* **Upstream Compatibility:** Maintain clean commit isolation so features, bug fixes, or PR merges can easily sync back and forth between the fork and upstream repo.
* **Mod-Ready Foundation:** Keep data structures and asset pipelines decoupled from the view layer to prepare for future JSON/Lua/KTS mod loading (Mindustry-style).

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

## Developer Environment & Testing Strategy

To maximize development velocity and avoid heavy Android emulator dependencies, the project uses a **PC-First Workflow**:

1. **Primary Development (`desktopMain`):** 90% of daily testing, physics verification, vector graphics rendering, and game state debugging are executed natively on Windows via `./gradlew :desktopApp:run`.
2. **Mobile Validation (`androidMain`):** 10% of testing (touch scaling, mobile performance) is validated via compiled APKs (`./gradlew :composeApp:assembleDebug`):
   * **BlueStacks:** Drag-and-drop the generated `.apk` directly into BlueStacks for fast desktop-based mobile emulation.
   * **Physical Android Phone:** Transfer the `.apk` via USB storage, Google Drive, or email to test directly on real hardware without needing local ADB/USB debugging setups.

---

## Step-by-Step Conversion Roadmap

### Phase 1: Gradle & Multiplatform Setup
* [ ] Convert single-module Android Gradle structure to a Kotlin Multiplatform (`kmpp`) layout (`commonMain`, `androidMain`, `desktopMain`).
* [ ] Integrate JetBrains Compose Multiplatform Gradle plugins and Version Catalog (`libs.versions.toml`).
* [ ] Move pure Kotlin data structures, game models, and math utilities directly into `commonMain`.

### Phase 2: Input & Control Layer Abstraction
* [ ] Extract single-finger touch dragging from `android.view.MotionEvent`.
* [ ] Map input handlers in `commonMain` to accept both Touch Drag (Mobile) and Mouse Drag / Pointer Movement (Desktop PC).
* [ ] Prepare optional WASD / Arrow Key binding structures for desktop playability.

### Phase 3: Canvas Rendering Port
* [ ] Replace `android.graphics.Canvas` and `android.graphics.Paint` drawing calls with Compose `DrawScope` / `androidx.compose.foundation.Canvas`.
* [ ] Port vector shape paths, lines, circles, and particle effects to standard Compose `Path` and `DrawScope` primitives.
* [ ] Verify vector graphic scaling and high-DPI rendering across arbitrary PC window sizes and mobile display ratios.

### Phase 4: Audio & Storage Abstraction
* [ ] Isolate Android `SharedPreferences` / file I/O behind an `expect`/`actual` interface or multiplatform storage library (e.g., `okio`).
* [ ] Abstract native audio playback wrappers (`expect`/`actual`) so sound effects execute cleanly on desktop JVM (Java Sound) and Android (`SoundPool` / `MediaPlayer`).

### Phase 5: Desktop Packaging & Polishing
* [ ] Configure Gradle desktop distribution settings (`packageVersion`, target formats, executable icons).
* [ ] Test native `.exe` build execution on Windows without emulator or IDE dependencies.
* [ ] Prepare clean PR documentation, architectural summary, and build instructions for `PubDeer`.

---

## AI Agent Directives & Guardrails

When generating code or refactoring files for this project, AI agents must adhere to the following rules:

* **Preserve Vector Math:** Do not replace custom vector drawing logic with bitmap sprites. All visuals must remain programmatic vector primitives on Canvas.
* **Avoid Engine Bloat:** Do not introduce external game engines (Godot, LibGDX, Unity) or heavy third-party game frameworks. Keep the app lightweight and native to Compose.
* **Strict Expect/Actual Isolation:** Any code requiring platform-specific handles (e.g., Android `Context` or Desktop Window Managers) must be isolated behind standard Kotlin `expect`/`actual` declarations.
* **Clean Code Style:** Follow standard Kotlin coding conventions, utilizing functional idioms and immutable state models where applicable.
* **Prioritize PC Execution:** Always ensure code introduced in `commonMain` compiles and runs smoothly under the `:desktopApp:run` target.

---

## Future Expansion (Post-Adaptation Roadmap)

* **Mindustry-Style Modding Engine:** Implement a runtime modloader reading external `.json` configuration packs for custom ships, enemy behaviors, and weapon trees.
* **Steam Deck / Gamepad Support:** Map controller inputs to vector aiming and movement vectors.
* **Custom Desktop UI:** Add mouse hover states, keybinding config screens, and customizable graphics scaling options.

## AI Execution & Prompting Protocol (4-Phase Workflow)

To prevent context exhaustion and build failures, AI agents must work through the conversion in four isolated, sequential phases. **Do not execute multi-phase tasks in a single prompt.**


Phase 1: Gradle & Folder Structure
└── Phase 2: Audio & Storage Abstraction (Expect/Actual)
└── Phase 3: Canvas & Input Port (File-by-File)
└── Phase 4: Desktop Run & Parity Verification

---

### Phase 1: Directory Restructuring & Gradle Setup
* **Scope:** Re-organize the project module hierarchy and update build scripts.
* **Rules:** Move pure Kotlin logic (wave math, collision, data models) into `commonMain`. Do **not** touch rendering or input logic yet.
* **Agent Prompt:**
  > "Read `AGENTS.md`. Refactor the Gradle build configuration (`build.gradle.kts` and `libs.versions.toml`) to a JetBrains Compose Multiplatform project layout targeting `commonMain`, `androidMain`, and `desktopMain`. Create the folder structure and move all pure Kotlin data models, wave/math utilities, and game state classes into `commonMain` without altering any logic."
* **Verification:** Run `./gradlew check` or sync Gradle in your IDE to ensure `commonMain` compiles without missing dependencies.

---

### Phase 2: Storage & Audio Abstraction (`expect`/`actual`)
* **Scope:** Decouple native Android OS interfaces from game logic.
* **Rules:** Isolate `SharedPreferences` and sound engine code behind Kotlin `expect` declarations in `commonMain`.
* **Agent Prompt:**
  > "Identify all references to `android.content.Context`, `SharedPreferences`, and Android audio APIs (`SoundPool`/`MediaPlayer`). Create platform-agnostic `expect` class/interface definitions in `commonMain` for persistent key-value storage and sound playback. Then, create the corresponding `actual` implementations for `androidMain` (using Android APIs) and `desktopMain` (using Java Preferences / Java Sound)."
* **Verification:** Confirm that no class remaining in `commonMain` imports `android.content.Context` or `android.media.*`.

---

### Phase 3: Canvas Rendering & Input Port
* **Scope:** Translate drawing loops and input listeners to Compose Multiplatform primitives.
* **Rules:** Process rendering files **one file at a time**. Do not convert sprite/vector math—only change the drawing handle.
* **Agent Prompt (Repeat per file):**
  > "Take [File_Name.kt] and convert its `android.graphics.Canvas` and `android.graphics.Paint` drawing logic to Compose Multiplatform's `androidx.compose.foundation.Canvas` `DrawScope` API. Translate native `MotionEvent` drag listeners to Compose `pointerInput` gesture detectors in `commonMain`. Ensure coordinate scaling remains resolution-independent."
* **Verification:** Ensure zero `android.graphics.*` imports exist in `commonMain`.

---

### Phase 4: Desktop Build & Parity Verification
* **Scope:** Compile and run natively on Windows/Desktop.
* **Rules:** Debug compiler or runtime errors iteratively.
* **Agent Prompt:**
  > "Run `./gradlew :desktopApp:run` (or inspect the desktop build error log). Fix any missing imports, unresolved platform references, or type mismatches in `desktopMain` or `commonMain` until the game window launches and renders on PC."
* **Verification:** The game window opens on desktop, responds to mouse drag / pointer movement, plays audio, and maintains 60 FPS vector rendering parity with the original Android APK.

---

### Documentation & Reference Links

* **JetBrains Compose Multiplatform Overview:**  
  [Kotlin Multiplatform First App Guide](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-create-first-app.html)  
  *(Teaches how commonMain, desktopMain, and androidMain interact.)*

* **Compose Canvas & Graphics Guide:**  
  [Android Developer Compose Graphics Draw Overview](https://developer.android.com/develop/ui/compose/graphics/draw/overview)  
  *(Gives exact syntax for DrawScope, drawCircle, drawPath, and drawIntoCanvas.)*

* **Compose Pointer Input & Gestures:**  
  [Android Developer Compose Gestures Guide](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)  
  *(Provides reference for translating touch drag gestures to mouse drag events on PC.)*

* **Expect/Actual Mechanism (Sound & Storage):**  
  [Kotlin Multiplatform Connect to Platform APIs](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html)  
  *(Crucial for Phase 2 abstraction of SharedPreferences and sound files.)*