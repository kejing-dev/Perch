# Perch — project guidance for agents

## What this project is

Perch is a focus & task companion for PICO Project Swan (PICO OS 6, Shared Space): one compact
Planar window parked under the user's virtual monitor that holds the task being worked on, the
remaining time of the current focus block, an eye-rest break cue, the next tasks, and a day wire of
what got done today. Design package: `design/design-doc.md` (+ `design/preview.html`).

Scaffolded 2026-09-25 with `pico-cli project create --template planar --name Perch --package io.embodit.perch`
(template 6.1, Spatial SDK BOM 6.1.9, AGP 8.13.2, Kotlin 2.1.20, compile/min/target SDK 35, arm64-v8a).

## Why this structure

- Planar `DefaultWindowContainer` in Shared Space (must coexist with other apps). Container and
  window model are architectural: do not switch to Volumetric/Stage or add Subwindow/TabBar/Toolbar
  casually — the design rejected each with reasons (design-doc §2/§3).
- Layered packages per the spatial-design-to-app architecture conventions:
  - `platform/` — `SpatialApplication` (calls `launch(::mainApp)`, owns `PerchGraph`), `LaunchActivity`
    (`SpatialLaunchActivity` subclass, no UI), `PerchGraph` (repository + timer store + `JsonFileTimerStore`;
    loads `timer.json` before `perch.json`, then saves every timer change that alters the persisted projection).
    Debug builds add `DebugCommandReceiver` (`app/src/debug`), see "Emulator state tour".
  - `domain/model/` — `Task`, `FocusSession`, `FocusSettings`, `TimerState` (pure Kotlin).
  - `domain/usecase/` — `FocusTimerUseCase` (pure wall-clock reducer), `TaskListUseCase`
    (ordering / promote / move / title normalization), `TodaySummaryUseCase` (day rollups).
  - `data/repository/` — `PerchRepository` interface (`snapshot`, `isLoaded`, `loadError`),
    `JsonFilePerchRepository` (writes inside one mutex: temp file → fsync → rename to `files/perch.json`;
    a corrupt file is moved to `perch.json.corrupt` and reported), `FakePerchRepository` (tests).
  - `data/local/` — `PerchJsonCodec` (org.json), `JsonFileTimerStore` (`files/timer.json`: phase, endsAt,
    pausedAt, breakDueAt, session id, cycle count).
  - `data/session/FocusSessionStore` — process-wide live timer so closing the window keeps the block.
  - `ui/focus/` — `FocusUiState` (state + sealed `FocusEvent` + `FocusEffect`), `FocusViewModel`
    (unidirectional; `combine(repository, timer store, local)` → state; plain 1 s ticker loop; block
    transitions serialized by one `Mutex`, store updated before repository writes), `FocusScreen`
    (stateful shell + stateless `FocusContent`), `components/` (PerchCard, QueueSection, QuickAddRow,
    DayWireRow, DurationsPopup, PerchDialogs, LogSheet, TimeFormat, Chime).
  - `ui/theme/` — `PerchTheme` (complete 16-role `ColorScheme` + Noto Sans typography injected via
    `PicoTheme(colorScheme, typography)`), `PerchColors` (design tokens), `PerchType` (Noto Sans).
- `Main.kt` stays thin: `DefaultWindowContainer { PerchTheme { FocusScreen(Modifier.windowConstraints(...)) } }`.

## Key runtime facts

- First-open size is the manifest `pico.spatial.windowcontainer.defaultsize` = `720x480` dp (the Roosting card is ~164 dp tall, so 440 left one queue row),
  `resizetype=2` (content-constrained); `windowConstraints` bounds resize to 560×400 – 960×720
  (the design's 360 dp compact tier is not built; 400 is the smallest height that holds one queue row).
- The window root keeps the system `Material.Regular` glass (manifest `materialbackground=1`);
  never paint a root background. The Perch card is an opaque paper surface lifted with `zOffset`.
- Timer correctness comes from wall-clock end timestamps (`TimerState.endsAtMillis`), not tick counting.
- A block survives process death: `timer.json` restores the live `TimerState` on launch, and a block that
  ended while the app was away lands in `BREAK_DUE` without auto-starting the break.
- Sessions left `RUNNING` with no live timer are reconciled after `repository.isLoaded`.
- Layout: the card and the day wire keep their height; `QueueArea` (weight 1, clipped) fits 1–4 queue rows
  of 64 dp + 4 dp gaps from its measured height and row height; the rest sits behind a
  "+n more" chip that opens the queue face of the log sheet (`FocusEvent.OpenQueue`).
- Strings exist in `values/` and `values-zh-rCN/`; counts use `plurals`.
- Every block transition (start, pause, resume, extend, finish, break, skip, back, perch-next, switch, tick)
  runs under `FocusViewModel.transitions` and starts by syncing the store to the clock (`tickLocked`).
- Queue up-arrow (`PerchNext`): the task takes the perch, except while the perched task is the one being
  worked on (block running, or its break is on and it is not landed yet) → it lines up behind it. "Switch to next task" (`SwitchToNext`) re-points the running session at the new
  task. Landing the perched task mid-block keeps the block logged for the task it finished.

## UI rules (hard constraints)

- All 2D UI is SpatialUI (`com.pico.spatial.ui.design.*`, `design.windows.*`, `design.menu.*`) inside
  `PicoTheme`; Material / Material3 (`androidx.compose.material*`) is forbidden. Icons are the app's
  own vector drawables in `res/drawable/ic_*.xml` (do not add `material-icons`).
- Every `Text` passes an explicit `color`; a slot that inherits the component's content color carries
  `// design-style: inherited-content-color <Component>`.
- Fixed design colors live only in `ui/theme/PerchColors.kt` with `// design-style: fixed-figma-color`
  annotations; use `.fixed()` (Vibrant.None) on paper-card ink so the glass Vibrant system does not re-tint it.
- Custom clickable containers use `clip → spatialHoverEffect → clickable(indication = LocalIndication.current) → controllerHapticFeedback` with one shared `MutableInteractionSource`.
- Typeface is Noto Sans (`res/font/noto_sans_*.ttf`, OFL in `third_party/noto-sans/`).

## Build, test, run

```bash
# Git Bash; the pico-cli managed JDK is not on PATH
JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1' PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk' ./gradlew.bat assembleDebug --no-daemon
JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1' PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk' ./gradlew.bat :app:testDebugUnitTest --no-daemon
pico-cli emulator start --avd PICO_6.1 --format json -y
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device emulator-5554
pico-cli app launch io.embodit.perch --device emulator-5554
pico-cli capture screenshot --device emulator-5554 --out .scratch/shots/<name>.png
pico-cli app logcat --device emulator-5554 --lines 200 --level E
```

Verification gate (spatial-design-to-app):

```bash
bash "C:/Users/Admin/.claude/plugins/cache/pico-xr/pico-spatial-agentic-tools/0.5.0/skills/spatial-design-to-app/scripts/validate_workflow_and_build.sh" \
  "C:/Users/Admin/Downloads/perch" --input-mode intent_only --generation-mode new_project \
  --design-color interaction=#D89B3C --design-color passable=#6F8B6A --design-color alert=#C4623F --design-color error=#B3402B \
  --design-color paper=#F4ECDC --design-color graphite=#2A2723
```

## Emulator state tour

Spatial windows cannot be tapped through adb, so debug builds expose the state machine:

```bash
adb shell "am broadcast -a io.embodit.perch.DEBUG -p io.embodit.perch --es cmd add --es title 'Write the brief'"
adb shell "am broadcast -a io.embodit.perch.DEBUG -p io.embodit.perch --es cmd start"
adb shell "am broadcast -a io.embodit.perch.DEBUG -p io.embodit.perch --es cmd advance --ei seconds 600"
```

Commands: add, draft, start, pause, resume, land, undo, extend, finish, finish_confirm, break, skip, back,
perch, landtask, move, delete, delete_confirm, dismiss, minutes, durations, log, queue, switch, closelog,
logtab, coachmark_done, advance. Screenshot pitfalls on the 6.1 emulator: a static window can come back
one capture stale and partial captures have transparent bands, so capture twice and retry while the
alpha channel is not fully opaque.

Unit tests drive the ViewModel with `runPerchTest` (cancels `viewModelScope` in `finally`) and
`advanceTimeBy + runCurrent`; `advanceUntilIdle` never returns because of the ticker.

## Natural next steps

1. Hour labels under the day-wire axis; hover tooltips on wire blocks.
2. Keyboard shortcuts for physical keyboards (space = pause/resume, esc = close overlays).
3. Settings sheet (work hours, long-break cadence, auto-start break, daily goal) with discard.
4. Device validation on Swan hardware: peripheral legibility of the clock at ~15° below gaze.
