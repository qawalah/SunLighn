# DevStore app repo rules (Android)

This app is released through DevStore. Pushing to `main` builds it, and a new
`versionCode` publishes a GitHub Release that installs itself on the user's
phone through Obtainium.

- **Never change `applicationId`.** The phone would treat it as a different app.
- **Bump `versionCode` (+1) and `versionName`** in `app/build.gradle(.kts)` for
  every change the user should receive. No bump means no release (the build
  still runs as a check).
- **Don't add a release `signingConfig`** and don't commit keystores. CI signs
  every release with the DevStore key, or with the app's own key if its repo
  has `DEVSTORE_KEY_ALIAS` set.
- Keep `gradlew`, `gradlew.bat` and `gradle/wrapper/*` committed.
- When a change is done: commit and push to `main`. That push *is* the
  release. If the environment allows it, run `./gradlew assembleRelease` first.
- If the Release workflow fails, read its log in the Actions tab and fix it.
- `HANDOVER.md` (if present) holds history from the original Claude chat.
  Read it before making larger changes.

## Lessons learned
- **Before changing screens, flow or links between devices**, read the shared lessons:
  `https://raw.githubusercontent.com/qawalah/devstore/main/docs/lessons/LESSONS.md`
  (or `docs/lessons/LESSONS.md` in a DevStore checkout). Follow them unless this file says otherwise.
- **Capture.** When a bug, an audit or the owner's feedback shows something a rule would have prevented, add it to
  this repo's `LESSONS.md` in the same change: the rule, then what went wrong, with the date.
- **Feed back.** Mark a lesson that would help other apps with `→ DevStore`. If this session can reach
  `qawalah/devstore` (`add_repo` with push access), add it to DevStore's `docs/lessons/LESSONS.md` under the right
  heading with this app's name and date, push, and change the mark to `→ DevStore (sent <date>)`. If it cannot,
  leave the mark; a DevStore session collects it later.

## SunLighn: what it is
Photography helper. Pick your subject (search for it or tap it); the app says where to stand and when the sun and
the moon will sit exactly on that line, and shows the shot as the camera will see it. It gives the answer, not a
sun path to read, which is the point: PhotoPills does the latter. Rebuilt as 2.0.0 after the audit in
`AUDIT-2026-11-14.md` (read it before changing screens; it lists what is still open).

- `Astro.java` sun and moon positions plus the search. Plain Java, tested in `app/src/test` against PyEphem values.
- `MainActivity.java` the map (osmdroid, OpenStreetMap, no key), the search bar, the map keys, the line, the sheet
  and what it shows in each state, and saving as you go.
- `Sheet.java` the sheet that rises from the bottom: a grab bar, a head that always shows (the answer in one line
  and the main key) and a body that scrolls. Three heights (peek, half, full); drag the head, fling, or tap the bar.
  With nothing to pull (the first steps) it is as tall as its words.
- The sheet has three states. **Start** (no subject): "Tap your subject", with an either/or switch for people who
  start from where they stand. **Where to stand** (subject only): the next 7 lights of the kind picked, where to
  stand for each (a distance slider, 50 m to 5 km), "Stand here" sets You and opens the viewfinder at that moment; "Plan a frame" opens `ComposeScreen.java`. Days that share a spot are one row; on foot, spots are kept to land (`Ground.java`).
  **Line** (both points): the soonest moment in large words, "Open the viewfinder", then the tabs Times and Day. A line that never lines up says so and offers the closest.
- `FrameScreen.java` + `FrameView.java` + `TimeRuler.java` the **viewfinder**: full screen and always dark.
  The scene (sky colour and glow from the sun's height, stars, the sun and moon at real size on their paths with
  the hours marked, the land, the subject to scale when its height is set, a compass along the top, heights down
  the side, bright corners for what the lens takes in). Pinch or the − + keys change the lens (snaps to 14 to
  400 mm), drag the sky or the ruler for time (the ruler settles on "on the line", sunrise, sunset), up/down tilts,
  double tap aims again. Keys, each with its name under it: Compass (follows the phone's compass and tilt, no camera), Turn
  (portrait/landscape), Height, Share (text or calendar). The bell plans the moment as a shoot. Maths in `FrameMath.java`, tested.
- `Light.java` the names of the light by the sun's height (Daylight, Golden hour, Blue hour, Twilight, Night) and
  one colour for each, used on the ruler, the answers and the Day read-out.
- `Fmt.java` words for times, days, distances. `Zones.java` the place's time zone (Open-Meteo, free, no key,
  remembered per place; the phone's zone until the answer comes or without a network).
- `Places.java` + `SearchScreen.java` search by name (OpenStreetMap Nominatim: one search per press, never while
  typing, a real User-Agent) or typed coordinates; the result becomes the Subject or You.
- `Trip.java` the **Saved** screen, three tabs: **Shoots** (planned shoots, `Shoots.java`), **Lines** (saved lines)
  and **Trip** (the days away and every sun and moon moment on the saved lines in those days). A shoot or a moment
  opens its line and the viewfinder at that time.
- `Reminders.java` the bell: a notification 30 minutes before a planned shoot; alarms set again after a restart.
- `Tutorial.java` first-start tutorial (three cards, Skip always there, replay from Settings, "How it works").
- `SettingsSheet.java` the gear: Theme (System, Light, Dark), Accent (five, as in CrossPaths), Map look, replay the tutorial.
  A theme or accent change restarts the screen (`recreate()`); everything is saved as you go and the sheet reopens.
- Icons are the CrossPaths set (`docs/design/reference/mobile/icons.js` in DevStore) as vector drawables
  (`res/drawable/ic_*.xml`, same path data, 1.8 stroke), tinted from code. Add new icons the same way. The sun and moon
  pictures (`Ui.GlyphView`) and the pin (`Ui.drawPin`) are drawn in the same style. Font: IBM Plex Sans Arabic,
  bundled in `assets/fonts` (SIL OFL, licence beside it); weight carries the hierarchy, never a second typeface.
- `Ui.java` colours and shapes from the DevStore design language (`docs/design/DESIGN.md` in DevStore): navy night
  and pale day themes, one accent, flat bordered surfaces, drawn line icons, route green and blue for the line.
- Map looks: `Map` (OpenStreetMap, tinted navy at night) and `Terrain` (OpenTopoMap), cycled with the layers key and
  remembered. Both need no key. The credit text on the map is required by their licences: keep it, update it per style.
- `app/src/test/.../ScreensTest.java` presses through the real screens (Robolectric, at a phone's density) and saves
  pictures in `app/build/screens/`. Extend it when a screen changes, and look at the pictures.
- `store/icon.png` the DevStore icon: three points on one line (you, subject, sun). Put real phone screenshots in `store/screenshots/` (1.png, 2.png ...).

## Controls (one shape, one meaning; keep it so)
| Shape | Means | Where |
|---|---|---|
| Words with a line under one (`Ui.tabs`) | Switches what is shown below | Times, Day; Shoots, Lines, Trip |
| Two parts in one track (`Ui.segmented`) | An either/or setting | Behind subject / Behind you; On foot / Drone; It is my subject / It is where I stand |
| Pills (`Ui.chip`) | A filter or a kind to pick | Sunset, Sunrise, Moonrise, Moonset; runs of days in Plan a frame; subject kinds; Settings |
| Slider, pinch, ruler | A value | Distance; lens; time |
| Filled accent button | The one main action of the state | Stand here; Open the viewfinder; Save the line on the map |
| A key over the viewfinder | Always an icon with its name under it | Back, Compass, Turn, Height, Share |
| The bell | Plans the moment as a shoot (and its reminder); lit when planned | Answers, Trip moments, Shoots, the viewfinder |
| Three dots | What is done to the line as a whole | Save line, Clear |
| The bar above the sheet (`say`) | What just happened, with Undo | Cleared; You off the map; reminders; hints |

Map touches: a **tap** places a point only while one is missing (the sheet says which). With a line drawn a tap
moves nothing. **Drag** a point to move it (it rides above the finger). **Press and hold** the map to be asked
which point moves there. Tap the sun badge at the end of the line to send it to the other end. In Where to stand,
tap a spot on the map to pick it, or anywhere else to stand there.

## Back (test this on every screen after any change)
| Where | Back does |
|---|---|
| Tutorial open | Closes it (same as Skip) |
| Settings sheet open | Closes it |
| Viewfinder open | Back to the map (same as the arrow). Lens, orientation and height are saved as you go |
| Plan a frame open | Back to the map, nothing set (You is only placed by "Stand here") |
| Subject height dialog or share menu open (viewfinder, Plan a frame) | Closes it, nothing changed |
| Search open | Back to the map, nothing set |
| Name dialog or date picker open (Saved, Times, Day) | Closes it, nothing added or changed |
| Saved open | Back to the map (same as the arrow). Saved as you go |
| Map, sheet pulled to full | The sheet goes back to half |
| Map otherwise | Leaves the app. The line and map position are saved as you go, so nothing is lost |

The phone's permission dialogs have no other layers. If a screen is added, add its row.

## Words (one word per idea)
Changed in 2.1.0 after a first-time-user test (`AUDIT-2026-11-15-first-use.md`): the words on the left of each
arrow are gone from the screens.
**You** = where the photographer stands. **Subject** = what you photograph, the thing the sun or moon lines up
with (never "object"); the first screens say "what you want to photograph" before using the word.
**Line** = the two together. **Behind subject / Behind you** = which end of the line the sun or moon stands at.
**Lined up** (was "on the line") = the body is exactly on your line. **Soonest** (was "Next") and **Low sun /
Low moon** (was "Low") are the two answers per body. **Times** and **Day** are the two tabs of the sheet.
**Viewfinder** (was "Frame") = the full-screen view of the shot. **Plan a frame** = making the picture first (drag
the sun or moon, size the subject) and being told where to stand. **Shoot** = a moment you plan to shoot: it has
the bell on, is listed under Shoots, and reminds you. **Saved** = the screen with the tabs **Shoots**, **Lines**
and **Trip** (the days away). **Moment** = one time the sun or moon sits on a line. **Reminder** = a shoot's
notification. **Where to stand** = a spot for You that lines the low sun or moon up behind (or in front of) the
subject, listed by light: **Sunset, Sunrise, Moonrise, Moonset** (were "Evening sun, Morning sun"). **On foot /
Drone** = whether spots are kept to land. **Compass** (was "Live") = the viewfinder follows the phone.
Do not introduce other names for these.

## Decisions (Claude's calls on 2026-11-14, not yet confirmed by the owner)
- Answer first. Two taps to the answer, last line remembered. The Day tab (sun and moon paths) is a second view the owner asked for on 2026-11-14; it never replaces the answer.
- Times show in the phone's time zone and the zone name is printed under the answer.
- English only for now (Arabic and right-to-left are in the design language; not done yet).
- "Low" means up to 8 degrees above the horizon; for the moon it also needs 60% or more lit.
- Free tile servers that need no key are few. Do not add CARTO (needs a key since 2026) or Esri imagery (terms unclear).
- Light and dark: follow the phone by default; the owner can force either in Settings (asked 2026-11-14).
- The map is graded, not styled: day is softened to near grey, night is inverted with the hues turned back and tinted navy.
- Frame: full-frame sensor (36 x 24 mm) and lenses 14 to 400 mm; the subject is assumed to stand on the horizon (2026-11-14, owner asked for a frame preview).

## Decisions (Claude's calls on 2026-11-14 for 1.2.0, owner asked for trip plan, reminders and where to stand)
- Trip = a first day plus 1 to 14 days, and any number of lines, each named by the user ("Line 1", "Line 2" if
  left empty). Lines are tied to reminders by id. Removing a line removes its reminders, with Undo.
- The trip lists every moment of the sun and the moon on its lines in those days, moon with % lit, Low marked.
- Reminders come 30 minutes before (sooner if that has passed). Exact when the phone allows exact alarms, else
  Android may deliver a few minutes late. Notification permission is asked at the first bell, and that bell is set
  once allowed.
- Where to stand keeps the subject and the distance, and moves You so the next evening or morning low sun (4 degrees
  up) sits on the line, at whichever end the chips say. Undo move puts You back.

## Decisions (Claude's calls on 2026-11-14 for 1.7.0, owner asked to pick an object and be told where to stand and when)
- The light is the sun or moon at 4 degrees up (Astro.SUGGEST_ALT), rising or setting; the next 7 of each. The moon shows % lit; no brightness filter.
- Where to stand is on the far side of the object from the sun (Behind subject) or on the sun's side (Behind you), at a distance the user picks.
- A tap on the map while only the object is set puts You there, so the user is never forced onto a suggestion.

## Decisions for 2.0.0 (2026-11-14)
Owner's choices, asked and answered on 2026-11-14 after the audit:
- Structure: the map stays home with a pull-up sheet; the Frame is full screen.
- Frame: a drawn scene plus Live compass. No camera picture behind it (no camera permission).
- Features in: place search, subject height and light quality, pick a date and the place's time zone, saved lines
  and sharing a moment. One release, 2.0.0.

Claude's calls inside that (not yet confirmed by the owner):
- The subject comes first by default; "Where I stand first" is the either/or switch on the first sheet.
- These replace earlier decisions: "Low" answers and Where to stand are unchanged in meaning, but the small
  "Where to stand / Move here" block on Times is gone: "Where to stand" there now takes You off the map (with
  Undo) and shows the full list. Clear moved behind the three dots, with Undo in the bar. The lens wedge on the
  map is gone (the Frame covers the map). Frame is no longer a tab.
- Golden hour = sun between -4 and 6 degrees, blue hour -6 to -4, twilight down to -18.
- Subject kinds and usual heights: Person 1.8, Tree 10, House 8, Building 30, Tower 100, Peak 300 m; any height
  can be typed. The outline is a plain shape to scale, not a picture of the thing. Without a height it is a dot.
- In the Frame the sun hidden behind the subject is not drawn over it (that is what the camera sees); the ring
  and "On the line" still show where it is.
- Stars in the Frame are not real constellations; they only say "night".
- Times are the place's time once its zone is known; the sheet says when they differ from the phone's.
- Search and time zone need the network; everything else works without (apart from map tiles).
- Not done, listed in the audit as open: turning the map with two fingers, terrain height, weather, units and
  reminder lead time in Settings, Arabic.

## Decisions for 2.1.0 (2026-11-15)
Owner asked on 2026-11-15 for: a separate tab for planned shoots; planning a frame in the viewfinder (set the
subject's height and where the sun or moon should be, and be told where to stand); Where to stand kept to land
"and other reasonable restrictions", with no limits for a drone; a date range when days repeat; saying when a line
can never line up, with near suggestions. And for a first-time-user test by a Haiku agent, with a free hand to
overhaul from its findings. Claude's calls inside that (not yet confirmed by the owner):
- A shoot is the bell: pressing the bell on any moment plans it (reminder + where and how it is shot, kept by the
  reminder's key in `Shoots.java`). Shoots is the first tab of Saved and opens when there is one.
- Plan a frame (`ComposeScreen.java`) is reached from Where to stand, beside "Stand here". The subject's size in
  the picture and its height give the distance; the body's height gives the time; its place left or right of the
  subject gives the side. A lens change keeps the picture (angles scale with it). It needs a height: until one is
  set it uses the usual height of the subject's kind (Building 30 m). Days that give the same spot are one run.
- Ground (`Ground.java`): water and buildings are read from the colour of the standard OpenStreetMap tile at zoom
  15 (no other service). On foot, a spot on water or in a building moves along its line to the nearest land
  (0.5 to 3 times the distance); if there is none the row says so and "Stand here" refuses. Unknown (no network)
  rules nothing out. Not checked: private land, cliffs, roads, whether the subject can be seen from there.
- Days whose spot is within one degree are one row with a range of dates; up to 45 days are looked at, 8 rows shown.
- Never lines up: the sheet says why (the line points too far north or south), shows the closest the body comes
  (date, how many degrees it misses) and "Line it up" moves You round the subject by that much, with Undo.
- From the first-use test: the first sheet asks "What do you want to photograph?" and explains "subject" in the
  same breath; the "Subject first / Where I stand first" switch became a quiet key; viewfinder keys have their
  names under them; the lens says wide, normal or long instead of degrees; the Day chart and the answers explain
  their colours and labels; "How it works" is first in Settings.
