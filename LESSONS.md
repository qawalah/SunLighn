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
- **Size a pager's pages while measuring, never from onSizeChanged, and test that a page has a size.** SunLighn sized
  its tutorial cards in onSizeChanged; the test picture was empty, this file called that a quirk of the test, and
  in 3.1.0 the owner saw the same empty pages on the phone. A blank test picture is a bug until shown otherwise:
  assert the page's width and its words' size after a real layout. (SunLighn, 2026-11-14, corrected 2026-11-15) → DevStore (sent 2026-11-15)
- **Test the screens on someone who knows nothing, before the owner does.** Give a small model (Haiku) only the
  pictures of the real screens and one neutral line on how each was reached; no code, no docs. Ask what each is
  for, what it would press, and where it would give up. Then tell it what the app does and ask what the app should
  have said itself. SunLighn 2.0.0 looked finished and the tester gave up at the viewfinder: five unlabelled icons
  and a word ("the line") that was never introduced. (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **A list that repeats itself is one row.** Seven days of "Stand 350 m ENE" are one row with a date range.
  (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **"Never" is an answer, and it comes with the nearest thing that works.** SunLighn said "never lines up" and
  stopped; it now shows the closest it comes and a key that makes it work. (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)

- **Every route ends on a screen that says what you can do next.** In 2.1.0 "Stand here" placed the pin and
  opened the viewfinder, and the owner was left on a map wondering "now what?". 3.0.0 ends every tool on "Your
  shot": the moment, then Open the viewfinder, Save, Remind me, Directions. (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **Back walks back through the steps before it leaves the app.** 2.1.0 closed overlays and then left, so on the
  map Back felt like "minimise". Model the route as steps (tool, step) and let Back pop one; only the first screen
  leaves. Test Back on every step. (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **Never change the layout from inside a layout pass.** A layout-change listener rebuilt the Home screen while
  the map was being laid out; the new cards kept a size of zero and the screen was blank in the test picture. Post
  the change (`view.post(...)`) and skip a rebuild when nothing changed. (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **A test that reads the clock must not depend on which event comes first today.** A Saved test waited for "Sun
  behind subject"; later in the day the moon came first and it failed with no code change. Assert what holds at
  any hour (" behind subject · "). (SunLighn, 2026-11-15)
- **A mode the whole app is in is shown, not only written.** SunLighn's Sun / Moon was a word in a switch, and the
  tool cards were numbers; the owner asked for icons and for the colours to follow the mode. Give each mode and
  tool a drawn icon (in the middle of its card, words beneath), let the accent follow the mode, and change the
  colours with a circle that grows from the finger over a picture of the old screen, never a restart flash.
  Rewrite the palette object in place; only a theme change needs a restart. (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **Robolectric's `idle()` runs a short animation to its end.** To test or picture a transition half way, act on the
  view without idling (`performClick()` then look), and end the animation by hand before the next picture.
  (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **A dark screen is not a black screen.** The viewfinder drew the night sky, the land and the subject all within a
  few steps of black, plus dark pills over them, and the owner called it unreadable. Keep the sky a deep blue,
  the land clearly darker with a horizon line, the subject a silhouette with a lit edge, the one thing that
  matters (the sun or moon) the brightest, and words on a scene as text with a shadow. A view for planning can use
  a calm planning sky instead of the real one. (SunLighn, 2026-11-15) → DevStore (sent 2026-11-15)
- **Opening a view over one with a shadow: give it a higher elevation, and test that it is on top, not that it
  exists.** Android draws and touches children by elevation first, order second. 3.1.1 gave the viewfinder's sheet a
  12 dp shadow; the Move map added later lay beneath it, invisible and untouchable, while the test that found it in
  the view tree passed. (SunLighn, 2026-11-15) → DevStore
- **Never rebuild a list under a finger.** Answers that arrive by themselves (a background search, a name from the
  network) rebuilt Saved's rows; a row being pressed and held was thrown away and "press and hold to rename" did
  nothing. Defer the rebuild until the finger lifts, and rebuild only what the answer changes. (SunLighn, 2026-11-15) → DevStore
- **A choice is not a fact about the answer.** The user picked "Light: Sunset"; the rows printed "Sunset" beside a
  sun 10° up at 2:40 PM, and the tester stopped trusting every answer. Print what the answer is (the light and the
  height at that moment), and say how to get what was asked for. (SunLighn, 2026-11-15) → DevStore
- **Reading the map's colours, lines and names are drawn over the ground.** Ferry routes and their pale edges over
  the Thames read as land, and a spot was put in the river. Count tints of the water's colour as water and leave
  line colours out of the count. (SunLighn, 2026-11-15)
- **Let the tester drive the app, not look at pictures.** Pictures miss what appears only after a scroll, a hold or a
  drag. `AliDriver` (Robolectric, real network) takes commands and answers with a picture and what can be pressed;
  a Haiku tester walked 250 steps and found the hidden Move map, the rename hold and the river spot. Check each
  finding before fixing: some come from the driver. Android allows one permission request at a time (answer through
  the framework's flag), a pinch starts only past ~27 mm, typing replaces the selection, and a picture taken before
  a 1 s animation ends looks empty. (SunLighn, 2026-11-15) → DevStore
- **An answer tied to a point goes stale when the point moves.** SunLighn's Your shot kept its moment after You was
  moved on the map or in the viewfinder, so it showed a time when nothing lined up, and "Sun lined up" jumped to
  it. Whatever is worked out from a point is worked out again when the point moves, and the user is told.
  (SunLighn, 2026-11-15) → DevStore
- **A test driver sends a gesture's events to the window the finger went down on.** Since Android 11 a dialog
  closes on a lift outside it; the driver sent the lift of the holding finger into the rename box it had opened,
  closed it, and reported "press and hold does nothing". (SunLighn, 2026-11-15) → DevStore
- **Every step answers three questions: what can I do next, what can I change, and will I see it.** Keep a table
  of the steps against them in the app's `CLAUDE.md` and check each change against it; "words only" in the last
  column is a gap. SunLighn's criteria step and Your shot were words only, and in Build the shot the picture could
  not be framed, only the sun moved. (SunLighn, 2026-11-15) → DevStore
- **Never shorten a weekday to a word the app uses for something else.** "Sun, Nov 22" on a moon answer read as the
  sun. Write the weekday in full, or leave it out where space is short. (SunLighn, 2026-11-15) → DevStore
- **`TextView.setSingleLine(true)` resets the minimum height to one line.** Set the minimum height again after it,
  and test that keys are whole and on screen, not only that their words fit. SunLighn's three keys under Your shot
  shrank to half height when made single-line. (SunLighn, 2026-11-15) → DevStore
- **A long weekday cuts the time off a headline.** Put the answer (the time) where it cannot be cut, the weekday on
  the line under it. (SunLighn, 2026-11-15)
- **Show only what the user is working on.** The viewfinder of a sun shot told where the moon was ("Moon is 93° to
  the left", a moon line, "Moon lined up") and covered the height scale with it; the owner: "I don't care where the
  moon is". One body per shot, everywhere. (SunLighn, 2026-11-15) → DevStore
- **A setting the user rarely changes folds into one row that names it.** Build the shot spent half its panel on
  Light, Look in, On foot / Drone and a Days field; the owner called it wasted space. Fold them, put the choice
  that changes the answer (When) beside the main key, and put the how-to behind Help. (SunLighn, 2026-11-15) → DevStore
- **A choice that narrows the answer offers "Any", and the answers come grouped by type.** SunLighn made you pick
  sunset or sunrise before seeing either; with Any, the spots come under a Sunset and a Sunrise heading, never mixed.
  (SunLighn, 2026-11-15) → DevStore
- **Starting a task from the start screen starts clean, with "use the last" one tap away.** An old pin from the
  last task sat on the map while the new one asked for a point. (SunLighn, 2026-11-15) → DevStore
- **In AR, the phone's compass is the weak part, not the positions.** A GPS fix is plenty for the sun's direction;
  the compass is often 5 to 20° off. Let the user point at any landmark they can see (picked on a map) and correct
  the compass from its true bearing. (SunLighn, 2026-11-15) → DevStore
- **In a Robolectric test, waiting means moving the clock, not only sleeping.** With the paused looper, `idle()`
  runs only what is due now; a `postDelayed` (the shading, 250 ms) waits for ever unless the clock moves
  (`idleFor`). A test had passed only because another screen's animation happened to move the clock; removing
  that screen (Where to stand, 3.6) made it fail with nothing wrong in the app. Wait loops use `idleFor`.
  (SunLighn, 2026-11-16) → DevStore
- **Merging two tools keeps the old one's saved route working.** A route stored as numbers ("2,0,1") must still
  open somewhere sensible after the tool is gone: keep its number reserved and map its steps onto the new tool
  (`Flow.decode`), with a test. (SunLighn, 2026-11-16) → DevStore
- **A sheet over a map squeezes the map; it never covers what it changes.** SunLighn's When sheet hid the map, so
  moving the day chart changed a sun nobody could see ("I don't see shit when I adjust the day timeline"). The map
  now ends where the sheet begins and refits the line when the sheet rests. (SunLighn, 2026-11-16) → DevStore
- **Show the choices, not one answer and a button.** When showed the soonest time with "Use the soonest" and kept the
  other times below the fold, repeating the soonest there; the owner saw no options. Put the list in sight, never
  repeat the lead item in it, and say "Pull up for more" when the sheet hides some. (SunLighn, 2026-11-16) → DevStore
- **No pop-up that offers the old data back on a fresh start.** "New shot: pick your points. Use the last" came up every
  time a tool started and the owner found it "so annoying". Starting clean is enough; Continue already brings the old
  work back. (SunLighn, 2026-11-16) → DevStore
- **Both tools of an app ask for the same things in the same order.** Line up a street asked where you stand first,
  Build the shot asked for the subject first; the owner called it inconsistent. Both start with the subject now.
  (SunLighn, 2026-11-16) → DevStore
- **A year of answers is grouped, not listed.** Eight daily rows of nearly the same time ("2 Apr 05:33", "3 Apr
  05:36"...) each with the same two tags read as clutter. Days in a row are one run with its range of days, times and
  heights, under month headings, and open to the days on a tap. (SunLighn, 2026-11-16) → DevStore
- **The last step of a flow has a way out.** Your shot had Back and nothing else; the owner asked for a "closure".
  Every flow's last screen gets a Close (or Done) beside its main action. (SunLighn, 2026-11-16) → DevStore
- **Ask what the thing is when it is placed, not later behind a key.** SunLighn drew the owner's building as a 1.8 m
  person because the subject's kind was left from an earlier shot and asked for only in the viewfinder's Height key.
  The subject step now asks "What is it?" as soon as it is on the map. (SunLighn, 2026-11-16) → DevStore
- **"On the horizon" means the real horizon.** SunLighn listed sunsets at 0° that a hill or rising ground beyond the
  subject would hide; the owner asked. Read the terrain along the line (40 points to 25 km, earth's curve and
  refraction) and leave out what it hides. (SunLighn, 2026-11-16) → DevStore
- **A coarse elevation model is not the ground at short range.** SunLighn trusted Open-Meteo's 90 m surface model 63 m
  from a building and stood the photographer 12 m above its foot, so the building sank below the horizon. Use height
  differences only between points several cells apart (300 m here), and keep terrain-horizon samples beyond the
  town's roofs (1 km). (SunLighn, 2026-11-16) → DevStore
- **Try a new web source from the app's own code against real data before release, and say on screen when it fails.**
  SunWhere 3.14.0 fetched buildings from Overpass, which could not be reached from the build container; it shipped
  tested on made-up buildings only, and on the owner's phone in Al Khobar nothing was drawn and nothing said why.
  3.14.1 moved to map tiles from a CDN (OpenFreeMap), added a live test that fetches a real place on demand, and a
  line saying "getting / could not be fetched / N near". (SunLighn, 2026-11-17) → DevStore
- **A library with native code needs a twin the tests can run, and a guard against dying at start.** MapLibre cannot
  run in Robolectric, so SunWhere 3.15 put the map behind one interface (`GeoMap`) with the old osmdroid map as the
  twin the screen tests use; and a native crash while the map starts would kill the app on every launch with Settings
  out of reach, so the start is marked and a mark still there at the next launch brings the flat map back.
  Also: native libraries are stored uncompressed by default (26 MB APK); `jniLibs.useLegacyPackaging true` made it
  12 MB. (SunLighn, 2026-11-17) → DevStore
- **A style borrowed for a working screen lends its type, colour, density and one signature, never its layout.**
  SunLighn's first Braun viewfinder concepts dressed the screen as a calculator's case and a wall poster; the owner
  asked for the design skill to be used, and its rule (a working screen keeps ordinary controls and layout) showed
  both were costumes. The second round kept standard keys and put Braun into one detail each: a radio's tuning scale,
  a single coloured key. Before showing concepts, ask what the screen must show first and what bothers the owner now.
  (SunLighn, 2026-11-17) → DevStore
- **A class named in the manifest must be public, and only a phone shows it.** `Reminders` (a receiver) was package-private; Android makes it with `Class.newInstance` from another package and the app crashed at the first broadcast (every install and boot) in 3.19.0 and 3.19.1, while Robolectric passed. `ManifestComponentsTest` now checks it; run a release on a real phone and read `adb logcat -b crash` once. (SunLighn, 2026-11-20) -> DevStore
- **A custom view never rebuilds the screen from inside its own touch.** `RangeBar` ran `buildSheet()` on the finger's lift, which removed the slider while it was still handling the touch; the next layout pass met a null child and the app crashed on every drag of the sun height slider (3.18 to 3.19.2; Robolectric passed because the test called `commit()`, not a finger). Android posts a button's click for this reason: post the rebuild (`decorView.post`). Test custom views with real `MotionEvent`s, and drag every new control once on a phone. (SunLighn, 2026-11-20) -> DevStore
