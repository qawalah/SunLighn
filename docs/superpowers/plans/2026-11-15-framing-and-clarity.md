# SunLighn 3.3: framing and clarity, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user frame Build the shot (swing and tilt), carry that framing to the viewfinder and saved shots,
show the shot as a picture on How you want it and Your shot, fit Home on one screen, and stop "Sun" meaning Sunday.

**Architecture:** Framing is two numbers, `panDeg` (camera bearing minus the subject's bearing) and `pitch`
(degrees, NaN = automatic), owned by `ComposeScreen`, saved in prefs and with a shot, and read by `FrameScreen`.
The still picture is `FrameView` in a new still mode (no touch, planning sky). Home cards become rows.

**Tech Stack:** Java, Android SDK 35, osmdroid, Robolectric 4.14 tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-15-framing-and-clarity-design.md`

## Global Constraints

- Plain Java, custom views, no new libraries; `applicationId` unchanged.
- Release 3.3.0: `versionCode 18`, `versionName "3.3.0"` in `app/build.gradle`.
- Words as in CLAUDE.md's Words section; new words: "Centre" (the key), "frame" (verb, in hints only).
- Dates: one day "Sunday, Nov 22"; tight "Nov 22"; ranges "Nov 18 to 20" / "Nov 30 to Dec 2"; "Today", "Tomorrow";
  the year only over 300 days away.
- Home fits a 360 x 780 dp phone (`w360dp-h780dp-xxhdpi`) with Continue shown, without scrolling.
- Every key over a scene: an icon with its name under it.
- Run one Gradle command at a time; `./gradlew testDebugUnitTest` must pass before each commit.

## Review Focus

1. A drag that starts just outside the body's ring frames instead of moving the body: the grab zone is at least
   48 dp across even when the body is drawn small (test in Task 3).
2. Framing so far that the subject leaves the picture: pan and tilt stop at the limit (test in Task 3).
3. A shot saved before 3.3 (no framing stored) opens centred, not at pan 0 with a NaN pitch drawn wrong (test in
   Task 4).
4. The still picture on Your shot for the moon at night and for "Behind you" (body behind the camera): it draws,
   with no body in frame and no crash (test in Task 5).
5. Date ranges across a month or year end ("Nov 30 to Dec 2", "Dec 31 to Jan 2, 2028" when far) (test in Task 1).

---

### Task 1: Dates that never say "Sun"

**Files:**
- Modify: `app/src/main/java/dev/qawalah/sunlighn/Fmt.java` (`day`, `days`, new `dayShort`, `whenShort`)
- Modify: `FrameScreen.java` (headline), `ComposeScreen.java` (sub words): use `whenShort` / `days`
- Test: `app/src/test/java/dev/qawalah/sunlighn/LightFmtTest.java`

**Interfaces:**
- Produces: `static String Fmt.day(Context, long, TimeZone)` → "Today" | "Tomorrow" | "Yesterday" | "Sunday, Nov 22"
  (+ ", 2027" when over 300 days away); `static String Fmt.dayShort(Context, long, TimeZone)` → same but "Nov 22";
  `static String Fmt.whenShort(Context, long, TimeZone)` → `dayShort + " · " + time`; `Fmt.days(c, first, last, tz)`
  → `day` when one day, else "Nov 18 to 20" (same month), "Nov 30 to Dec 2", starting "Today to Nov 20" when the
  first is today/tomorrow.

- [ ] **Step 1: Write the failing tests** in `LightFmtTest`: `dayIsNeverSun` (a Sunday 4 days ahead formats as
  "Sunday, " + "MMM d"; no result of `day`, `dayShort`, `days` over the next 14 days matches `^Sun,|^Sun `),
  `rangesShareTheMonth` ("Nov 18 to 20"), `rangesAcrossMonths` ("Nov 30 to Dec 2"), `shortDayHasNoWeekday`.
  Build dates with `Calendar` in `TimeZone.getTimeZone("Europe/London")` relative to now.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*LightFmtTest*'` → FAIL.
- [ ] **Step 3: Implement** with skeletons "EEEEMMMd" (one day), "MMMd" (short), and for ranges "MMMd" then "d" or
  "MMMd" for the end; add "y" when over 300 days away (existing rule).
- [ ] **Step 4: Use `whenShort`** for the viewfinder headline (`FrameScreen` line with `Fmt.when(c, cursorMs, …)`)
  and in `ComposeScreen`'s sub words where space is tight.
- [ ] **Step 5: Run all tests** `./gradlew testDebugUnitTest` → PASS (fix any test that asserted the old "Sat, Nov 14").
- [ ] **Step 6: Commit** "Dates: full weekday, never Sun".

### Task 2: Home on one screen, and the Build the shot icon

**Files:**
- Modify: `HomeScreen.java` (`addTool`: row card)
- Modify: `app/src/main/res/drawable/ic_tool_build.xml` (owner's sketch)
- Modify: `Tutorial.java` only if it reuses the card layout
- Test: `ScreensTest.java` (`toolCardsHaveIconsAboveTheirWords` → `toolCardsAreRowsAndFit`)

**Interfaces:**
- Produces: each tool card is a horizontal `LinearLayout` described by the tool name; child 0 = a vertical column
  (name `TextView` first, words second), child 1 = the icon disc.

- [ ] **Step 1: Rewrite the test** `toolCardsAreRowsAndFit`: with Continue showing (setUp state), each card's
  orientation is HORIZONTAL, child 0's first `TextView` text equals the tool name, child 1 contains an `ImageView`
  and its left is greater than child 0's right; the "Build the shot" card's bottom in window ≤ 2340 px.
- [ ] **Step 2: Run** `--tests '*ScreensTest.toolCardsAreRowsAndFit'` → FAIL.
- [ ] **Step 3: Implement** the row card: padding 16 dp, name 18 sp bold, words 13.5 sp at most 2 lines, disc 56 dp
  with the 28 dp icon, 10 dp between cards; reduce Home's top spacing if needed to fit.
- [ ] **Step 4: Redraw `ic_tool_build.xml`** (24 x 24 viewport, stroke 1.8, round caps, as the other `ic_*`):
  four corner brackets; a sun at the top left (circle r 2 at 7.5,7.5 with four short rays); the subject: a tower
  standing in the middle from the bottom (a narrow trapezoid from 12,10 up to 12,18 on a base line 9..15 at y 18).
- [ ] **Step 5: Run all tests** → PASS; look at `app/build/screens/01-home.png` and `01b-home-moon.png`.
- [ ] **Step 6: Commit** "Home: the three tools as rows, all on one screen; Build the shot icon redrawn".

### Task 3: Build the shot: drag the picture to frame it

**Files:**
- Modify: `FrameView.java` (touch in compose mode; `Listener.onFrame`)
- Modify: `ComposeScreen.java` (`panDeg`, `framePitch`, limits, Centre key, hint, prefs)
- Test: `ScreensTest.java` (`buildTheShotFramesByDrag`)

**Interfaces:**
- Produces: `default void onFrame(double dAzDeg, double dPitchDeg, boolean settled)` in `FrameView.Listener`
  (degrees the picture moved, finger-follow sign: drag right → the subject moves right → camera swings left);
  `boolean FrameView.onBody(float x, float y)` (within max(ring + 24 dp, 24 dp) of the body's centre);
  `double ComposeScreen.panDeg()` and `double ComposeScreen.framePitch()` (NaN = automatic);
  prefs keys `"frame_pan"` (float string) and `"frame_pitch"` (float string or absent).

- [ ] **Step 1: Write the failing test** `buildTheShotFramesByDrag`: open Build the shot's compose step (as
  `17-build-the-shot`); record the body's place and `panDeg()`; drag from a point 150 dp left of the body to 100 dp
  right of it → `panDeg()` changed, the body's alt/offset unchanged; drag starting on the body → body moved,
  `panDeg()` unchanged; a Centre key (`describedFrom("Centre")`) is shown, and pressing it makes `panDeg()` 0 and
  `framePitch()` NaN; a huge drag keeps the subject's x inside the frame (|panDeg| ≤ fovH/2 − 2°).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement in FrameView**: on ACTION_DOWN in compose mode decide `grabBody = onBody(x, y)`; moves with
  `grabBody` keep today's `onBodyAt`; otherwise call `onFrame(-dx/pxPerUnitDeg, dy/pxPerUnitDeg, false)` per move
  and `settled = true` on up. Pinch unchanged.
- [ ] **Step 4: Implement in ComposeScreen**: `aim()` + `panDeg` for `setAim`'s centre (the subject stays at
  `aim()`); pitch = `framePitch` when not NaN; clamp pan to ±(fovH/2 − 2°) and pitch so the subject's foot or top
  stays in frame; Centre key (Ui.Icon a cross-hair, name "Centre", on the scene beside Turn and Height) visible only
  when pan ≠ 0 or pitch not NaN; save both to prefs on settle; the hint: "Drag the sun to place it. Drag the picture
  to frame it. Pinch to make the subject bigger or smaller." ("moon" for the moon).
- [ ] **Step 5: Run all tests** → PASS; look at `17-build-the-shot.png` and a new `17c-build-the-shot-framed.png`.
- [ ] **Step 6: Commit** "Build the shot: drag the picture to frame it, Centre to reset".

### Task 4: The framing carries to Your shot, the viewfinder and saved shots

**Files:**
- Modify: `ComposeScreen.Host.composed(...)` (adds `double panDeg, double pitch`), `MainActivity` (keeps
  `framePan`, `framePitch`; passes them to `FrameScreen.Line`; clears them when a shot comes from another tool)
- Modify: `FrameScreen.java` (`Line.panDeg`, `Line.pitch`; aim; double tap resets both)
- Modify: `Shoots.java` (`put` stores `pan`, `pitch`; reading returns them, absent → 0 / NaN), `MainActivity.openShot`
- Test: `ScreensTest.java` (`framingCarriesToTheViewfinder`), `ShotsTest.java` (`oldShootHasNoFraming`)

**Interfaces:**
- Consumes: `ComposeScreen.panDeg()`, `framePitch()` (Task 3).
- Produces: `FrameScreen.Line.panDeg` (double, default 0), `FrameScreen.Line.pitch` (double, default NaN);
  `Shoots.put(Context, String key, long timeMs, Trip.Line line, double focal, boolean portrait, double panDeg,
  double pitch)` (the old overload passes 0, NaN).

- [ ] **Step 1: Write the failing tests**: `framingCarriesToTheViewfinder` (frame in Build the shot, Stand here, Open
  the viewfinder → the viewfinder's aim equals subject bearing + pan and its pitch the framed pitch; double tap →
  pan 0); `oldShootHasNoFraming` (a Shoots record written without pan/pitch reads back 0 and NaN).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** the fields and the aim (`az = subjectAz() + line.panDeg`, pitch `line.pitch` when not NaN
  and no manual tilt); Save and the bell store them; a saved shot opens with them.
- [ ] **Step 4: Run all tests** → PASS.
- [ ] **Step 5: Commit** "The framing travels: Your shot, the viewfinder, saved shots and reminders".

### Task 5: The shot as a picture (How you want it, Your shot)

**Files:**
- Modify: `FrameView.java` (`setStill(boolean)`: no touch, no labels except the subject's, compose planning sky)
- Modify: `MainActivity.java` (`renderCriteria`: picture in the sheet head under the intro; `renderShot`: picture
  under the moment, tap → `showFrame(ms)`; helper `View shotPicture(double bodyAz, double bodyAlt, …)`)
- Test: `ScreensTest.java` (`criteriaShowsTheShot`, `yourShotShowsTheShot`)

**Interfaces:**
- Consumes: `FrameView.setLens/setAim/setSubject/setBodies/setCompose` (existing), `Line.panDeg/pitch` (Task 4).
- Produces: `void FrameView.setStill(boolean)`; the picture's content description "Your shot as a picture" on Your
  shot (clickable) and "How it will look" on How you want it (not clickable).

- [ ] **Step 1: Write the failing tests**: `criteriaShowsTheShot` (the picture is present, 120 dp tall; changing
  Where the sun sits from Just on top to On the horizon changes the body's drawn alt in the view); `yourShotShowsTheShot`
  (present; a click opens the viewfinder; for the moon at night and with Behind you it is present and draws without
  throwing).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `setStill` and the two placements; How you want it uses the body's target alt from
  `FrameMath.targetAlt(standPlace, heightNow(), planDist, -FrameMath.EYE)` and the lens that fills a third; Your
  shot uses the real sun/moon at `shotMs` from You.
- [ ] **Step 4: Run all tests** → PASS; look at `05-criteria.png` and `07-your-shot.png`.
- [ ] **Step 5: Commit** "The shot as a picture on How you want it and Your shot".

### Task 6: Point steps say they are done, docs and release

**Files:**
- Modify: `MainActivity.renderPlace` (set point: "Your subject: <name>" / "Your subject is on the map", then
  "Next shows when the <body> lines up with it."; You placed: "You are on the map. Next: your subject.")
- Modify: `CLAUDE.md` (Home card shape in the route text and Controls table; dates in Words; Back unchanged;
  Decisions for 3.3.0 with the owner's 2026-11-15 sketch), `LESSONS.md` (the three questions per step),
  `app/build.gradle` (18 / "3.3.0")
- Test: `ScreensTest.java` (`subjectStepSaysItIsSet`)

- [ ] **Step 1: Write the failing test** `subjectStepSaysItIsSet` (Line up a street step 2 with a subject: the head
  text starts with "Your subject", and "What is at the other end?" is gone).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run all tests** → PASS.
- [ ] **Step 5: Docs and version**, then `./gradlew testDebugUnitTest assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 6: Ali** runs the new build once (driver, told the design, focused on Home, Build the shot framing, the
  pictures and dates); fix what is real; commit.
- [ ] **Step 7: Commit and push** to the work branch; to `main` (the release) when the owner says so.
