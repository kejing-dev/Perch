# Spatial App Design Doc — Perch

> Design package produced by `pico-spatial-app-designer` (Frame → Explore → Plan → Critique → Build → Critique again). Facts are in PICO design terms (Shared Space, WindowContainer Planar). Downstream code generation is owned by `spatial-design-to-app`.

## 1 · Frame

**Subject** — A focus & task companion for PICO Project Swan (PICO OS 6). Domain: personal productivity for desk-based knowledge work inside a headset that is used as a monitor replacement (Swan: 4000 PPI micro-OLED, 12 ms passthrough, several app windows side by side). The brief was one line ("the productivity app you think fits Swan best"); the subject was defined from Swan's own positioning: a monitor replacement invites 1–4 hour desk sessions, and the thing those sessions lack is a place, outside the monitor, that holds *what I am doing now* and *how long I have left*.

**Viewer & context** — One person, seated at a real desk, headset on with passthrough, 1–4 h sessions. The main work surface is a large virtual monitor ~1.5–2 m ahead at the center of view. Hands on a physical keyboard/mouse most of the time; the companion is driven by gaze + pinch / hand ray / controller ray, with occasional typing through the system keyboard. Reading distance ~1.5–1.75 m; PICO dynamic world scale keeps the window's angular size constant.

**The single job** — Decide what to work on next and protect a focus block for it: start a timed block on ONE task, keep that task + remaining time visible while working on the monitor, take the eye-rest break when it is due, and see what got done today.

**Spatial justification**

| Core task | Affordance | Why spatial beats flat | 2D counterfactual |
| --------- | ---------- | ---------------------- | ----------------- |
| T1 capture / pick tasks | Position (fixed world spot under the monitor) | The quick-add field lives at one world position; eyes drop, pinch, type, Enter, eyes back up. The system keyboard rises under the work, not over it. "Perch next" is one 56 dp pinch at a known place. | A to-do window on the monitor must be found (alt-tab), steals keyboard focus from the work app, and its IME overlaps the document; capture costs 3–5 s and a context switch. |
| T2 run a focus block | Position + scale (three 56 dp controls at fixed coordinates, ~1.7° each) | Pause / Done / More never move for the whole session, so gaze+pinch finds them without reading; they sit on the same lifted plane as the clock they act on. | A Pomodoro window's buttons move with window-manager state (behind, minimized, resized): bring forward, locate, click, return — 2 extra clicks per pause. |
| T3 glance at task + remaining time | Distance/scale + lower-peripheral position + depth (+16 dp card over glass) | Display-40 digits (~1.2° cap height) on an opaque paper card ~15° below the gaze line read with a micro-saccade; the filled branch gives a pre-attentive "how far along" without decoding digits. The monitor keeps every pixel. | On the monitor the timer is a 12 px tray/tab number or a window that occludes or is buried; a second virtual monitor would spend work-surface FOV on one number. |
| T4 break cue when a block ends | Motion only for state change: 300 ms depth lift (+16 → +24 dp) + paper tint shift, where the eye already checks | Stereo depth change is noticed peripherally before color is decoded, at the spot glanced at every few minutes; caught without a sound blast or a modal. A 20 s auto-start protects the eyes mid-sentence. | A corner toast overlaps work, disappears in 5 s and looks like every other toast; a modal steals keyboard focus mid-keystroke. Both train dismissal, not rest. |
| T5 review today | Time laid along a track: the day wire is one horizontal row of dots under the monitor, morning left, now right | The tally is always visible at the bottom of the same strip; landing a task visibly adds a dot where the day happened; a Sheet expands the row in place. | A stats page is a separate screen you navigate to and rarely open; the tally is not co-located with the timer. |

Form and scale follow from this: a **compact Planar WindowContainer in Shared Space**, no attachments. The job is a glanceable companion, not a wall, not a 3D subject, not an immersive scene.

**Assumptions**

| Assumption | Confidence | What changes if wrong |
| ---------- | ---------- | --------------------- |
| Shared Space remembers per-app window placement between launches | medium | Spatial-memory argument for T1/T2 weakens; add a first-run coachmark "park me under your monitor" (already planned) and keep the window small so re-parking costs one drag |
| A 720 dp strip ~15° below the gaze line is read peripherally while typing | medium | Increase clock to Display 40 → larger custom size, or recommend beside-monitor placement; verify on device |
| Users run 25/45/90-minute blocks with a 5-minute eye-rest break | high | Durations are settings (5–180 focus, 1–60 break) |
| English UI first; Noto Sans for Latin; CJK falls back to the system font | high | Add localized strings later; Noto Sans CJK would add ~10 MB per weight |
| Single user, local persistence, no calendar/cloud in v1 | high | Repository interface allows a synced implementation later |
| Typing is possible (system keyboard or physical keyboard through the streamed PC) | high | Quick-add stays; voice capture is a later feature |

## 2 · Explore — alternatives

**Direction generation** — subagent-per-direction, four fresh-context agents spawned in parallel, each seeded only with the shared frame plus one divergence constraint and blind to the others; two independent judges then scored all four. Full proposals are archived as `direction_A..D.json` in the session scratchpad; judge outputs as `judge_1.json` / `judge_2.json`.

### Direction A — Perch Shelf, the glanceable sidekick

One 720×440 dp frosted shelf parked under the virtual monitor, zero ornaments. Signature: **"the branch and the bird"** — an 8 dp LinearProgressIndicator (the branch) spans a cream paper card lifted +16 dp; above it a Display-40 clock and exactly ONE perched task. Pinching ✓ lands the task (title crossfades, a dot lands on the day wire, the next task hops onto the branch) and the clock never stops. When the block ends the same card lifts to +24 dp and turns into the robin-breast eye-rest card. Main risk: placement memory is system-owned; only two queued tasks visible at default size.

### Direction B — The Perch and the Tree (ornament-led, Subwindow)

Main Planar window 920×640 (backlog, capture, day wire) plus a 360 dp Subwindow instrument holding one task, a 200 dp ring and three controls, always visible regardless of the main content. Signature: "one bird on the perch". Main risk: the assembly is ~1300 dp wide; the Subwindow side is system-assigned, so the "ring nearest the monitor" argument may not hold; heavier for a daily companion.

### Direction C — The Ledge (day track)

A 1600×420 dp strip where today runs left→right at 4 dp/min under a fixed now-marker; tasks are perched onto slots and become blocks whose width is their duration; finished blocks settle left with a moss check. Signature: duration as literal length. Main risk: 51° wide with a moving track and drag-to-assign interaction; the most complex to build and the least "simple"; the strip competes with the monitor's width.

### Direction D — Nearest Branch (depth stack)

One 800×600 dp window beside the monitor with a bottom Toolbar; the Now card at +24 dp, the NEXT row at +8 dp, the queue and done pile at 0 dp; state changes are dealt like cards (350 ms lift/settle). Signature: "nearest thing is the only thing". Main risk: a 600 dp tall window beside the monitor puts the ring at 28–32° eccentricity; depth-as-priority is subtle at 1.75 m and needs device validation; the Toolbar separates controls from the card.

**Decision matrix** — two independent judges scored all four (1–5; risk 5 = lowest). Judge 1 weighted comfort and everyday efficiency (this is used 1–4 h/day beside a monitor); Judge 2 weighted spatial value and distinctiveness (total = eff + 2×spatial + comfort + domain + risk + 2×distinct, max 40).

| Direction | Task efficiency (J1/J2) | Spatial value | PICO comfort | Domain fit | Risk | Distinctiveness | Total J1 / J2 |
| --------- | ----------------------- | ------------- | ------------ | ---------- | ---- | --------------- | ------------- |
| A         | 5 / 4                   | 3 / 3         | 5 / 5        | 5 / 5      | 5 / 5 | 3 / 3          | 26 / 31       |
| B         | 3.5 / 4                 | 3 / 3         | 3 / 3        | 4 / 4      | 2 / 2 | 3 / 3          | 18.5 / 25     |
| C         | 3 / 3                   | 4 / 5         | 2.5 / 3      | 4 / 4      | 2 / 2 | 5 / 5          | 20.5 / 32     |
| D         | 3 / 4                   | 3 / 3         | 3 / 4        | 4 / 5      | 3 / 4 | 3 / 3          | 19 / 29       |

Judge 1 picked A; Judge 2 picked C narrowly over A (32 vs 31) and only under the spatial-value-first weighting, while noting C passes the 65° clear-FOV gate "by about 1° vertically" and only with a width cap. Both judges flagged A ≈ D as near-duplicates in information architecture (one lifted now-card, quick-add beneath, queue on glass, tally row); D was therefore treated as a variant of A and its best ideas grafted rather than competing.

**Chosen: A** — The brief is a *simple* daily companion used 1–4 h a day beside a monitor, so comfort and everyday efficiency are the criteria that decide it: A is the only direction where every frequent action is one pinch at a fixed spot on the same plane as the clock, the whole surface (~22°×13°) is reached by a ~15° eye drop with no head turn, nothing ever moves or resizes, and every element is a listed SpatialUI component (lowest build risk for a week of emulator iteration). Its known weaknesses — the depth lift being below stereo threshold as the *sole* break cue, the day wire being an unordered dot row, only two queued rows — are all fixed by grafts below without changing the architecture.
**Rejected** — B: its own proposal concedes the Subwindow is "designed as secondary or navigation content", that the system may assign its side, collapse or hide it, and may not expect 1 Hz Display-sized updates in it — the exact situations the instrument was chosen for; the ~1300×640 dp assembly (≈42°×21°) is the largest footprint for a glance-only job. C: by its own 0.032°/dp estimate the strip is ~51° wide, wider than the monitor it docks under, so quick-add (5–15×/day) and header actions sit at ±25° azimuth while the highest-frequency transport controls live in a Toolbar below a strip whose bottom edge is already at −26°; the sliding track and drag-to-assign make it the most complex build for the least frequent task, and Judge 2's own conditions (cap at 1280 dp, relocate controls) turn it into a wider A. D: its depth axis is self-refuted (24 dp ≈ 2 arcmin, "sub-threshold in the periphery"), so priority is carried by tone and scale, i.e. a 2D shadow; parking beside the monitor at 40–45° eccentricity means every title read needs a head turn; and the Toolbar ornament separates controls from the card they act on.
**Grafts adopted** (from the judges' lists) — (1) from B/C: the day wire becomes a real time axis (work hours, default 9:00–18:00, auto-extended) with one block per finished focus block at its start time, width proportional to minutes with a 56 dp floor, plus a "now" marker, so the shape of the day reads as spacing; (2) from D: the break cue is redundant — paper→blush tint, eye icon, Display-40 "5:00", one soft chime — and the +16→+24 dp lift is a secondary channel, animated as a 16→32→24 dp pulse over 300 ms; (3) from B: a "Land ‹task›" ToggleableChip on the Landing/Resting card so the task can be closed at block end; (4) from D: an explicit Empty state where the quick-add field moves into the Perch card with "What's the one thing?"; (5) from D: a daily focus goal (default 180 min, settable) shown as a short LinearProgressIndicator in the tally row; (6) from C: queue rows drop to reduced emphasis while a block runs; (7) from B/D: the branch thickens from 8 to 12 dp so its fill fraction is the pre-attentive read at −15° to −20°; (8) from D: the early-finish AlertDialog states the minutes that will be logged.

## 3 · Plan

### Visual tokens

The design's own colors drive the theme. Downstream builds a **complete custom `ColorScheme`** from this table and injects it through `PicoTheme(colorScheme = …)`.

| `ColorScheme` role  | Exact hex or explicit Vibrant inheritance   | Used for |
| ------------------- | ------------------------------------------- | -------- |
| `fillPrimary`       | `inherit SpatialUI Vibrant fillPrimary`     | ListItem / component fills on the glass |
| `fillSecondary`     | `inherit SpatialUI Vibrant fillSecondary`   | secondary button / chip fills |
| `fillTertiary`      | `inherit SpatialUI Vibrant fillTertiary`    | segment control track, text field fill |
| `fillLight`         | `inherit SpatialUI Vibrant fillLight`       | large light sections (unused at default size) |
| `labelPrimaryLight` | `inherit SpatialUI Vibrant labelPrimaryLight` | text on the ochre Start button |
| `labelPrimary`      | `inherit SpatialUI Vibrant labelPrimary`    | primary text on the glass (queue titles) |
| `labelSecondary`    | `inherit SpatialUI Vibrant labelSecondary`  | tally text, meta on glass |
| `labelTertiary`     | `inherit SpatialUI Vibrant labelTertiary`   | placeholders, receded queue while focusing |
| `labelQuaternary`   | `inherit SpatialUI Vibrant labelQuaternary` | faint hints |
| `lightenHover`      | `inherit SpatialUI Vibrant lightenHover`    | hover state layer |
| `lightenPressed`    | `inherit SpatialUI Vibrant lightenPressed`  | pressed state layer |
| `error`             | `#B3402B` deep robin                        | destructive: delete task, end block early |
| `alert`             | `#C4623F` robin breast                      | eye-rest break only (Landing / Resting) |
| `passable`          | `#6F8B6A` moss                              | landed / done marks, day wire dots |
| `interaction`       | `#D89B3C` 9 a.m. light (ochre)              | the single active accent: Start, branch fill, Focusing chip |
| `dividerLine`       | `inherit SpatialUI Vibrant dividerLine`     | divider above the day wire |

**Brand / decorative tokens outside `ColorScheme`**

| Token name | Exact hex | Used for |
| ---------- | --------- | -------- |
| paper      | `#F4ECDC` | Perch card surface (opaque, so the clock reads over passthrough) |
| paperEdge  | `#E3D8C2` | 1 dp outline of the Perch card |
| paperBlush | `#F6E0D6` | Perch card surface during Landing / Resting |
| graphite   | `#2A2723` | ink on paper: clock digits, perched title, card icons |
| graphiteSoft | `#6B6560` | meta line on paper |
| ochreTrack | `#33D89B3C` | empty branch outline / progress track (20 % ochre) |
| robinTrack | `#33C4623F` | break branch track (20 % robin) |

**Semantic colors**

| Status | Design color / role | Redundant cue (shape/icon) | UI label |
| ------ | ------------------- | -------------------------- | -------- |
| Focusing | `interaction` ochre branch fill | ▶ play glyph in the state chip; branch fills left→right | "Focusing" |
| Paused | graphite digits + `labelSecondary` chip | ❚❚ pause glyph; digits stop | "Paused · 3 min" |
| Break due / resting | `alert` robin, paperBlush card | eye icon; card lifts +8 dp; branch drains right→left | "Eyes off the screen" / "Resting" |
| Landed / done | `passable` moss | ✓ check glyph, filled dot on the day wire | "Landed" |
| Planned (not yet run) | `labelTertiary` outline dot | outlined dot | "planned" |

**Type** — Noto Sans for every role (user requirement), keeping the PicoTheme scale: Display Large 40 Bold (clock), Headline Medium 20 Bold (perched title), Title Medium 16 Semibold (sheet headers), Label Large 14 / Label Medium 12 Semibold (meta, chips, tally), Body Large 16 / Body Medium 14 Medium (queue titles, hints). CJK falls back to the system font. Nothing below 12 sp; the clock drops to Display 32 below 640 dp width.

**Materials / depth** — Window root: system `Material.Regular` glass (nothing painted on the root). Perch card: opaque paper surface lifted **+16 dp** (`zOffset`), 24 dp corner; **+24 dp** during Landing (300 ms ease) settling back to +16 when the break runs. Queue rows, quick-add and day wire sit at 0 dp on the glass. Overlays use the window-level material of their SpatialUI component (Sheet Thick, Menu / SpatialPopup Thick, AlertDialog Thick). Total depth 24 dp of the 640 dp budget; no negative offsets; the window never moves or resizes itself.

**Glass premise** — the preview root is the vendored SpatialUI Web `vibrant` theme with its `Material.Regular` glass; no substitute gray root.

### Signature element

**The branch and the bird.** One task perches on an 8 dp ochre branch that fills as the block runs; landing the task (✓) crossfades it out, drops a moss dot on the day wire and hops the next task up without stopping the clock. Everything around it is quiet: queue rows on bare glass, one divider, one tally line. The only color events of the day are ochre (running), moss (landed) and robin (rest).

### Surface sizing

| Window / surface | Type | Content | Default (dp) | Min | Max |
| ---------------- | ---- | ------- | ------------ | --- | --- |
| Perch main window | WindowContainer Planar, Shared Space | Perch card + queue + quick-add + day wire | 720 × 440 | 560 × 360 | 960 × 720 |
| Log sheet | Sheet overlay (component-owned) | tally + landed list | component default | — | — |
| More menu / duration popup | Menu / SpatialPopup (component-owned) | 4 items / three number fields | component default | — | — |
| Confirm dialogs | AlertDialog (component-owned) | title + body + 2 buttons | component default | — | — |

Sizing was validated internally against the chain (glanceable 2D companion → productivity tier at ~1.75 m → 720 dp ≈ 22° wide, well inside 65°×40° when foveated and inside the 85°/55° band while the monitor holds the center → 56 dp hit floor for 3 controls + clock column + 2-line title → width 720; height = 16 + 144 card + 8 + 2×60 queue + 56 quick-add + 17 divider band + 60 wire + 16 = 437 → 440). Larger sizes only add queue rows and breathing room; the type scale never changes.

**Height chains (measured in the web mirror, tallest card = Landing; the runtime build re-measures with its own component heights)**

| Height band | Rows | Chain (inset + card + gap + queue + gap + quick-add + gap + divider + gap + wire + inset) | Holds |
| ----------- | ---- | ---- | ----- |
| **Min 360** (≤ 435) | 1 | 12 + 136 (560 wide) / 141 (≥ 640, Display-40 break clock) + 4 + 64 + 4 + 49 + 4 + 0.5 + 4 + 60 + 12 = 349.5 / 354.5 | ≤ 360 ✓ (slack 10 / 5) — inset 12, card padding 10 / inner gap 6; the overflow badge moves onto the single row; at 560 wide the goal bar drops under the one-line tally so the wire row stays 60 |
| **Default 440** (436–527) | 2 | 16 + 147 + 4 + 128 + 4 + 49 + 4 + 0.5 + 4 + 60 + 16 = 432.5 | ≤ 440 ✓ (slack 7); two rows need ≥ 433, so the band starts at 436 |
| **Third row** (≥ 528) | 3 | worst case 960 wide (inset 20, card 163): 20 + 163 + 4 + 192 + 4 + 49 + 4 + 0.5 + 4 + 60 + 20 = 520.5 | ≤ 528 ✓ (slack 7); the wider 20/24 inset itself only applies at ≥ 860 × 464 (two-row chain 456.5) |
| **Max 720** (≥ 600 gap 12) | 3 | 20 + 163 + 12 + 192 + 12 + 49 + 12 + 0.5 + 12 + 60 + 20 = 552.5 | ≤ 720 ✓; the remaining 167 px stays blank inside the queue region (density ceiling: three rows) |

The min chain fixes the web mirror's region gap at 4 px and the card padding at 12 px (10 at ≤ 435) because its `ListItem` rows are 64 px, not 60; the runtime build with 60 dp rows regains the 8 dp gap.

### Layout

Root fills the window edge-to-edge (`fill_window`); a 16 dp inset is owned by the inner content column; the 8 dp vertical gap between regions is owned by that column; 16 dp inner padding is owned by the Perch card; ListItems own their own 60 dp rows. Single primary focus: the Perch card. Density ceiling: one card, two (max three) queue rows, one quick-add row, one wire row.

```
Perch — 720 × 440 dp · FOCUSING · root = system Material.Regular glass · corner 32
┌──────────────────────────────────────────────────────────────────────┐
│ 16                                                                   │
│ ╭ Perch card · paper #F4ECDC · +16 dp · corner 24 ─────────────────╮ │
│ │ 24:31        Draft Q3 hiring plan                [❚❚] [✓] [⋯]   │ │ 72  Display 40 clock · Headline 20 title (≤2 lines) · 3× IconButton 56
│ │ left of 45 · block 2 of 4 · began 09:40      (+10 min chip ≤5m) │ │ 20  Label 12 meta
│ │ ████████████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │ │  8  LinearProgressIndicator "the branch"
│ ╰──────────────────────────────────────────────────────────────────╯ │
│ 8                                                                    │
│  ☐ Reply to Mei about the dataset licence                    [⤒]    │ 60  ListItem · Checkbox · IconButton "perch next"
│  ☐ Review ArUco fisheye notes                       (+4)     [⤒]    │ 60  ListItem · NumberBadge overflow
│  [ Add a task, press Enter                              ] [ Add ]   │ 56  TextField + Button
│ 8 ───────────────────────── Divider ────────────────────────────── 8 │ 17
│  ●●●○○   Today · 3 blocks · 1 h 45 m focused · 4 landed       Log › │ 60  ListItem · DotBadge ×N · Label · Link
│ 16                                                                   │
└──────────────────────────────────────────────────────────────────────┘

Perch card · ROOSTING                                   Perch card · LANDING (+24 dp, paperBlush)
│ Perch next   Reply to Mei about the…   [25|45|90] [⋯]│   │ 👁 Eyes off the screen                    5:00        │
│ [ ▶  Start 45 min                                  ] │   │ Look far, into the room.  [Start break · 12] [Skip → next] │
│ ░░░░░░░░░░░░░░░░░ (empty branch outline) ░░░░░░░░░░ │   │ ████████████████████████████ (drains right→left)      │
```

### State graph

| State | Primary task | Primary focus | Components | Data deps | Entry/exit |
| ----- | ------------ | ------------- | ---------- | --------- | ---------- |
| Roosting | pick what to perch on, choose 25/45/90, Start | Start button + perched title; empty branch outline | SegmentControl, IconButton ⋯ (SpatialPopup with three NumberFields), Button Start, queue ListItems, TextField | tasks, settings | enter: launch, break finished/skipped, block finished early; exit: Start → Focusing |
| Empty (Roosting variant) | capture the first task | quick-add field; card reads "Nothing perched" | TextField + Button, coachmark on first run | tasks == ∅ | enter: no open tasks; exit: task added → Roosting |
| Focusing | glance; pause / land / extend | clock digits + branch | Text clock, LinearProgressIndicator, IconButton ×3, ButtonChip +10 (last 5 min), Menu | timer (1 s tick), perched task | enter: Start / Resume / Skip→next; exit: Pause → Paused; 0:00 → Landing; ⋯ Finish early (AlertDialog) → Roosting |
| Paused | resume or finish | ❚❚ glyph + "Paused · n min" | same card, IconButton Resume | timer | enter: Pause; exit: Resume → Focusing; Finish early → Roosting |
| Landing | decide: rest now or skip | lifted blush card, eye icon, 5:00, 20 s auto-start | Icon, Text, Button ×2 | break settings, next task | enter: clock 0:00; exit: Start break / auto → Resting; Skip → Focusing (next task) or Roosting (empty) |
| Resting | rest the eyes | draining robin branch + countdown | Text, LinearProgressIndicator, Button "Back early" | break timer | enter: Landing; exit: 0:00 or Back early → Roosting (next task pre-selected) |
| Tallying | review today | tally line + landed list | Sheet, SegmentControl Today/Yesterday/7 days, ListItem list, ScrollIndicator | sessions, tasks | enter: day wire row / Log link; exit: dismiss (timer keeps running underneath) |

| Transition | From → To | Trigger | Explicit confirm? |
| ---------- | --------- | ------- | ----------------- |
| Start | Roosting → Focusing | Start button | no |
| Pause | Focusing → Paused | IconButton ❚❚ | no |
| Resume | Paused → Focusing | IconButton ▶ | no |
| Land task | Focusing → Focusing (next task hops up) / Roosting-Empty | IconButton ✓ | no; Snackbar "Landed · Undo" 4 s |
| Extend | Focusing/Paused → same | ButtonChip +10 min / Menu +10 min | no |
| Finish early | Focusing/Paused → Roosting | Menu "Finish block early" | **yes** — AlertDialog "End this block early? 18 min will be logged." |
| Block ends | Focusing → Landing | timer reaches 0 | no (lift + chime) |
| Start break / auto | Landing → Resting | Button or 20 s auto | no |
| Skip break | Landing → Focusing / Roosting | Button "Skip → next" | no |
| Back early / break ends | Resting → Roosting | Button / timer 0 | no |
| Perch next | any → same | IconButton ⤒ on a queue row | no |
| Add task | any → same | TextField Enter / Add button | no |
| Delete task | any → same | Menu on row → "Delete" | **yes** — AlertDialog "Delete this task?" |
| Open log | any → Tallying | day wire row / Link | no |
| Change durations | Roosting → same | SpatialPopup NumberField ×3 → Button "Use" | no |

### Components

- **Perch card** — task: T2/T3/T4 hub. Data: timer state + perched task. Variants: Roosting (picker + Start), Focusing, Paused, Landing (blush, +24), Resting. Custom container (paper surface + zOffset); every control inside is a SpatialUI built-in.
- **Clock** — `Text` Display 40 (Display 32 < 640 dp). Data: remaining seconds. Variants: focus / paused / break.
- **Perched title** — `Text` Headline 20, max 2 lines. Data: next open task.
- **Meta line** — `Text` Label 12: "left of 45 · block 2 of 4 · began 09:40" / "Paused · 3 min". Data: plan, session log.
- **Block controls** — `IconButton` ×3: Pause/Resume, Land (✓), More (⋯ → `Menu`: +10 min, Finish block early, Switch perched task, Durations).
- **Extend chip** — `ButtonChip` "+10 min", visible when remaining ≤ 5 min.
- **Branch** — `LinearProgressIndicator` 12 dp: fills ochre (focus) / drains robin (break) / empty outline (roosting).
- **Duration picker** — `SegmentControl` + `SegmentItem` 25 | 45 | 90; `IconButton` ⋯ → `SpatialPopup` with three `NumberField`s — focus 5–180 by 5, break 1–60 by 1, daily goal 30–600 by 15 (each `NumberField` clamps to its own `min`/`max`; no `Stepper`) — and a `Button` "Use". Bounds are enforced by the component, so "Use" never receives an out-of-range value.
- **Start** — `Button` primary (interaction ochre) "Start 45 min".
- **Queue rows** — `ListItem` ×2 (×1 ≤ 435 dp tall, ×3 ≥ 528 dp tall; see the height chains above): leading `Checkbox` (land directly), trailing `IconButton` ⤒ (perch next) + `IconButton` ⋯ (`Menu`: move up/down, delete). Data: open tasks minus the perched one, user order.
- **Overflow badge** — `NumberBadge` "+n" on the last row → opens the Sheet (Queue tab).
- **Quick add** — `TextField` (placeholder "Add a task, press Enter", imeAction Done) + `Button` "Add".
- **Divider** — `Divider`.
- **Day wire** — `ListItem` 60 dp whose leading area is the **time axis**: a custom horizontal track (domain-specific visualization) spanning the work hours (settings, default 9:00–18:00, auto-extended when now falls outside), one rounded block per finished focus block at its start time (moss = completed, moss outline = stopped early, ochre = running, robin tick = break), width = minutes × scale with a 56 dp hit floor, a graphite "now" marker; headline `Text` Label 12 tally "3 blocks · 1 h 45 m focused · 4 landed", a short `LinearProgressIndicator` (focus minutes / daily goal), trailing `Link` "Log".
- **Break card content** — `Icon` eye + `Text` Headline 20 "Eyes off the screen" + `Text` Display 40 countdown + `Text` Body 14 "Look far, into the room" + `ToggleableChip` "Land ‹task›" + `Button` ×2 ("Start break · 12" / "Skip → next", or "Back early" while resting).
- **Empty state** — when no open task exists the Perch card holds the quick-add `TextField` with the prompt "What's the one thing?" and the first-run `CoachmarkBox`.
- **Daily goal** — `LinearProgressIndicator` in the tally row; goal minutes set in the durations popup (the third `NumberField` of the same popup, 30–600 by 15, default 180).
- **Log sheet** — `Sheet` with `SegmentControl` Today | Yesterday | 7 days, `Text` Title, `ListItem` list, `ScrollIndicator`.
- **Undo snackbar** — `SnackbarHost` (PicoTheme provides the host state).
- **First-run coachmark** — `CoachmarkBox` + `SimpleCoachmark` on the clock: "Park me under your monitor. I stay put."
- **Confirmations** — `AlertDialog` for Finish early and Delete task.

### SpatialUI Web component map

| Planned element | SpatialUI tag | API checked in | Public event/state | Custom fallback reason |
| --------------- | ------------- | -------------- | ------------------ | ---------------------- |
| Clock / titles / meta / hints | `sui-text` | `js/text.js` | `variant`, `color` | n/a |
| Block controls, perch-next, row more | `sui-icon-button` | `js/icon-button.js` | `click-action` | n/a |
| Start, Add, break buttons, dialog buttons | `sui-button` | `js/button.js` | `click-action` | n/a |
| +10 min | `sui-button-chip` | `js/chip.js` | `click-action` | n/a |
| Branch (focus / break) | `sui-linear-progress` | `js/progress.js` | `value` | n/a |
| Duration picker | `sui-segment-control` (+ `sui-segment-item`) | `js/segment-control.js` | `select` / `selected-index` | n/a |
| Custom durations popup | `sui-spatial-popup` | `js/spatial-popup.js` | `dismiss-request` | n/a |
| Queue rows, day wire row, log rows | `sui-list-item` | `js/list-item.js` | slots + `click-action` | n/a |
| Land directly | `sui-checkbox` | `js/checkbox.js` | `checked-change` | n/a |
| Overflow count | `sui-number-badge` | `js/badge.js` | `number` | n/a |
| Day wire "now" marker | `sui-dot-badge` | `js/badge.js` | `color` | n/a |
| Day wire time axis (blocks at clock positions) | custom `div` track | — | — | domain-specific visualization: duration-as-length on a work-hours axis has no `sui-*` control equivalent |
| Daily goal bar | `sui-linear-progress` | `js/progress.js` | `value` | n/a |
| Land task at block end | `sui-toggleable-chip` | `js/chip.js` | `click-action` / `selected` | n/a |
| Custom durations + daily goal | `sui-number-field` ×3 (`min`/`max`/`step` = 5/180/5, 1/60/1, 30/600/15) inside `sui-spatial-popup` | `js/number-field.js` | `value-change` | n/a |
| Quick add | `sui-text-field` | `js/text-field.js` | `value-change`, Enter | n/a |
| Divider | `sui-divider` | `js/divider.js` | — | n/a |
| Log link | `sui-link` | `js/link.js` | `click-action` | n/a |
| Log sheet | `sui-sheet` | `js/dialogs.js` | `dismiss-request` | n/a |
| More menu / row menu | `sui-menu` + `sui-menu-item` | `js/menu.js` | `select` / `click-action` | n/a |
| Undo / logged feedback | `sui-snackbar-host` | `js/feedback.js` | `show()` | n/a |
| First-run hint | `sui-coachmark` | `js/window-components.js` | `dismiss` | n/a |
| Finish early / delete confirm | `sui-alert-dialog` | `js/dialogs.js` | `dismiss-request` + button `click-action` | n/a |
| Eye icon, state glyphs | `sui-icon` | `js/icon.js` | — | n/a |
| Perch card surface | custom `div` | — | — | domain-specific: an opaque paper surface with zOffset has no SpatialUI control equivalent; every control inside it is a `sui-*` element |

## 4 · Critique (before build)

**Generic-default simulation** — a lazy prompt would produce a near-black glass dashboard with a teal accent: a big circular timer in the middle, a to-do list on the left, a stats card on the right, a bottom toolbar with Start/Stop, and a settings gear — a scaled-up phone Pomodoro app with three equal tiles.

**Where the plan risked that default, and what I changed** — (1) The first draft of the queue had its own "card"; removed so the backlog sits on bare glass and the Perch card is the only surface, keeping a single focus. (2) The ring was tempting (three of four directions used one); kept the linear branch because it belongs to the metaphor, spans the full card so it reads peripherally, and doubles as the break drain. (3) Color: replaced the acid accent with a palette from the desk (paper, graphite, 9 a.m. ochre, moss, robin) and gave each color exactly one meaning. (4) Dropped the Toolbar/Subwindow reflex: controls belong on the same lifted plane as the clock.

**Hard checks**

| Check | Pass? | Evidence |
| ----- | ----- | -------- |
| ≥3 real alternatives with rejection reasons | yes | §2: A/B/C/D differ in size (720 → 1600 dp), attachments (none / Subwindow / Toolbar), IA (stack / instrument+tree / day track / depth stack); rejections cite each proposal's own numbers |
| Every core task has a 2D counterfactual | yes | §1 table, five rows |
| Form/scale follows the spatial justification (not habit) | yes | glance job → compact Planar strip; no wall, no volumetric, no Stage |
| Every window/surface size was internally validated | yes | §3 sizing chain (FOV, hit floor, height budget) |
| Attachments justified vs None/in-place | yes | §3 components: None chosen; Toolbar/Subwindow/TabBar/Augment each rejected with a reason in Direction A's proposal |
| Domain-swap test (design would change) | yes | swap to "meeting timer" and the branch/bird, day wire, eye-rest lift and paper palette all stop making sense |
| Every core component has data source + task | yes | §3 components |
| All 16 `ColorScheme` roles are explicitly defined | yes | §3 visual tokens |
| Every standard control/system surface maps to SpatialUI Web | yes | §3 map; one custom container only |
| Single primary focus / clear decision | yes | the Perch card; the decision is "start this block now" |

## 5 · Build — coverage manifest

Prototype: `design/preview.html` (one self-contained file, vendored SpatialUI Web bundle inlined byte-for-byte, scope `web_design_validation_only`). Root = `#app` on the library's `Material.Regular` glass (`--Background-MaterialRegular` + its blur), corner 32, default 720×440, resizable 560×360 … 960×720 with container-query reflow (clock Display 40 → 32 and goal bar under the tally below 640 px; one queue row ≤ 435 px tall, two by default, three at ≥ 528 px, region gap 12 at ≥ 600 px, wider inset at ≥ 860 × 464; every band's chain is recorded in §3 and was re-measured at 560×360, 720×360, 960×360, 560×436, 720×440, 960×464, 720×528, 960×528, 960×720 in Roosting, Focusing and Landing with no clipped row). Theme: `PicoTheme.install({ scheme: "vibrant", colorScheme })` with all 16 roles forwarded from `vibrantColorScheme()` and `error #B3402B`, `alert #C4623F`, `passable #6F8B6A`, `interaction #D89B3C` replaced. Verified in the desktop browser pane: every state, transition, dialog and data mode below was triggered and observed; no console errors.

**Review hooks (console only, no visible controls)** — `window.__perch.setData('normal'|'fallback'|'error')`, `window.__perch.go('Empty'|'Roosting'|'Focusing'|'Paused'|'Landing'|'Resting'|'Tallying')`, `window.__perch.fastForward(seconds)`, `window.__perch.snapshot()` (also `.state`), `window.__perch.coachmark(true|false)`. Any user action can be replayed by dispatching the component's public `CustomEvent` on its stable id, e.g. `document.querySelector('#btn-land').dispatchEvent(new CustomEvent('click-action', { bubbles: true, composed: true }))`; `document.querySelector('#snackbar').dismiss('action-performed')` fires Undo. Resize with the frame handle or `#app.style.width/height`.

| Kind | Design fact (§3) | Implemented where |
| ---- | ---------------- | ----------------- |
| state | Empty | `#app[data-state="empty"]`, `#perch-card[data-variant="empty"] .v-empty` (quick-add moves into the card, `#coachmark` on first run) |
| state | Roosting | `#app[data-state="roosting"]`, `.v-roosting` (`#duration-picker`, `#btn-durations`, `#btn-start`, empty `#branch`) |
| state | Focusing | `#app[data-state="focusing"]`, `.v-running` (`#clock`, `#perched-title`, `#block-controls`, `#state-chip`, `#meta`, `#extend-chip`, ochre `#branch`); queue rows recede via `--queue-ink` |
| state | Paused | `#app[data-state="paused"]` (play glyph, "Paused · n min", colon stops) |
| state | Landing | `#app[data-state="landing"]`, `.v-break` (blush card, `#break-icon`, "Eyes off the screen", `#break-clock` 5:00, `#btn-break-start` "Start break · n", `#btn-break-skip`, `#chip-land-break`, full robin `#branch`, `.pulse` 300 ms + chime, 20 s auto-start) |
| state | Resting | `#app[data-state="resting"]` ("Resting", draining robin `#branch`, `#btn-break-back`) |
| state | Tallying | `#app[data-overlay="tallying"]`, `#log-sheet` (timer keeps running underneath) |
| transition | Start | `#btn-start` `click-action` → Focusing |
| transition | Pause / Resume | `#btn-pause` `click-action` → Paused ⇄ Focusing |
| transition | Land task | `#btn-land` `click-action` or `#queue .row-land` `checked-change` → next task hops up (200 ms title crossfade, moss dot on `#day-wire-track`) or Empty; `#snackbar` "Landed · task · Undo" 4 s |
| transition | Extend | `#extend-chip` `click-action` (visible when remaining ≤ 5 min) or `#more-menu [value=extend]` `select` → +10 min |
| transition | Finish early (confirm) | `#more-menu [value=finish]` → `#dialog-finish` "End this block early? n min will be logged." → `#dialog-finish-confirm` → Roosting; `#dialog-finish-cancel` / `dismiss-request` keep going |
| transition | Block ends | clock reaches 0 → Landing (lift pulse + chime) |
| transition | Start break / auto | `#btn-break-start` `click-action` or 20 s countdown → Resting |
| transition | Skip break | `#btn-break-skip` → Focusing (next block) or Empty |
| transition | Back early / break ends | `#btn-break-back` or break clock 0 → Roosting with next task pre-selected (or Empty) |
| transition | Perch next | `#queue .perch-next` `click-action` → perched (Roosting) / next in line (running) |
| transition | Add task | `#quick-add-field` Enter or `#quick-add-button`; Empty: `#quick-add-empty-field` / `#quick-add-empty-button` |
| transition | Delete task (confirm) | `#queue .row-more` → `#row-menu [value=delete]` → `#dialog-delete` → `#dialog-delete-confirm`; Undo snackbar |
| transition | Open log | `#day-wire` / `#log-link` `click-action`, `#queue .overflow` click (Queue tab) → Tallying; `#btn-log-close` / `dismiss-request` closes |
| transition | Change durations | `#btn-durations` or `#more-menu [value=durations]` → `#durations-popup` (`#nf-focus` 5–180/5, `#nf-break` 1–60/1, `#nf-goal` 30–600/15 `value-change`) → `#btn-durations-use`; `dismiss-request` discards |
| transition | Land at block end | `#chip-land-break` `checked-change {selected:true}` → landed + Undo; the chip is driven from state (`S.breakLanded`): unselected on every Landing / Resting entry and after Undo, selected + “Landed ‹task›” while the landing can still be undone, `{selected:false}` on the selected chip un-lands (same as Undo) |
| transition | Switch perched / move up / down | `#more-menu [value=switch]`, `#row-menu [value=up|down]` |
| transition | First-run coachmark | `#coachmark` shown at boot and on entering Empty while first-run; `#coachmark-dismiss` `click-action` hides |
| component | Perch card | `#perch-card` custom paper surface (`#F4ECDC` / blush `#F6E0D6`, 24 px, +16 dp lift as shadow + translate, landing +24 dp) — every control inside is `sui-*` |
| component | Clock / meta / titles | `#clock .clock-min/.colon/.clock-sec` (`sui-text displayLarge`, `.compact` = `displayMedium`), `#perched-title`, `#meta`, `#state-chip` |
| component | Branch | `#branch` `sui-linear-progress` (ochre fill / robin drain / ochre-track outline), host scaled to the card width |
| component | Duration picker + Start | `#duration-picker` `sui-segment-control` 25 / 45 / 90, `#btn-durations`, `#btn-start` "Start n min" |
| component | Queue rows + overflow | `#queue-row-1..3` `sui-list-item` with `.row-land` `sui-checkbox`, `.perch-next` / `.row-more` `sui-icon-button`, `.overflow` `sui-number-badge` |
| component | Quick add + Divider | `#quick-add` (`sui-text-field` + `sui-button`), `sui-divider` |
| component | Day wire | `#day-wire` `sui-list-item`; `#day-wire-track` custom axis (hour ticks, `.wire-block.done / .early / .running / .break`, `.wire-dot`), `#now-marker` `sui-dot-badge`, `#tally`, `#goal-bar`, `#log-link` |
| component | Log sheet | `#log-sheet` `sui-sheet` with `#log-range` (Today / Yesterday / 7 days / Queue), `#log-summary`, `#log-list`, `#log-scroll`; rows rebuild only when their content signature changes (the 1 s tick underneath keeps the scroll position; a range switch resets to top) |
| component | Overlays | `#more-menu`, `#row-menu` (`sui-menu` / `sui-menu-item`), `#durations-popup` (`sui-spatial-popup`), `#dialog-finish`, `#dialog-delete` (`sui-alert-dialog`), `#snackbar` (`sui-snackbar-host`), `#coachmark` (`sui-coachmark`) |
| binding | tasks (perched / queue) | normal: 1 perched + 6 queued ("+4" overflow); fallback: none → Empty, `#app[data-stale]`, hint "last landed yesterday 17:20"; error: `#app[data-error]`, "Tasks unavailable", disabled quick-add, `#queue-hint` + `#queue-retry`, snackbar "Couldn't load your tasks" |
| binding | sessions today | normal: 3 blocks · 1 h 45 m · 2 breaks on the wire; fallback: none today (yesterday in Log) → "Today · nothing yet · last block yesterday"; error → "Today · unavailable" |
| binding | landed today | normal 4 (moss dots), fallback 0, error 0 |
| binding | timer / break / landing countdown | 1 s preview tick, `fastForward` hook; extend, pause minutes, 20 s auto-start |
| binding | settings + work-hours axis | 45 / 5 / 180, 9:00–18:00; axis auto-extends (fallback clock 08:12 → 8:00 start) |
| binding | daily goal | `#goal-bar` = focus minutes / goal (105 / 180 → 0.58) |

**Known deviations (web-mirror limits, recorded for the runtime build)** — `sui-linear-progress` exposes 4 / 8 px only (design: 12 dp branch) and a fixed 240 px track, so the host is scaled to the card width; `sui-list-item` fixed 16 px padding makes queue rows 64 px (design 60) with `min` icon buttons; the "now" marker uses `labelPrimary` on the dark preview room (design: graphite); the log sheet gained a "Queue" segment as the overflow-badge target; paper and Thick-glass ink use the documented `--sui-*` subtree override (the 16-role scheme itself is untouched).

## 6 · Critique again

Reviewed by a fresh-context reviewer (no memory of how the design or the prototype was produced) per `review-guide.md`, three times: once after the initial build and once after each patch round. The reviewer rebuilt the coverage denominator from §3 only and traced every public-event listener in the app script; it also ran `node --check` on the extracted app script and on the inlined bundle (both OK).

**Independent coverage rebuild** — states 7/7 (Empty, Roosting, Focusing, Paused, Landing, Resting, Tallying) reachable by user action and by `window.__perch.go(...)`; transitions 22/22 (15 table rows + 7 implied by the state table / components) traced to `click-action` / `checked-change` / `select` / `value-change` / `dismiss-request` listeners on stable ids; components 21/21 and card variants 5/5 located in the markup or the `renderQueue` / `renderSheet` templates; data bindings 6/6 with normal / fallback / error samples reachable through the console hooks. Diff vs the generator's §5 manifest after the patch rounds: none blocking; three minor notes were recorded (log sheet is close-button-only unless `dismiss-on-click-outside` is set; undo of a landing that closed the running block returns to Roosting rather than restoring the block; three §3 map rows named a convenience event rather than the component's documented one — corrected in §3).

**Quality bar**

| Criterion | Met? | Evidence |
| --------- | ---- | -------- |
| App's job is a clear decision/task outcome | yes | one card, one queue, one wire; the decision is "start this block now" (§1) |
| Form/scale follows the spatial justification | yes | glance job → 720×440 Planar strip under the monitor; no attachments (§1, §3) |
| Spatialization justified affordance-by-affordance | yes | §1 table T1–T5 with concrete 2D counterfactuals |
| ≥3 alternatives compared, rejects have reasons | yes | §2 A–D, two judges, rejections cite each proposal's own numbers |
| Every window/surface records final default/min/max dimensions | yes | §3 sizing table; nine size bands re-measured in the prototype with no clipped row (§5) |
| Design derives from domain (survives domain-swap) | yes | branch-and-bird, paper, 9 a.m. ochre / moss / robin with one meaning each, work-hours day wire |
| SpatialUI bundle is inline; mapped tags are registered, present, and event-wired | yes | single bundle `<script>`, 68 `define()`s, 25 `sui-*` tags used, all wired through public events |
| No unjustified native/hand-rolled duplicate of an available `sui-*` control | yes | no `<button>/<input>/<select>/<dialog>` in app markup; confirmations are `sui-alert-dialog` |
| Root surface uses SpatialUI Web `vibrant` + `Material.Regular` glass | yes | `#app` uses the bundle's `.surface.material-regular` variables; `PicoTheme.install` forwards all 16 roles with only the 4 §3 overrides |
| No design/debug-only controls appear in the product UI | yes | data modes and state jumps are console hooks only |
| preview.html is a triggerable state machine, full coverage | yes | see coverage rebuild |
| Signature present, surroundings quiet | yes | only `#perch-card` is a surface; queue rows on bare glass with transparent containers |

**Patch log** (max 3 rounds)

| Round | Problem | Target | Expected improvement | Re-ran Build+critique? |
| ----- | ------- | ------ | -------------------- | ---------------------- |
| 1 | `#extend-chip[hidden]` still rendered because the component's host CSS overrode `hidden` | Focusing / Paused extend chip | "+10 min" appears only when remaining ≤ 5 min, as §3 states | yes |
| 2 | Recorded min size 560×360 and the ≥500 dp third-row rule did not hold their content (rows clipped); log list rebuilt every second (scroll reset); Land chip selected state not driven from state | Surface sizing / container tiers; `#log-list`; `#chip-land-break` | Every recorded size holds its content (one row ≤ 435 px, three at ≥ 528 px); sheet rows rebuild only on content change; chip reflects `S.breakLanded` | yes |
| 3 | §3 named "two Steppers" in the surface table while the components list and prototype use three `NumberField`s | design-doc §3 consistency | One component story (NumberField ×3 with min/max/step) | yes |

**Device validation** — `not_performed` (Web validates logic/layout only; real-device comfort, occlusion, physical size, performance not verified). The runtime build is validated separately on the PICO 6.1 emulator (see `.scratch/shots/`).

**Verdict** — **pass**. No blocking finding after round 3; the three remaining notes are minor and recorded above for the runtime implementation.

## 7 · Runtime deviations (implementation record, after the design verdict)

The design above stays the reference; these are the places where the Kotlin build differs, each found on the PICO 6.1 emulator.

| Date | Design | Runtime | Why |
| --- | --- | --- | --- |
| 2026-10-01 | Default 720 × 440, two queue rows | Default 720 × 480 | The built Roosting card measures about 164 dp (SegmentControl row + Start button), not 141, so 440 held one row. |
| 2026-10-01 | Min 560 × 360 with a compact tier (inset 12, card padding 10) | Min 560 × 400, no compact tier | The compact tier is not built; 400 is the smallest height that holds the card, one 64 dp queue row, quick-add and the day wire. |
| 2026-10-01 | Queue row count by height tier (≤ 435 one, default two, ≥ 528 three) | Row count computed from the measured space between the card and the day wire (1–4 rows of 64 dp + 4 dp gaps) | Card height differs per state; fixed tiers squeezed the day wire in Focusing. |
| 2026-10-01 | Overflow badge on the last row | "+n more" chip on the last row opens a queue face in the log sheet | The badge was not actionable; hidden tasks were unreachable. |
| 2026-10-01 | Day wire axis without labels | Hour labels under the ticks (every hour, or every 2–3 when narrow) | Bars had no time reference at a glance. |
