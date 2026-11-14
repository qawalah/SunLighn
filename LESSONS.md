# Lessons learned in this app

What this app taught us, so it is not repeated here or in the next app. The shared list for every app is in
DevStore: https://raw.githubusercontent.com/qawalah/devstore/main/docs/lessons/LESSONS.md

Add a lesson in the same change that fixes the mistake: the rule first, then what went wrong, with the date.
Mark a lesson that would help other apps with `→ DevStore`; once it has been added to DevStore's list, change the
mark to `→ DevStore (sent <date>)`. See DevStore `docs/lessons/README.md`.

<!-- Example:
- **Back never throws work away without asking.** Back during a long reading cancelled it silently. (2026-11-14) → DevStore (sent 2026-11-14)
-->

- **A cloud Claude session has no Android SDK and no way to build until you give it one.** Fetch
  `https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip`, put it in
  `cmdline-tools/latest`, accept licences, install `platforms;android-35` and `build-tools;35.0.0`, set
  `sdk.dir` in `local.properties` (git-ignored). Then run `./gradlew testDebugUnitTest assembleRelease`.
  Gradle downloads through the proxy fail at random when run in parallel: use `--max-workers=1` and just run it
  again. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **A push to a session branch is not built by CI.** The release workflow runs for `main` and pull requests only,
  so a branch push proves nothing. Build and run the tests locally before the push that releases.
  (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **Save the user's work as you go, so Back never needs a question.** SunLighn stores the line and map position on
  every change; Back just leaves, and Clear has an Undo instead of a confirmation. (SunLighn, 2026-11-14)
- **Fetch a map tile and look at it before choosing a tile server.** Free tile servers change terms; CARTO's dark and
  light maps now return an "API KEY REQUIRED" picture instead of a map, and a build still succeeds. Download one tile
  with curl, open it, and keep the credit text the server's licence asks for on screen. For a dark map without a
  dark server, invert + rotate hue 180 degrees + dim and tint (not a plain inversion). (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **Reuse the shared icons as vector drawables, not hand-drawn copies.** The CrossPaths `icons.js` path data goes
  straight into `res/drawable/ic_*.xml` (stroke 1.8, round caps, tinted in code); Android reads the SVG arcs as they
  are. My first hand-drawn "locate", "help" and "layers" icons drifted from the set and had to be redone. Bundle the
  shared font in `assets/` and load it with `Typeface.createFromAsset` (works on every API level). (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **When Gradle says "Could not find x.jar" for a file that downloads fine with curl, it has cached the miss.**
  Stop the daemon (`./gradlew --stop`), delete `~/.gradle/caches/modules-2/metadata-*`, and run again. Do not loop
  this: every wipe makes Gradle ask Maven Central for everything again, and after a few rounds it answers 429 Too
  Many Requests; wait a few minutes then. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **No emulator in a cloud session (no KVM): check screens with Robolectric.** A test that launches the real
  activity, presses its keys by text or description, presses Back and saves PNGs (`@GraphicsMode(NATIVE)`) caught
  layout and Back mistakes without a phone. It needs `android.useAndroidX=true` and
  `unitTests.includeAndroidResources = true`. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **osmdroid's `zoomToBoundingBox` never returns on a map that has no size yet.** Calling it from a result that arrives
  before the first layout (a background search finishing fast) freezes the app. A Robolectric test that pressed
  through the screen found it; guard with `isLayoutOccurred()` and `addOnFirstLayoutListener`. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **One container, one job.** When a card has grown tabs inside flows inside a scroller, stop adding to it: give
  the answer a sheet that can be pulled (peek, half, full) and give any view that needs the screen the whole
  screen. Seven releases each added a feature to SunLighn's one bottom card until the viewfinder was a stamp and
  the answer was the smallest text on it. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **One shape, one meaning, for controls too.** Tabs, an either/or switch, filter chips and values (a slider, a
  pinch) must not share a look. SunLighn used the same pill for all four, two rows of them stacked. Keep a small
  table of shapes in the app's `CLAUDE.md`. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **A tap never moves work that is already placed.** A tap may place what is missing; moving a placed thing takes
  a drag or a press-and-hold that asks. SunLighn's third tap silently moved whichever point was nearer.
  (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **The headline is the answer, not the measurement.** The largest text on SunLighn's card was the bearing
  ("230° SW · 451 m"); the time the user came for was a list row. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **Render test pictures at a phone's density.** Robolectric defaults to 1 pixel per dp, so a 1080 px picture
  shows the screen three times too small to judge. Add `qualifiers = "w360dp-h780dp-xxhdpi"` to `@Config`.
  (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **A view sized in onSizeChanged (a pager) stays empty in a Robolectric picture** taken by measuring and laying
  out by hand. Do not ship that picture as evidence; test such a screen by its words and its Back. (SunLighn, 2026-11-14)

