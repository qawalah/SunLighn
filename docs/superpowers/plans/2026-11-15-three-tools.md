# SunLighn 3.0 (three tools) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn SunLighn from "one map, one sheet" into a Home screen with three step-by-step tools (Line up a street, Where to stand, Build the shot) that all end on a shared Your shot step, with Back stepping back, sun and moon separated, dropdowns instead of pills, Move in the viewfinder and one-press saving named by place.

**Architecture:** One activity keeps the map and the two points. A plain-Java `Flow` (tool, body, step) decides what the sheet shows and what Back does. Home is a full-screen view over the map. Steps are rendered from `MainActivity`, grouped per tool (see the deviation note in Task 3). New plain-Java `Sky` works out the impossible sky for Build the shot. `Shots` replaces `Shoots` and saved lines.

**Tech Stack:** Java 17, Android SDK 35 (min 24), osmdroid, custom views, JUnit 4 + Robolectric 4 (`ScreensTest`). No new libraries.

**Spec:** `docs/superpowers/specs/2026-11-15-three-tools-design.md`

## Global Constraints

- `applicationId` unchanged. `versionCode 12`, `versionName "3.0.0"` in `app/build.gradle`.
- No release `signingConfig`, no keystores. Keep `gradlew`, `gradlew.bat`, `gradle/wrapper/*`.
- No new libraries or services. Nominatim: one request per user action, never while typing, the existing User-Agent.
- No pills: `Ui.chip` is deleted. Picks from a list use `Ui.dropdown`. Either/or stays `Ui.segmented`, tabs stay `Ui.tabs`.
- Words from the spec's Words section only: Home, Line up a street, Where to stand, Build the shot, Your shot, Shot, Move, Directions, You, Subject, Line, Lined up, Behind subject / Behind you, Soonest, Low sun / Low moon, Times, Day, Viewfinder, Reminder, Saved, Trip, On foot / Drone.
- Back never deletes a placed point or a setting. Only Home leaves the app.
- Everything saved as you go (prefs), so Back never asks.
- Build check: `./gradlew --max-workers=1 testDebugUnitTest assembleRelease` with `sdk.dir` in `local.properties` (git-ignored).

## Review Focus

1. **Process death / restart mid-tool:** reopening after the app was killed lands on Home with Continue, and Continue returns to the same tool and step with the points. Test: `ScreensTest.restartOffersContinue`.
2. **Switching body inside a tool** (step bar `Sun ▾` → Moon) on the spots or Your shot step: answers recompute for the moon, no sun row remains, the step does not change. Test: `ScreensTest.switchBodyInsideATool`.
3. **No network on save:** the shot is saved as "Shot N" at once and keeps that name; it is not lost or blocked. Test: `ShotsTest.unnamedShotKeepsNumberName` + `ShotsTest.nextNumberName`.
4. **Old data from 2.1.0:** saved lines and planned shoots appear in Shots after upgrade and reminders keep their keys. Test: `ShotsTest.migratesLinesAndShoots`.
5. **Back pressed fast through every step and overlay** (dropdown open, Move panel open, viewfinder from Your shot): each press goes one level. Test: `ScreensTest.backStepsThroughEveryTool`.

---

### Task 1: `Flow`, the route model

**Files:**
- Create: `app/src/main/java/dev/qawalah/sunlighn/Flow.java`
- Test: `app/src/test/java/dev/qawalah/sunlighn/FlowTest.java`

**Interfaces:**
- Produces:
  - `final class Flow { static final int HOME = 0, LINE = 1, STAND = 2, BUILD = 3; int tool; boolean moon; int step; }`
  - Step ids (constants on `Flow`): `YOU = 0, SUBJECT = 1, WHEN = 2, CRITERIA = 3, SPOTS = 4, COMPOSE = 5, SHOT = 6`.
  - `static int[] steps(int tool)`: LINE → `{YOU, SUBJECT, WHEN, SHOT}`, STAND → `{SUBJECT, CRITERIA, SPOTS, SHOT}`, BUILD → `{SUBJECT, COMPOSE, SHOT}`, HOME → `{}`.
  - `int current()` (step id at `step`), `int count()`, `void start(int tool)` (step 0), `boolean next()`, `void go(int stepId)`.
  - `boolean back()`: moves one step back; from step 0 sets `tool = HOME` and returns true; on HOME returns false (caller leaves the app).
  - `String encode()` / `static Flow decode(String s)`: `"tool,moon,step"`; garbage or null → HOME, sun.
  - `static String title(int stepId, boolean moon)`: YOU "Where you stand", SUBJECT "Your subject", WHEN "When", CRITERIA "How you want it", SPOTS "Where to stand", COMPOSE "Build it", SHOT "Your shot".
  - `static String toolName(int tool)`: "Line up a street", "Where to stand", "Build the shot".

- [ ] **Step 1: Write the failing tests** in `FlowTest`:
  - `backStepsThenHomeThenLeaves`: `start(STAND)`, `next()` ×2 → `current()==SPOTS`; `back()` → CRITERIA; `back()` → SUBJECT; `back()` returns true and `tool==HOME`; `back()` returns false.
  - `stepsPerTool`: `steps(LINE)` equals `{0,1,2,6}`, `steps(BUILD)` equals `{1,5,6}`.
  - `nextStopsAtLast`: on BUILD at SHOT, `next()` returns false, `current()==SHOT`.
  - `encodeRoundTrip`: `{tool=LINE, moon=true, step=2}` survives `decode(encode())`; `decode("x")` and `decode(null)` give HOME with `moon=false`; `decode("2,0,9")` clamps step to the last index.
  - `goJumpsToStep`: `start(LINE)`, `go(SHOT)` → `step==3`.
- [ ] **Step 2: Run** `./gradlew --max-workers=1 testDebugUnitTest --tests '*FlowTest'`. Expected: compile failure (no `Flow`).
- [ ] **Step 3: Implement `Flow`** as specified.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `Flow: the route through each tool`.

### Task 2: `Ui.dropdown` + `PickSheet`, pills gone

**Files:**
- Create: `app/src/main/java/dev/qawalah/sunlighn/PickSheet.java`
- Modify: `Ui.java` (add `dropdown`, delete `chip`), `MainActivity.java:905-925` (light kinds), `ComposeScreen.java:234-250, 323-336` (kinds, runs), `HeightDialog.java:38` (kinds), `SettingsSheet.java:97,139` and accent swatches (theme, map look, accent)
- Test: `ScreensTest` (extend)

**Interfaces:**
- Produces:
  - `static LinearLayout Ui.dropdown(Context c, Palette p, String label, String[] options, int selected, IntConsumer onPick)`: bordered field, label (12.5 sp, muted) above, value (15 sp, 600) and a chevron (`ic_chevron_down.xml`, CrossPaths path, 1.8 stroke). Content description `label + ": " + options[selected]`. Tap opens a `PickSheet`.
  - Overload with `int[] swatches` (accent): each row shows a 16 dp colour dot before the name.
  - `final class PickSheet extends FrameLayout`: a dim scrim plus a bottom panel listing options, a tick on the selected one, a 48 dp row height. `static PickSheet open(Activity a, Palette p, String title, String[] options, int[] swatches, int selected, IntConsumer onPick)` adds itself to `android.R.id.content`. `static boolean closeTop(Activity a)` removes an open one and returns true (used by Back).
  - Accent names: Teal, Blue, Violet, Amber, Rose. Check the five `ACCENT_DAY` colours in `Ui` and fix the names if they differ.

- [ ] **Step 1: Write the failing test** `ScreensTest.dropdownsReplacePills`: open Settings; assert `byDescription("Theme: System")` exists; click it; assert a view with text "Dark" is visible; click "Dark"; assert prefs `theme == 2`. Add a source check test `NoPillsTest.noChipCalls`: read every file under `app/src/main/java` and assert none contains `Ui.chip(`.
- [ ] **Step 2: Run** `--tests '*ScreensTest.dropdownsReplacePills' --tests '*NoPillsTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `Ui.dropdown`, `PickSheet`, swap every chip call site, delete `Ui.chip`. Back: `PickSheet.closeTop` is checked first in Back (Task 4 wires the dispatcher; until then add it at the top of `onBackPressed`).
- [ ] **Step 4: Run** the full `testDebugUnitTest`. Expected: PASS (fix older ScreensTest clicks on chip text to use the dropdowns).
- [ ] **Step 5: Commit** `Dropdowns instead of pills everywhere`.

### Task 3: Home screen, step bar, Back through the flow

**Files:**
- Create: `app/src/main/java/dev/qawalah/sunlighn/HomeScreen.java`
- Modify: `MainActivity.java` (`onCreate`, `buildChrome` 428-505, `onBackPressed` 1772-1781 → `OnBackPressedCallback`, `loadState`/`saveState` 1922-1975, `refresh`/`buildSheet` 556-585), `Ui.java` (add `stepBar`), `Tutorial.java` (three cards: one per tool)
- Test: `ScreensTest` (extend)

**Interfaces:**
- Consumes: `Flow` (Task 1), `Ui.dropdown` (Task 2).
- Produces:
  - `HomeScreen(Context c, Ui.Palette p, Host host)`, `interface Host { void startTool(int tool); void continueTool(); void setMoon(boolean moon); void showSaved(); void showSettings(); void showHelp(); }`, `void refresh(boolean moon, String continueText)` (null hides the Continue card).
  - Home layout as the spec sketch: Sun / Moon `Ui.segmented`, optional Continue card ("Continue · <tool name>, <subject name or 'your line'>"), three numbered cards (number, title 18 sp 700, description 14 sp muted; words switch "sun"/"moon"), "How it works" text button; Saved and gear keys top right.
  - `static LinearLayout Ui.stepBar(Context c, Palette p, String text, boolean moon, Runnable back, Consumer<Boolean> setMoon)`: arrow key (content description "Back"), text `"2 of 4 · Where to stand"`, and a small `Ui.dropdown`-style key "Sun ▾" / "Moon ▾" opening a PickSheet with Sun, Moon.
  - `MainActivity` fields: `Flow flow`; prefs key `"flow"` holds `flow.encode()`; prefs key `"moon"` the Home switch.
  - Back order (callback): PickSheet → tutorial → settings → dialogs in frame/compose → frame → compose → search → saved → sheet FULL→HALF → `flow.back()` (refresh sheet, show Home when it returns HOME) → when `flow.back()` is false, `moveTaskToBack(true)` (same as system default for the root activity on Android 12+).
  - App start: always Home. Continue is offered when the saved flow's tool is not HOME; it restores that tool and step.
  - The 2.1 start sheet (`renderStart`), the "I know where I will stand" switch and the search's "It is my subject / It is where I stand" switch are removed: each step says which point it places and the search sets it.

**Deviation from the spec (Claude's call, note it in the spec in this task's commit):** step rendering stays in `MainActivity`, in one section per tool (`// ---- Line up a street ----` etc.), instead of new `LineTool`/`StandTool`/`BuildTool`/`ShotStep` classes. Those methods read a dozen fields of map state; moving them behind interfaces is a large refactor with no visible change, and it can follow once 3.0 is on the phone.

- [ ] **Step 1: Write the failing tests** in `ScreensTest`:
  - `opensOnHome`: fresh prefs (no flow) → texts "Line up a street", "Where to stand", "Build the shot" visible; shot `01-home`.
  - `backStepsThroughEveryTool`: from Home click "Where to stand" → step bar text starts "1 of 4"; tap the map to set the subject (use the existing tap helper) and press Next → "2 of 4"; Back → "1 of 4" and the subject is still set (`prefs subject_lat` present); Back → Home visible; Back → assert `shadowOf(act).isTaskMovedToBack()` is true... or `act.isFinishing()` if `moveTaskToBack` is not tracked by Robolectric.
  - `restartOffersContinue`: prefs `flow = "2,0,2"` plus subject → recreate → Home shows "Continue · Where to stand"; click it → step bar "3 of 4".
- [ ] **Step 2: Run** these tests. Expected: FAIL.
- [ ] **Step 3: Implement** Home, step bar, Flow wiring, the Back callback, the new tutorial cards. Rewrite the CLAUDE.md Back table to the spec's table.
- [ ] **Step 4: Run** full `testDebugUnitTest`. Expected: PASS (older tests now start from Home: give their `setUp` a saved flow `"1,0,2"` so they open on the When step via Continue, or click Continue first).
- [ ] **Step 5: Commit** `Home with three tools; Back steps back`.

### Task 4: Line up a street, one body at a time

**Files:**
- Modify: `MainActivity.java` (`renderLine` 1143-1193, `renderTimes` 1194-1219, `addSection` 1259-1277, `renderDay` 1416-1457, `compute` 1098-1124, `LineOverlay.drawDay`), `DayChart.java` (draw one body)
- Test: `ScreensTest`

**Interfaces:**
- Consumes: `Flow.moon`, step ids YOU, SUBJECT, WHEN.
- Produces: `private void renderStep()` in MainActivity: switch on `flow.current()` calling `renderYouStep()`, `renderSubjectStep()`, `renderWhen()`, `renderCriteria()`, `renderSpots()`, `renderShot()` (Tasks 4–7 fill these). `buildSheet()` calls it instead of `renderStart/renderPlan/renderLine`.
- YOU step: head "Where you stand", sub "Tap the map where you will stand, use your location, or search.", keys: "Use my location" and main "Next" (enabled once You is set). SUBJECT step: head "Your subject", sub "Tap what you want to photograph: the end of the street, a building, a peak.", main "Next" (enabled once Subject is set). On the SUBJECT step of LINE, You stays draggable.
- WHEN step: headline = soonest moment of the chosen body only (`foundSun` or `foundMoon`); Behind subject / Behind you; tabs Times / Day; Times lists that body only (soonest, low, list, Pick a date, never/closest); Day draws only that body (`DayChart` gets `boolean moon`). Tapping a time row sets `shotMs` and `flow.go(SHOT)`. Main key "Use the soonest" (sets `shotMs` to the soonest).
- `compute()` only computes the chosen body (and recomputes when the body changes).
- New field `long shotMs` (prefs `"shot_ms"`), used by Your shot (Task 7).

- [ ] **Step 1: Write the failing test** `lineUpAStreetMoonOnly`: prefs You, Subject, `moon=true`, flow `"1,1,2"` → Continue → wait for "Moon behind subject"; assert no visible text starts with "Sun behind"; click Day tab; shot `04-when-day-moon`. And `switchBodyInsideATool`: in that state open the step bar body key, pick Sun → wait for "Sun behind subject"; assert step bar still "3 of 4".
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** as above.
- [ ] **Step 4: Run** full tests. Expected: PASS.
- [ ] **Step 5: Commit** `Line up a street: steps, one body at a time`.

### Task 5: Where to stand, with criteria

**Files:**
- Modify: `MainActivity.java` (`computePlan` 707-730, `renderPlan` 868-973 → `renderCriteria` + `renderSpots`, `standHere` 1026-1045), `Astro.java` (keep `SUGGEST_ALT` only as the fallback), `FrameMath.java` (add `sunOnTop`)
- Test: `FrameMathTest`, `ScreensTest`

**Interfaces:**
- Produces:
  - `static double FrameMath.targetAlt(int place, double height, double metres, boolean moon)`: place 0 On the horizon → 0.5; 1 Behind the middle → `topAngle(height, metres) / 2`, at least 0.5; 2 Just on top → `topAngle(height, metres) + 0.27` (the body's radius; same for sun and moon), at least 0.5.
  - Prefs: `"stand_place"` (default 2), `"moon_full"` (0 Any, 1 Half or more = 0.5, 2 Nearly full = 0.9; default 1), `"plan_light"` (0 setting, 1 rising), existing `"plan_dist"`, `"drone"`.
  - CRITERIA step body: Light dropdown (Sunset/Sunrise or Moonset/Moonrise), Subject dropdown (FrameMath.KINDS with usual heights, e.g. "Building · 30 m") plus a height field (number, metres; a height from the kind shows "usual height" next to it), "Where the sun sits" dropdown (word "moon" when moon), Distance slider (as now) with the line `"About %d mm to fill a third of the picture"` (focal so that the subject's height takes a third of the frame's height: `FrameMath` inverse of `fovV`, snapped with `snapFocal`), moon only "How full" dropdown, On foot / Drone. Main key "Find spots" → `flow.next()`.
  - SPOTS step: today's rows and map spots, computed with `targetAlt(...)` instead of `SUGGEST_ALT`, moon hits below the How full threshold skipped. Main key "Stand here" with the line under it "Puts you on this spot and shows your shot." → sets You, `shotMs`, `flow.go(SHOT)` (no viewfinder jump).

- [ ] **Step 1: Write the failing tests:** `FrameMathTest.targetAltPlaces`: `targetAlt(0, 30, 300, false) == 0.5`; `targetAlt(2, 30, 300, false)` ≈ `5.71 + 0.27` (±0.01); `targetAlt(1, 30, 300, false)` ≈ 2.86; `targetAlt(2, 0, 300, false) == 0.5` (height 0 gives the floor). `ScreensTest.whereToStandCriteriaThenShot`: Home → Where to stand → set subject → Next → assert dropdown "Where the sun sits: Just on top"; press "Find spots" → wait for "Stand here" → press it → step bar "4 of 4" and text "Open the viewfinder" visible; shots `05-criteria`, `06-spots`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** full tests. Expected: PASS.
- [ ] **Step 5: Commit** `Where to stand: criteria by height and where the sun sits`.

### Task 6: Build the shot, with the impossible sky

**Files:**
- Create: `app/src/main/java/dev/qawalah/sunlighn/Sky.java`
- Modify: `ComposeScreen.java` (days dropdown, bands, edge stop, Host), `FrameView.java` (draw shaded bands with a label), `MainActivity.java` (`showCompose`, `composed` → `flow.go(SHOT)` not the viewfinder; Back from compose → `flow.back()`)
- Test: `app/src/test/java/dev/qawalah/sunlighn/SkyTest.java`, `ScreensTest`

**Interfaces:**
- Produces:
  - `static double Sky.highest(boolean moon, double lat, double lon, long fromMs, int days)`: highest altitude the body reaches in those days (sample every 20 min with `Astro.sun/moon`).
  - `static boolean[] Sky.possible(boolean moon, boolean setting, double lat, double lon, long fromMs, int days, double metres, double offDeg, boolean drone, Ground.Fn ground)`: index = whole degree 0..`Sky.TOP` (40). `false` where the body never reaches that altitude in those days, or (on foot) where every distinct spot (up to 6 runs, as `ComposeScreen.solve` groups them) is `WATER` or `BUILDING`. `UNKNOWN` counts as possible. Background thread only.
  - `interface Ground.Fn { int at(double lat, double lon); }` (ComposeScreen's host already provides `ground`).
  - `void FrameView.setImpossible(boolean[] byDegree, String why)`: shades rows (accent-neutral `p.bad` at alpha 40) and writes `why` once in the top shaded band.
  - ComposeScreen: Days dropdown (Next 2 weeks = 14, Next 6 weeks = 42, Next year = 365; default 42), Runs dropdown (when 2+ runs). Dragging the body into a false degree clamps to the nearest true degree on release and calls `host.say(why)` with "The sun is never that high at sunset in these days" (body and light words match) or "Every spot for that is on water or in a building". Bands recomputed when light, days, size, lens, height or drone change (debounced like `solveLater`).

- [ ] **Step 1: Write the failing tests** in `SkyTest`: `highestSunJeddahNovember` (lat 21.5, lon 39.2, from 2026-11-15 UTC, 14 days) between 49 and 51.5 (declination about −18.5° to −21°, noon height 90 − 21.5 − dec, highest on day 0); `possibleAboveHighestIsFalse`: with a ground fn always LAND, `possible(...)[52] == false` and `[10] == true` for that place; `waterEverywhereOnFootIsFalse`: ground fn always WATER, drone false → all false; drone true → `[10] == true`; `unknownCountsAsPossible`: ground fn UNKNOWN → `[10] == true`.
- [ ] **Step 2: Run** `--tests '*SkyTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `Sky`, the shading and the clamp. `ScreensTest.buildTheShotShowsImpossibleSky`: Home → Build the shot → subject → Next → compose visible; assert a view with content description starting "Impossible:" exists after the bands are worked out; shot `07-build`.
- [ ] **Step 4: Run** full tests. Expected: PASS.
- [ ] **Step 5: Commit** `Build the shot: impossible sky shaded`.

### Task 7: Your shot, Shots, naming by place

**Files:**
- Create: `app/src/main/java/dev/qawalah/sunlighn/Shots.java` (replaces `Shoots.java`, which is deleted), `app/src/test/java/dev/qawalah/sunlighn/ShotsTest.java`
- Modify: `Places.java` (add `reverse`), `Trip.java` (tabs Shots, Trip; rename on tap; name unnamed on open), `MainActivity.java` (`renderShot`, save/remind/directions, `planShoot`, `saveLine`, `showShoot`), `Reminders.java` (unchanged keys)
- Test: `ShotsTest`, `ScreensTest`

**Interfaces:**
- Produces:
  - `final class Shots { static final class Shot { String id, name, reminderKey; boolean named, moon, portrait, remind; long timeMs /* 0 = none */; double youLat, youLon, subLat, subLon, height, focal; int mode, kind; } }`
  - `static List<Shot> Shots.all(Context)`, `static Shot add(Context, Shot)` (assigns id and `name = "Shot " + nextNumber`), `static void update(Context, Shot)`, `static void remove(Context, String id)`, `static int nextNumber(List<Shot>)`, `static void migrate(Context)` (runs once, prefs flag `"shots_v3"`: each saved `Trip.Line` → Shot with `timeMs 0`, `named true`, its name; each `Shoots` entry → Shot with `remind true`, same `reminderKey`; old keys left in place for one release).
  - `static void Places.reverse(double lat, double lon, Callback1<String> cb)`: `https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&lat=..&lon=..`; name = first of `address.suburb | neighbourhood | quarter | village | town`, then `, ` + `address.city | town | state`; null on any failure. `static String placeName(String json)` (pure, tested).
  - Shot name on save: `subjectName != null ? subjectName + ", " + city : place name`; until the answer: "Shot N", `named=false`. Trip opening with network names unnamed shots, one request each, at most 5 per opening, 1 s apart (Nominatim's limit).
  - SHOT step (spec sketch): headline `Fmt.day + " · " + Fmt.time` of `shotMs`; sub `"<Sunset|Moonrise…> behind <subject> · NN% lit"`; line "Stand <distance> <compass> of the subject"; main "Open the viewfinder"; row of three: Save (bookmark icon), Remind me (bell; saves too), Directions (`Intent.ACTION_VIEW`, `geo:lat,lon?q=lat,lon(Your spot)`; if no app handles it, `say("No maps app on this phone.")`). Save → `say("Saved as <name>", "Undo", …, "Rename", …)`.
  - Saved: tabs Shots, Trip. Shots rows: name, date and time (or "Any time"), bell lit when reminding; tap opens the line at that time on the SHOT step of LINE (`flow = LINE, SHOT`).

- [ ] **Step 1: Write the failing tests** in `ShotsTest` (Robolectric for prefs): `nextNumberName` (two shots → "Shot 3"), `unnamedShotKeepsNumberName` (add with no network → name "Shot 1", `named == false`), `placeNameFromJson` (fixture `{"address":{"suburb":"Al Shati","city":"Jeddah"}}` → "Al Shati, Jeddah"; `{"address":{"town":"Zermatt","state":"Valais"}}` → "Zermatt, Valais"; `{}` → null), `migratesLinesAndShoots` (prefs with one saved line and one shoot in the 2.1 format → two Shots, one with `remind` and the same `reminderKey`; running `migrate` again adds nothing). `ScreensTest.yourShotSaveAndBack`: flow LINE at SHOT with `shot_ms` → press Save → bar shows "Saved as Shot 1" (no network in Robolectric) → open Saved → row "Shot 1" → Back → back on Your shot; shot `08-your-shot`, `09-saved`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** Delete `Shoots.java`, move its callers to `Shots`.
- [ ] **Step 4: Run** full tests. Expected: PASS.
- [ ] **Step 5: Commit** `Your shot; Shots saved in one press and named by place`.

### Task 8: Move and Save in the viewfinder

**Files:**
- Modify: `FrameScreen.java` (keys, Move panel, Host), `MainActivity.java` (`showFrame`, host methods)
- Test: `ScreensTest`

**Interfaces:**
- Produces:
  - `FrameScreen.Host` adds `void moveYou(double lat, double lon)` (sets You, saves, recomputes; the frame gets `setNext` back as now) and `void saveShot(long ms)`.
  - Keys over the viewfinder: Back, Compass, Turn, Height, **Move**, Share, **Save** (each icon with its name under it; Move uses a new `ic_move.xml` in the CrossPaths style: four arrows from the centre).
  - Move panel: an osmdroid `MapView` in the lower third (same tile source and filter as the main map), You draggable (drawn with `Ui.drawPin`), Subject as a dot, the line between them. While dragging, `FrameView` redraws with the new bearing/distance; on release `host.moveYou`. Back or the Move key closes it. Content description "Move: drag where you stand".
- [ ] **Step 1: Write the failing test** `viewfinderMoveAndSave`: flow LINE at SHOT → "Open the viewfinder" → click `byDescription("Move")` → assert a view with description "Move: drag where you stand" → Back → panel gone, viewfinder still open → Back → Your shot. Call `act.moveYou(lat+0.001, lon)` via the host and assert prefs `you_lat` changed. Click Save → "Saved as Shot 1". Shot `10-move`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** full tests. Expected: PASS.
- [ ] **Step 5: Commit** `Viewfinder: Move where you stand, Save`.

### Task 9: Docs, version, pictures, first-use check, release

**Files:**
- Modify: `app/build.gradle` (versionCode 12, versionName "3.0.0"), `CLAUDE.md` (what it is, sheet states → tools, controls table: Dropdown row in, Pills out, Back table, Words), `LESSONS.md`, spec (deviation note, if not done in Task 3)
- Create: `AUDIT-2026-11-15-three-tools.md` (first-use findings)

- [ ] **Step 1:** Run `./gradlew --max-workers=1 testDebugUnitTest assembleRelease`. Expected: BUILD SUCCESSFUL. Look at every picture in `app/build/screens/` (Read tool) and fix anything cut off, overlapping or unreadable.
- [ ] **Step 2:** First-use check: give a Haiku agent only the new pictures and one neutral line per picture (as in `AUDIT-2026-11-15-first-use.md`). Fix what blocks the route; list the rest as open in the audit file.
- [ ] **Step 3:** Update CLAUDE.md and add lessons: "Every route ends on a screen that says what you can do next" and "Back walks back through the steps before it leaves", both `→ DevStore`, dated 2026-11-15.
- [ ] **Step 4:** Commit `SunLighn 3.0.0: Home, three tools, Your shot`, push to the session branch, then to `main` (the release). Check the Release workflow on Actions; fix and push again if it fails.
