# Perch — iteration roadmap (autonomous loop)

Each round: change → `assembleDebug` + unit tests → design-style verifier → install on AVD `PICO_6.1` → state tour (`tour.sh`) → screenshot review → fix → commit notes here. Full gate (`validate_workflow_and_build.sh`) at every version bump.

## v1.0 — 2026-09-25 (done)
- Planar 720×440 window, paper Perch card (+16/+24 dp), branch progress, queue, quick-add, day wire time axis + goal bar, log sheet, durations popup, finish-early / delete dialogs, undo snackbar, coachmark, chime.
- Domain: wall-clock timer reducer, task ordering, today summary; JSON file repository; process-wide timer store; orphan-session reconciliation.
- Verified on the 6.1 emulator: 17-state tour, no crashes; design package reviewed (pass).

## v1.1 — polish found by the first tours
- [x] Roosting: SegmentControl fixed width so the title keeps its column.
- [x] Log sheet: fixed content width (unbounded `fillMaxWidth` rendered a black sheet); segment items share width.
- [x] Overflow badge neutral colors.
- [x] State chip (Focusing / Paused) in the meta row; tapping toggles pause.
- [x] Re-capture overlay screenshots with longer waits + blank-frame retry (popup / dialog windows).
- [x] Log sheet fixed 328×420 content (a resize after showing left the sheet black); tab labels Label 12.
- [x] AddTask captures the draft at event time (rapid add events dropped titles); ticker bumps `tickMillis` so paused time / now-marker refresh.
- [x] Persist live `TimerState` (`files/timer.json`) so a process death mid-block resumes; a block that ended while away never auto-starts its break.
- [x] Review fixes (2026-10-01): plain ticker loop; transition mutex with store-first ordering (double Start, finish races); repository writes inside the mutex with fsync; corrupt file backup + `LoadFailed` snackbar; `isLoaded` gating of orphan reconciliation; pause-at-zero ends the block; one minute-rounding rule; DST-safe day bounds with injected time zone; undo by task id; finish dialog names the block's task; effects no longer queue behind snackbars.
- [x] Queue overflow reachable: "+n more" chip opens the queue face of the log sheet; visible rows computed from the measured card height; default window 720×480.
- [x] More menu: "Switch to next task" during a block.
- [x] zh-CN strings and plurals (moved up from v1.4).
- [x] Tour script: double capture + alpha-channel check (static windows came back one capture stale).
- [ ] Not done from the review: durations popup applies live without discard; a block running across midnight is counted on its start day; debug `externalEvents` has no replay.
- [x] Hour labels under the day-wire axis (every hour, or every 2–3 when narrow; 12/24-hour clock).

## v1.2 — 2026-10-01, second review round (done)
- [x] Layout: card and day wire keep their height, `QueueArea` fits 1–4 rows from measured space; queue rows 64 dp (ListItem padding 8) with 4 dp gaps; min window height 400. Fixes the day wire being squeezed in Focusing.
- [x] Pause, resume, extend, perch-next and switch run under the transition mutex; every transition first syncs the store to the clock (`tickLocked`), so pausing in the last instant ends the block properly.
- [x] Break auto-start ignores a countdown nobody watched to its end (window closed or process killed during the 20 s).
- [x] Restored / re-opened block: ticker ticks before its first delay and `PerchGraph` recomputes the countdown on restore (no 00:00 flash); a live block whose session record is missing gets one rebuilt.
- [x] Break card chip undoes exactly the task it landed (`breakLandedTaskId`).
- [x] Finish-early dialog shows live minutes and closes when the block ends by itself.
- [x] Up-arrow during a block or break lines the task up behind the perched one ("Next up" snackbar); "Switch to next task" re-points the running block at the new task.
- [x] Day-wire axis stretches to cover blocks outside the work hours.
- [x] Duration text localized (`duration_h_m` / `duration_h` / `duration_m`).
- [x] Up-arrow during a break: lines up behind the perched task until that task is landed, then picks the next block.
- [x] Log: breaks read "Eye rest"; a break cut off before it rounds to a minute is hidden from the log and the wire; sheet height pinned while open.
- [x] zh-CN pass on the emulator with an app-scoped locale (`cmd locale set-app-locales`): Noto Sans Latin + system CJK fallback renders cleanly.
- Evidence: 57 unit tests; tours `v8`–`v10` and `v9zh` in `.scratch/shots/`; review workflow `perch-v11-review` (8 agents, 12 findings confirmed, all fixed).

## v1.2 — comfort & input
- [ ] Physical keyboard: Enter adds (done via IME action), Space pauses/resumes when the field is not focused, Esc closes overlays.
- [ ] Hover tooltips on day-wire blocks (task, start, minutes) via the SpatialUI tooltip modifier.
- [ ] Controller haptic on Start / Land (built-ins already vibrate; confirm on device).
- [ ] Replace `ToneGenerator` beep with a bundled soft chime (OGG) played through `MediaPlayer`.

## v1.3 — settings & log
- [ ] Settings sheet: work hours, blocks before long break, auto-start break toggle, daily goal.
- [ ] Log "7 days": per-day totals row above the list; empty-day copy.
- [ ] Export today's log as text to the clipboard (share to the monitor side).

## v1.4 — localization & type
- [x] Verify CJK fallback rendering with Noto Sans Latin + system CJK on a zh-CN locale emulator run (done in v1.2).
- [ ] Reflow audit at 560×360 and 960×720 (one / four queue rows), clock Display 32 below 640 dp width.

## v1.5 — hardening
- [x] Adversarial code review workflow (correctness, lifecycle, coroutine cancellation, persistence races) — 43 findings triaged into v1.1.
- [ ] Emulator soak: 4-hour simulated day via `advance` commands; memory / logcat scan.
- [ ] Release build (`assembleRelease`, minify off) and APK size check (fonts ~3.3 MB).

## Later / device-only
- Swan hardware: peripheral legibility of the clock at ~15° below gaze; placement memory across launches; hand-ray hit comfort on 56 dp controls.
