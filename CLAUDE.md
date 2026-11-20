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
- `ROADMAP.md` holds what is planned or proposed (and waiting for the owner). Read it before larger changes; move
  an item out of it into the decisions below when it is done.

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

## SunWhere (was SunLighn): what it is
The app is called **SunWhere** on the phone since 3.9.0 (owner, 2026-11-16). The repository, the package and the
`applicationId` keep the old name (`dev.qawalah.sunlighn`): never change them.

Photography helper. Pick your subject (search for it or tap it); the app says where to stand and when the sun and
the moon will sit exactly on that line, and shows the shot as the camera will see it. It gives the answer, not a
sun path to read, which is the point: PhotoPills does the latter. Rebuilt as 2.0.0 after the audit in
`AUDIT-2026-11-14.md`, and as 3.0.0 around Home and three tools (`docs/superpowers/specs/2026-11-15-three-tools-design.md`;
read it before changing screens); two tools since 3.6.0 (Where to stand folded into Build the shot).

**The route (3.0, two tools since 3.6).** The app opens on **Home** (`HomeScreen.java`): Sun / Moon (each with its
icon), a Continue card for the tool left unfinished, and two tools, simplest first, each a row card with its name and words on the left
and its icon on the right, all on one screen (3.3, owner's sketch; was icon in the middle, 3.1). Each tool is a few steps (`Flow.java`: tool, body, step; Back walks
back). Every tool ends on **Your shot**: the moment, Open the viewfinder, Save, Remind me, Directions.
1. **Line up a street**: Your subject → Where you stand (the other end of the street) → When (that body's times;
   All times / Day chart) → Your shot. (Subject first since 3.8, as in Build the shot.)
2. **Build the shot**: Your subject → the compose view (drag the body from its ring; drag anywhere else to frame
   the picture, swing and tilt, with Centre to reset; pinch the subject; **Where the sun sits**: Horizon / Middle /
   On top, keys that snap the body there and keep it there as the subject, lens or distance change, none lit once
   it is dragged; heights it can never reach in the days chosen, or with every spot on water or in a building, are
   shaded red; folded Settings: Light, Look in, How full for the moon, Distance from the subject, On foot / Drone;
   When, the days found, beside Stand here) → Your shot. The framing travels to Your shot, the viewfinder and the
   saved shot (3.3). (Where to stand, the 3.0 tool between these two, was folded in here in 3.6.0; a saved route
   in it continues here, `Flow.decode`.)
The step bar on top of the map says `3 of 4 · When`, has Back and a Sun / Moon dropdown. Sun and moon
are never in one list. Saved and Settings are on Home.

- `Astro.java` sun and moon positions plus the search. Plain Java, tested in `app/src/test` against PyEphem values.
- `GeoMap.java` the main map behind one face (3.15): `LibreMap` (MapLibre Native 13.7.1, BSD, no key: tilts with two
  fingers or the **3D key**, turns, and stands OpenFreeMap's buildings up while tilted, at the heights the sums use;
  the same raster looks, graded with the same colour matrices through the TextureView's layer paint) and `OsmMap`
  (osmdroid: the screen tests, Settings' **3D map: Off**, phones without OpenGL ES 3 or an Arm chip). Zoom is
  osmdroid's everywhere (MapLibre's is one less). The app's line and pins are a `GeoMap.Layer` drawn on top from the
  map's projection; a finger landing on a pin is the layer's. A start that dies is caught at the next launch: the flat
  map comes back and the bar says so (`map3d_starting`). Only Arm phones get MapLibre's engine (`abiFilters`), stored
  compressed: the APK is about 12 MB. The viewfinder's small maps (Move, Calibrate) are still osmdroid.
- `MainActivity.java` the map (`GeoMap`, OpenStreetMap tiles, no key), the step bar and search, the map keys, the line,
  each step's sheet (one section per tool), Your shot, and saving as you go. A subject on higher ground hides the
  body below its ground: Line up a street leaves those lined-up times out and says so (`Astro.search` with
  `hiddenBelow`, 3.6).
- `Sheet.java` the sheet that rises from the bottom: a grab bar, a head that always shows (the answer in one line
  and the main key) and a body that scrolls. Three heights (peek, half, full); drag the head, fling, or tap the bar.
  With nothing to pull (the first steps) it is as tall as its words.
- `ComposeScreen.java` Build the shot's compose view. `Sky.java` where the sun or moon can and cannot be for it
  (tested). `PickSheet.java` the list a dropdown opens; `Ui.oneLine` keeps a dropdown's value on one line (When).
- `FrameScreen.java` + `FrameView.java` + `TimeRuler.java` the **viewfinder**: full screen and always dark, in one of
  two layouts chosen in Settings (3.16, `Ui.viewfinder`): **Answer band** (the default: the lined-up moment in a band of
  the body's colour, a tap on it goes there; the picture; the time on screen with Now; a radio-style **tuning scale**
  for time, `TimeRuler.TUNING`; the lens − +; the keys) or **Camera** (the picture on the whole screen, the time over the
  sky, a lens scale of focal lengths, a slider for time, `TimeRuler.SLIM`, and one orange **Lined up** key between
  the keys, filled at the moment, a ring elsewhere). The subjects are pictograms (3.16).
  The scene (sky colour and glow from the sun's height, a deep blue night that is never black, stars, the sun and moon at real size on their paths with
  the hours marked, the land, the subject to scale when its height is set, a compass along the top, heights down
  the side, bright corners for what the lens takes in). Pinch or the − + keys change the lens (snaps to 14 to
  400 mm), drag the sky or the ruler for time (the ruler settles on "on the line", sunrise, sunset), up/down tilts,
  double tap aims again. Keys, each with its name under it: Compass (follows the phone's compass and tilt, no camera), Turn
  (portrait/landscape), Height, Move (a joystick to walk where you stand: up towards the subject, sideways
  sideways; the scene follows live, the moment is worked out again on letting go; 3.4; beside it a small map
  following you, with the subject and the line; while walking the map and the stick take the lower part of the
  screen, in the sheet's place, and the picture sits above, 3.4.3), Share (text or
  calendar), Save. The bell sets the moment's reminder. It shows only the shot's body: nothing about the other
  (3.4). Maths in `FrameMath.java`, tested.
- `CameraFeed.java` (3.5) the back camera's live picture (Camera2, no library) behind the viewfinder in AR: it
  fills the screen (centre-crop) and gives the lens of what the screen shows, so the drawn path lies on the real sky.
  AR is the Compass key with the camera allowed: no drawn sky, land or dimmed edges; the subject a ring; the lens
  bar hidden (the camera's lens is the lens). **Calibrate the compass** (under the scene while Compass is on): tap a
  landmark you can see on a small map (the subject is offered first; any tower, mast or hilltop will do), point
  the centre cross at it, Set; the correction (true bearing less the compass) is kept (`compass_offset`), Reset
  removes it. `FrameMath.headingOffset`, tested.
- `Light.java` the names of the light by the sun's height (Daylight, Golden hour, Blue hour, Twilight, Night) and
  one colour for each, used on the ruler, the answers and the Day read-out.
- `Heights.java` the ground's height above the sea where you and the subject stand (Open-Meteo elevation, free,
  no key, a 90 m terrain model: ground, not buildings; up to 100 points per request; remembered per spot of ~11 m).
  `FrameMath.rise` = the subject's ground less yours less your eye (1.6 m). Used by Build the shot (the distance), Your shot (the words) and the viewfinder (the
  subject's foot and top, a hill under a subject on higher ground). Without a network the ground is level.
- `Buildings.java` (3.14) buildings that hide a low sun or moon: OpenStreetMap's buildings as OpenFreeMap vector tiles
  (zoom 14, free, no key, a CDN; read by hand, no library; since 3.14.1, Overpass refused the phone). The tiles round
  you and along the line; kept: within 300 m of you, a corridor 80 m wide to 1.5 km, tall ones to 5 km. Tiles kept a
  month in the cache folder. The tile's 5 m means "not mapped": 9 m (about three floors) is guessed. They stand on
  your ground. `horizon` (tested): the highest top the line's ray crosses, leaving out the building you stand in and
  the subject's own. Optional: Settings, **Buildings** (Count them / Leave out); When and the viewfinder always say
  what the buildings are doing (getting them, could not be fetched, how many, guessed). `BuildingsLiveTest` fetches
  Al Khobar for real when `BUILDINGS_LIVE` is set.
- `ShotLink.java` (3.18) a shot as a link and back (tested); `RangeBar.java` the two-thumb slider; `UserBuildings.java` the
  buildings the owner added.
- `Shadows.java` (3.17) the ground shadow of a standing thing from the sun's or moon's height and bearing (tested); drawn
  by the viewfinder and by the 3D map's `shadows` layer; Settings, Shadows.
- `Fmt.java` words for times, days, distances. `Zones.java` the place's time zone (Open-Meteo, free, no key,
  remembered per place; the phone's zone until the answer comes or without a network).
- `Places.java` + `SearchScreen.java` search by name (OpenStreetMap Nominatim: one search per press, never while
  typing, a real User-Agent) or typed coordinates; the result is the point the step asks for. `Places.reverse`
  names a saved shot by its place (one request per save, only with a network).
- `Trip.java` the **Saved** screen, two tabs: **Shots** (saved shots: a line, a moment or "any time", a name, the
  bell; press and hold to rename) and **Trip** (the days away and every moment on the saved shots' lines, each line
  once however many shots are on it, 3.6). A shot opens Your shot at its moment. `Shoots.java` keeps what a reminder needs (lens, place) by its key.
- `Reminders.java` the bell: a notification 30 minutes before a planned shoot; alarms set again after a restart.
- `Tutorial.java` the **guides** (3.21): swipeable cards, each an animated picture, a title, a line and its points with
  icons (`Ui.note`). **How it works** (`Kind.INTRO`, the first start; Home and Settings replay it): Sun or moon, the two
  tools, Your shot, On the map, Saved. **A guide per tool** (`LINE`, `BUILD`: one card per step), shown the first time
  the tool starts, replayed from the three dots ("How Line up a street works") or Build the shot's Help. **The
  viewfinder's** (`FRAME`): what is drawn, Swipe (sideways = time, up/down = tilt, double tap = aim again), Pinch (the
  lens), the time (band or Camera layout, by the setting), the keys; the first time it opens, replayed by its Help key.
  Settings lists the three under Guides. Seen once closed (`Tutorial.seenKey`); Skip always there; Back closes; a tap
  on a picture plays it again. The screen tests mark them all seen in their setups.
- `Reveal.java` the change of colours (Sun / Moon, Theme, Accent): a picture of the old screen lies on top and a
  circle grows from the finger until the new one fills it (560 ms, CrossPaths ease-out). With animations off it is
  immediate. Sun / Moon changes in place (`Palette.setMoon`); Theme and Accent restart the screen and reveal after.
- `SettingsSheet.java` the gear (on Home): How it works, then Design, Theme, Accent and Map look as dropdowns. Accent starts on
  **Sun and moon** (amber for the sun, violet for the moon), then the five fixed accents.
  A theme or accent change restarts the screen (`recreate()`); everything is saved as you go and the sheet reopens.
- Icons are the CrossPaths set (`docs/design/reference/mobile/icons.js` in DevStore) as vector drawables
  (`res/drawable/ic_*.xml`, same path data, 1.8 stroke), tinted from code. Add new icons the same way. SunLighn's
  own, drawn in that style (3.1): `ic_sun`, `ic_moon`, `ic_tool_line` (a street running to the sun on the horizon),
  `ic_tool_build` (a viewfinder, the sun just on top of the subject). (`ic_tool_stand` went with Where to stand.) The sun and moon
  pictures (`Ui.GlyphView`) and the pin (`Ui.drawPin`) are drawn in the same style. Font: IBM Plex Sans Arabic,
  bundled in `assets/fonts` (SIL OFL, licence beside it); weight carries the hierarchy, never a second typeface.
- **Design** (3.7, `Ui.design`, Settings): **Classic** (the 3.6 look), **Pocket map** (the default: a printed city
  map, paper and map greys, the line in the body's colour, Home a booklet cover with the body as a quarter disc, a
  **map key** under the answer: `Ui.mapKey`/`keyRow`) and **Readout** (a handheld navigator: grey case, a graphite
  **readout** with the answer in large figures and three numbers under it, round keys: `Ui.readout`, `cells`,
  `roundKey`, `namedKey`). Mock-ups in `docs/design/redesign-2026-11-16.html`. The owner is trying both on the phone.
  **Appliance** (3.19, the fourth): small household devices after the owner's picture of Braun-style objects. Off-white
  rounded **faces** standing off the case (`Ui.face`, `FaceDrawable`, with an orange side key on Home's tools), an
  **LCD** for the readout (`LcdDrawable`: pale with dark figures by day, black by night), orange round keys, dot
  **grilles** (`Ui.Grille`). It shares Readout's blocks (`Palette.readoutLike()`); Home's tools are devices
  (`HomeScreen.addDeviceTool`: the name on the LCD, the words, the orange key, a grille and "tool 1" at the foot).
- `Ui.java` colours and shapes from the DevStore design language (`docs/design/DESIGN.md` in DevStore): navy night
  and pale day themes, one accent (rewritten in place when the body changes; never captured), flat bordered surfaces, drawn line icons, route green and blue for the line.
- Map looks: `Map` (OpenStreetMap, tinted navy at night), `Terrain` (OpenTopoMap) and `Clear streets` (Humanitarian
  OSM, 3.8), and the map's colours (Soft, Natural, Paper, Grey, Dark), chosen in `MapLookSheet` from the layers key
  or in Settings, and remembered. Both need no key. The credit text on the map is required by their licences: keep it, update it per style.
- `app/src/test/.../ScreensTest.java` presses through the real screens (Robolectric, at a phone's density) and saves
  pictures in `app/build/screens/`. Extend it when a screen changes, and look at the pictures.
- `app/src/test/.../AliDriver.java` a remote control for a tester agent ("Ali", a Haiku model playing a first-time
  user): runs the real app in Robolectric with the real network (map tiles, search, heights) and answers each
  command (tap, hold, swipe, scroll, pinch, type, back, allow/deny, wait, reopen, dark) with a picture and the list
  of what can be pressed. Skipped unless `ALI_DIR` is set: `ALI_DIR=dir ./gradlew testDebugUnitTest --tests
  '*AliDriver*' --rerun`, then write `dir/cmd.1`, `cmd.2` ... and read `out.N.txt`/`out.N.png` after `done.N`.
  Run it after a larger change: first blind, then told the design; check each finding against the code, since some
  are the driver's (see LESSONS.md).
- `store/icon.png` the DevStore icon: three points on one line (you, subject, sun). Put real phone screenshots in `store/screenshots/` (1.png, 2.png ...).

## Every step answers three questions (owner, 2026-11-15; keep it so)
**What can I do next? What can I change here? Will I see the change?** Check every new or changed step against
this table and add its row. A "words only" in the last column is a gap.

| Step | What next | What I can change | What I see |
|---|---|---|---|
| Home | a tool, Continue | Sun / Moon | the colours change (Reveal) |
| Your subject | Next | tap, search, drag the pin, What is it? (kind and usual height) | the pin; "Your subject: Big Ben. Next: where you stand."; the map key names the kind and height |
| Where you stand | Next (greyed until placed; the words say why) | my location, tap, drag, search | the line; "Next shows when the sun lines up." |
| When | See it in the viewfinder, or tap another time (opens the viewfinder) | All times / Day chart, Behind subject / you, Where the sun sits (pictures) and Sun height (a range, two thumbs), date, Sun / Moon, drag a pin, drag the sun on its day path (the time), Swap You and the subject (three dots), hold the map to add a building | the answer card and the line redraw; the sun and its time on the map; the map above the sheet |
| Build the shot | Stand here (beside When) | drag the body, Where the sun sits (Horizon / Middle / On top), frame (drag the picture), pinch, lens, height, turn, When, Settings (folded: Light incl. golden hour, Look in, How full, Distance, On foot / Drone), Help | the picture itself; one line of words; the lit key |
| Your shot | Open the viewfinder (or tap the picture), Close (Home), Share (a link), Save, Remind me, Directions; from Line up a street, Build the shot (the other tool) | move a point (a new moment, said in the bar); pull the sheet | the still picture of the shot; the map above the sheet |
| Viewfinder | Back, Help (its guide), Save (Answer band), the bell; the band, or in the Camera layout the "Lined up" words (to the moment); the shutter, Take the shot (Camera layout: saves the shot), Now (band) | lens, time, tilt, height, turn, Move (joystick: walk), Compass (AR with the camera), Calibrate the compass (landmark); the layout in Settings | the scene, live as you walk; the real sky in AR; the band and the time on screen |
| Saved | open a shot, the bell | rename (hold), remove (Undo), trip days | the list |

## Choices have "Any", results are grouped by type (owner, 2026-11-15; keep it so)
Where a choice narrows the answer (Light, How full, ...), it offers **Any** unless Any means nothing (Where the sun
sits, a lens). With Any, the results come **grouped by type**, each group under its name (Sunset, then Sunrise),
never mixed in one list. Done for Light and How full in Build the shot (3.4.1, 3.6).

## Starting clean (owner, 2026-11-15)
A tool started from Home starts with no points where it asks for one (no old You pin when picking the subject).
Continue keeps everything as it was. (The bar offering **Use the last** went in 3.8: the owner found it annoying.)

## Controls (one shape, one meaning; keep it so)
| Shape | Means | Where |
|---|---|---|
| Words with a line under one (`Ui.tabs`) | Switches what is shown below | Shots, Trip |
| A round key on the map's side | Changes the map, not the answer | Map look (layers), Go to my location, 3D map (cube: tilts it and stands the buildings up; again, flat and north up), North up (3.20: its needle points to north; only where the map turns) |
| Big tabs: equal parts, an icon over each name (`Ui.bigTabs`, 3.8) | Switches what the sheet shows below | All times, Day chart (When) |
| Two or three parts in one track (`Ui.segmented`) | An either/or setting; with none raised, keys that put something in place and stay lit while it is there | Sun / Moon on Home (with their icons); Behind subject / Behind you; On foot / Drone; Buildings (Settings: Count them / Leave out); 3D map (Settings: On / Off); Where the sun sits in Build the shot (Horizon / Middle / On top, none lit once dragged) |
| Dropdown (`Ui.dropdown`, opens `PickSheet`) | Pick one from a list | Light, How full, Look in, When; Theme, Accent, Map, Viewfinder; Sun / Moon in the step bar |
| A row that names settings, with a chevron | Pulls the settings card up (or down) | Build the shot's Settings (3.10: the card also pulls by dragging) |
| Slider, pinch, ruler | A value | Distance (Build the shot's Settings); lens; time |
| Filled accent button | The one main action of the step | Next; See it in the viewfinder (When); Stand here; Open the viewfinder |
| A key over the viewfinder | Always an icon with its name under it | Back, Help, Compass, Turn, Height, Move, Share, Save (Take the shot in the Camera layout) |
| A guide: swipeable cards, a picture, points with icons, Skip and Next (`Tutorial`) | How a part of the app works | How it works; each tool the first time; the viewfinder the first time; replayed from Settings, Guides, the three dots, Help |
| The bell / Remind me | The shot's reminder (saves the shot too); lit when set | Your shot, Shots, Trip moments, the viewfinder |
| Three dots | What is done to the line as a whole | Save, Swap You and the subject, Clear, How this tool works |
| A row card: name and words on the left, icon on the right | Starts a tool | Home |
| A small still picture of the shot | What the moment looks like; a tap opens the viewfinder | Your shot |
| The bar above the sheet (`say`) | What just happened, with Undo | Cleared; You off the map; reminders; hints |
| A note: a small line icon, then one idea in words (`Ui.note`, 3.20) | Explains, warns (red, the warning icon) or says what is left out; never a paragraph of several ideas | Under the answers on When and Your shot, the Day chart, Build the shot's Help and Distance, Settings, Calibrate the compass |

**No pills** (owner, 2026-11-15): a pick from a list is always a dropdown; `NoPillsTest` keeps `Ui.chip` out.

Map touches: on a step that asks for a point (Where you stand, Your subject) a **tap** places or moves that
point, and so does a press and hold. On other steps a tap moves nothing. **Drag** a point to move it (it rides above the finger). **Press and
hold** the map (past the point steps) to be asked which point moves there. Tap the sun badge at the end of the line to send it to the
other end.

## Back (test this on every screen after any change)
| Where | Back does |
|---|---|
| A dropdown list, a guide (How it works, a tool's, the viewfinder's), Settings, a dialog, the share menu, the joystick, the compass calibration | Closes it, nothing changed |
| Viewfinder | Back to the step it was opened from (Your shot). Lens, orientation and height are saved as you go |
| Your shot opened from Saved | Back to Saved (3.2.2) |
| Build the shot, compose step | One step back (Your subject); the picture's points stay |
| Search | Back to the step, nothing set |
| Saved | Back to Home (where it is opened). Saved as you go |
| Sheet pulled to full | Goes back to half |
| A tool, step 2 or later | The step before. Points and settings stay |
| A tool, step 1 | Home |
| Close on Your shot (3.9) | Home: the tool is done; the shot stays as it is |
| Home | Leaves the app (the system default for the first screen) |

Entering Build the shot sets You aside (it exists to find it): "Stand here" places a new You,
and back on Home the old one returns. Build it is kept as it was left while Your shot shows. A screen restart
(dark mode at sunset) returns to the same step. Switching Sun / Moon on Your shot steps back to choose again.
The phone's permission dialogs have no other layers. If a screen is added, add its row.

## Words (one word per idea)
Changed in 2.1.0 after a first-time-user test (`AUDIT-2026-11-15-first-use.md`): the words on the left of each
arrow are gone from the screens.
**You** = where the photographer stands. **Subject** = what you photograph, the thing the sun or moon lines up
with (never "object"); the first screens say "what you want to photograph" before using the word.
**Line** = the two together. **Close** (3.9) = the key on Your shot that ends the tool and goes Home. **Run** (in code)
= days in a row the body lines up, one row in All times that opens to its days. **All times** and **Day chart** (3.8, were Times and Day) = the two tabs of When. **Behind subject / Behind you** = which end of the line the sun or moon stands at.
**Lined up** (was "on the line") = the body is exactly on your line. **Distance** (3.19.7, was "Stand 459 m", "stand 99 m back",
"away") = how far you are from the subject, always said "Distance 459 m" (Build the shot's answer and slider, Your shot, the
viewfinder, Share); "Stand here" / "Fly here" stay as the main key's name only. Build the shot's card runs top to bottom:
the settings, then the answer (Soonest, its Distance, the details, When, Stand here); the Distance slider shows where it
gives a result (red where none does). **Soonest** (was "Next") and **Low sun /
Low moon** (was "Low") are the two answers per body. **Times** and **Day** are the two tabs of the sheet.
**Viewfinder** (was "Frame") = the full-screen view of the shot. **Answer band** and **Camera** (3.16) = its two layouts, chosen
under **Viewfinder** in Settings. **Frame** (verb, 3.3) = swing and tilt the
picture in Build the shot; **Centre** = the key that undoes it. **Dates** (3.3): one day "Sunday, Nov 22" (the
weekday in full, never "Sun"), "Nov 22" where a line is short, ranges "Nov 18 to 20"; "Today", "Tomorrow". **Saved** = the screen with the tabs **Shots**
and **Trip** (the days away). **Moment** = one time the sun or moon sits on a line. **Reminder** = a shot's
notification. **Where to stand** = no longer a tool (3.6): the words say where to stand, Build the shot finds it;
its Light names stay: **Sunset, Sunrise** or **Moonset, Moonrise** (for the body chosen). **On foot /
Drone** = whether spots are kept to land. **Design** (3.7) = the look chosen in Settings (Classic, Pocket map, Readout, Appliance); **map key** and **readout** name their
signature blocks in code only. **Compass** (was "Live") = the viewfinder follows the phone (with the
camera behind it when allowed: AR, 3.5). **3D map** (3.15) = the tilted map with the buildings standing up (the key and the setting). **Landmark** = anything you can see whose place you tap on the map, used
to **Calibrate the compass**.
Changed in 3.0.0: **Home** = the first screen. **Line up a street**, **Build the shot** = the tools (Build the shot
replaces "Plan a frame" and, since 3.6, Where to stand). **Your shot** = the last step of every tool. **Shot** = a
saved line with a moment (replaces "Shoot" and saved "Line" in Saved). **Move** = the viewfinder key that moves
You. **Directions** = opens the phone's maps app at the spot. **Where the sun sits** = on the horizon, behind the
middle, just on top (the keys say Horizon, Middle, On top). ("How you want it", the criteria step, went in 3.6.)
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
- Search and time zone need the network; everything else works without (apart from map tiles). (3.2: so does the
  ground's height; without it the ground is level.)
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

## Decisions for 3.0.0 (2026-11-15)
Owner's choices: a Home with three tools (simplest first) and Sun / Moon at the top; each tool a short series of
steps; sun and moon separated; Back steps back; no pills (dropdowns); change position from the viewfinder;
saving a shot in one press, named by city or place later. Claude's calls inside that (not yet confirmed):
- One activity with a step model (`Flow`), not separate activities or Fragments. Step sheets stay in MainActivity.
- Where the sun sits defaults to "Just on top": the body's lower edge on the subject's top, from height and distance.
  The moon also has How full (Any, Half or more, Nearly full; default Half or more).
- Line up a street starts from where you stand; the other tools from the subject.
- Every tool ends on Your shot (Open the viewfinder, Save, Remind me, Directions).
- Build the shot shades heights only (left or right of the subject is always possible), looked at in the next 2
  weeks, 6 weeks or year. The reason is written under the picture.
- A shot is a saved line with a time; 2.1 lines become shots with no time, 2.1 planned shoots become shots (once).
- Naming asks Nominatim only with a network; until then "Shot N". Unnamed shots are named when Saved opens online.
- Saved and Settings are reached from Home only. The When step's time rows have no bell (Remind me is on Your shot).
- The viewfinder still draws both bodies (it is what the camera sees) and keeps both "lined up" jumps.

## Decisions for 3.1.0 (2026-11-15)
Owner asked for: icons for the tools that say what each does, in the middle of the card with the words beneath
(Where to stand: a question mark with a cross beneath); the CrossPaths language; the accent changing between sun and
moon; icons for Sun and Moon; the transition; and the tutorial. Claude's calls inside that (not yet confirmed):
- The accent follows the body by default: amber for the sun, violet for the moon (the colours the sun and moon
  already had). It is a new Accent choice, **Sun and moon**, first in the list; a fixed accent stays fixed. Saved
  under a new key (`accent2`), so everyone, the owner included, starts on Sun and moon once.
- The change opens as a circle from the finger (`Reveal`), for Sun / Moon, Theme and Accent alike. Sun / Moon
  rebuilds in place (no restart: the map and its tiles stay); the sheet and Home are rebuilt, the step bar's icon
  updates. The numbers on the tool cards are gone: the order (simplest first) stays.
- Tool icons: Line up a street = a street running to the sun on the horizon; Build the shot = a viewfinder with
  the sun just on top of the subject.
- The tutorial gets a first card, Sun or moon, which plays the change in small; the tool cards show their icon.
- The bar that says what happened (inverted colours) now uses the accent of the other theme for its keys
  (it was a fixed teal, against the Read-The-Theme rule).

## Decisions for 3.1.1 (2026-11-15)
Owner: the viewfinder colours were bad ("suggest ways"), then "do all"; and the tutorial showed empty pages.
- The night sky is a deep blue with a glow at the horizon (`KEY_HORIZON`/`KEY_ZENITH` first keys), never black.
  The land is darker than the sky with a fine pale horizon line; the subject is a silhouette with a lit edge.
- Build the shot uses a fixed blue-grey **planning sky** at any hour (no stars, no sun glow): it is for placing
  things. The realistic sky stays in the viewfinder opened from Your shot.
- The moon is off-white with a soft glow; the grip ring and the paths take the body's colour (sun gold, moon
  lilac), stronger. Bodies are drawn at least 7 dp in Build the shot so they can be grabbed.
- Labels on the scene are words with a shadow, not dark pills (the coloured "Lined up" badge and the "is 70° to
  the left" pills stay). "Drag the moon" goes once the body has been dragged.
- Outside the lens is dimmed more (55%), the grey box is gone, the corners are finer.
- Heights the body can never reach: red lines over a faint red, the sky still seen through.
- The sheet under the scene has a lighter edge and a shadow.
- Not done then, done in 3.2.0: the ground's height of You and the subject.

## Decisions for 3.2.0 (2026-11-15)
Owner asked: "automatically fetch ground height" (after asking whether the app takes the height of you and the
subject into account; it did not). Claude's calls inside that (not yet confirmed):
- Open-Meteo's elevation service (the same free, keyless provider as the time zone). Ground only: a subject on a
  roof or you on a balcony is not known; the subject's own height is still typed or taken from its kind.
- Your eye is 1.6 m above your feet, always (it was 0 before: level ground now means the subject's foot 1.6 m
  below your eye).
- Where to stand: the days are found for level ground, then the ground under every spot is asked in one request
  and each day is worked out again for its spot (once; a day the body never gets that high is dropped). The row
  says "18 m lower than the subject's ground" when the difference is 3 m or more.
- Build the shot: for the same picture, a subject on higher ground means standing farther away. Two rounds of
  asking settle the spot. The words under the answer name the difference.
- "On the horizon" means on the subject's ground when that stands above the horizon.
- Your shot and the viewfinder use the two grounds once they come (the sheet and the scene redraw).
- Not done: Line up a street's "lined up" moments do not yet hide a sun that is below the subject's ground; the
  curve of the earth and refraction are left out (under 0.1 degree at these distances).

## Decisions for 3.2.1 (2026-11-15)
Owner asked for a first-time-user test by a Haiku agent ("Ali") walking the real app, blind and then told the
design, and for the app to be improved from it. Claude's calls inside that (not yet confirmed):
- Where to stand rows say the light and the height ("Daylight, 10° up", "Golden hour, 3° up"), not "Sunset": the
  Light choice only says which side of the day. With the sun above golden hour the row adds how far to stand for
  it (the same part of the subject at 4° up).
- Ground: blue tints over water count as water and violet lines (ferry routes, boundaries) are left out of the
  count; the patch is about 25 m square.
- The viewfinder's Move map stands above the sheet (it was drawn and touched beneath its shadow) and fits both
  points. "Sun lined up" there goes back to the shot's moment when opened from Your shot.
- Saved never rebuilds while a finger is on it (press and hold to rename works); the Trip moments rebuild only the
  Trip tab.
- Days drop the year unless over 300 days away. The subject's kind is named without a height (the height is beside
  it). Just under the horizon reads "on the horizon". Search prefers places near the map and says how far each is.
  "Fly" on Your shot when it came from a Drone spot.

## Decisions for 3.2.2 (2026-11-15)
Owner asked for a review of the process flow with a UX audit and a re-run of Ali (`AUDIT-2026-11-15-ux-flow.md`).
Claude's calls inside that (not yet confirmed):
- A point moved on Your shot (hold, drag, or Move in the viewfinder) gives a new moment: the soonest from the new
  spot, said in the bar; the viewfinder goes to it.
- Location is asked once, when the first tool's map opens (not after the tutorial).
- Never lines up: "Line it up" is the main key. Build the shot has its own On foot / Drone (the same setting).
- Saved has no save key of its own; Save on Your shot is the one way. Shots named from one place get " 2", " 3".
- Back from a shot opened in Saved returns to Saved. Back from Your shot keeps the spot row that was picked.
- The Continue card names the step it opens. The viewfinder's light tag sits under the time.



## Decisions for 3.3.0 (2026-11-15)
Owner asked: every step answers "what can I do next, what can I adjust, will I see it"; in Build the shot, move
the frame (composition and tilt), not only the sun; fix "Sun" read as Sunday; all three tools on Home's screen as
in their sketch (name left, icon right; the Build the shot icon redrawn: corners, the sun top left, the subject).
Design: `docs/superpowers/specs/2026-11-15-framing-and-clarity-design.md`. Claude's calls inside that (not yet confirmed):
- A drag that starts on the body's ring (its ring plus a finger's width) moves it; anywhere else frames the picture
  under the finger. Framing keeps the subject and the body in the picture, scales with the lens, and is remembered.
- The framing is kept on the saved shot, not in the reminder's own store; a double tap in the viewfinder re-centres.
- The still picture sits in the sheet's head (How you want it: above Find spots; Your shot: under the moment).
- Home's words were shortened to fit beside the icon.
- After the review and Ali's run: framing margins are in proportion to the lens (15% / 20%), applied on every
  draw; the body stops at the picture's edge and never so high the subject must leave. Headlines and time rows
  say "Nov 26 · 3:28 PM" with the weekday on the line under (the time is never cut). Date ranges come from the
  locale's own range format ("Nov 18 to 20", "18 to 20 Nov"). A saved shot keeps its subject's name; Back from a
  Trip moment returns to Saved.

## Decisions for 3.4.0 (2026-11-15)
Owner, from the phone: "I don't care where the moon is" (a sun shot); a joystick in the viewfinder to move while
looking (asked: it moves where you stand); in Build the shot the words, Days and settings were wasted space
("collapse", "When chooser", "make it ?"), and Light needs golden hour; on How you want it the lens should be a
choice. Claude's calls inside that (not yet confirmed):
- The viewfinder shows only the shot's body: path, disc, ring, "is 93° to the left", readout, lined-up key, ruler
  marks. It aims by that body. (Replaces 3.0's "draws both bodies".)
- Move shows a joystick (replaces the small map): up walks towards the subject, sideways sideways, speed with the
  push (a fifth of the distance a second at full, at least 10 m/s); never closer than 15 m. The scene follows at
  once; letting go moves You everywhere and gives the new soonest moment.
- Build the shot: one line of words (times, light, height, ground); When (the days found) beside Stand here;
  Light, Look in and On foot / Drone folded into one Settings row; the how-to behind a Help key; a red line only for
  a reason (shaded, or no place to stand, when Stand here is greyed). Light adds "Sunset golden hour" and "Sunrise
  golden hour": the sun no higher than 6°.
- How you want it: Lens (Fill a third, or 14 to 400 mm); the picture uses it, the words say how much of the picture
  the subject fills; Stand here opens the viewfinder with it.
- From the review's minors: Save brings a saved shot's framing up to date; a sideways drag keeps the automatic
  tilt; search is near the map only when it shows a town (zoom 8 or more).

## Decisions for 3.4.1 (2026-11-15)
Owner: an old You pin when a tool asks for the subject is annoying; and, as a general note, choices should offer
Any with the results grouped by type. Claude's calls inside that (not yet confirmed):
- Starting a tool from its Home card clears the points it asks for (Line up a street: You and the subject; the
  other two: the subject; You is set aside as before) and offers "Use the last" in the bar. This replaces 3.0's
  "starting a tool clears nothing".
- Light gets Any in Where to stand (spots for both, at most four runs each, under a Sunset and a Sunrise heading)
  and in Build the shot (both sides' days, at most three each, When names each "Sunset · ..."; shading allows a
  height either side reaches). Not given Any: Where the sun sits, Look in, Subject (a kind is needed for a height).

## Decisions for 3.4.2 (2026-11-15)
Owner: the joystick should show on the map where you have got to. Claude's calls inside that (not yet confirmed):
- A small map (to look at, not to touch) beside the joystick: you (accent dot), the subject (blue) and the line
  (green), centred on you and following as you walk, the subject in sight at the start.
- While walking, the joystick and the map take the lens bar's place under the picture (the lens bar comes back on
  closing), so the subject and the sun are not covered.

## Decisions for 3.4.3 (2026-11-15)
Owner: the lens selection should be a range; the map and joystick should take the lower part. Claude's calls
inside that (not yet confirmed):
- Lens on How you want it is a range of stops (14, 16, 20, 24, 28, 35, 50, 70, 85, 105, 135, 200, 300, 400 mm),
  a slider with two thumbs (`RangeBar`); 14 to 400 means any lens (the "Any" rule). The app picks the length in the
  range that fills a third, or the nearest end, and says so ("With 24 to 105 mm: use 70 mm ..."; "... fills 12% at
  105 mm: come closer for a third"). The picture uses it; Stand here opens the viewfinder with it. (Replaces the
  single-lens dropdown of 3.4.0.)
- Walking: the map and the stick fill the lower part of the screen (280 dp) in the sheet's place; the sheet and
  the lens bar come back when Move closes.

## Decisions for 3.5.0 (2026-11-15)
Owner: an AR mode to see the sun or moon over the real view; asked whether positions must be calibrated (answer:
only yours, and the GPS is enough; the compass is what needs setting); the compass is set from any landmark, not
necessarily the subject. Design: `docs/superpowers/specs/2026-11-15-ar-design.md`. This reverses 2.0.0's "no camera
picture behind it (no camera permission)". Claude's calls (not yet confirmed):
- AR lives in the viewfinder's Compass key; the camera is asked for once, at the first Compass press; refused, the
  drawn sky stays (and is not asked again).
- In AR the picture takes the camera's field of view (its focal length, sensor size and the centre-crop), upright.
- Calibrate: a small map, the subject offered first, any point tappable; a cross in the middle of the picture; Set
  stores the correction, Reset clears it; Back closes it. Tilt is not calibrated.

## Decisions for 3.21.0 (2026-11-20)
Owner: "revisit the How it works card, revise and add if needed, make a tutorial for each mode, and a tutorial for the
viewfinder: how to navigate it and its main elements, like what swiping does and what pinch does". Claude's calls (not
yet confirmed):
- "Mode" read as the two tools. One `Tutorial` class, four guides (`Kind`), the same card shape: a picture, a title,
  one line, then the points, each with its icon in the accent (the 3.20 notes).
- How it works, revised: the two tool cards say a little more (where the sun sits; hills and buildings counted; red
  shading), and three cards are new: **Your shot** (the keys light one by one), **On the map** (tap, drag a pin, drag
  the sun, tilt, north), **Saved, and more help** (Saved, Trip, the guides). Six cards.
- A tool's guide is one card per step, numbered ("1 · Your subject"); it comes the first time the tool starts from Home
  (not from Continue) and only once. Build the shot's Help opens it instead of the how-to lines under the picture (the
  same words are on card 2).
- The viewfinder's guide comes the first time it opens, over it. Pictures: the scene drawing itself (path, hours,
  compass, heights), a finger swiping sideways (the sun walks, the time changes) then up (the horizon moves: tilt), two
  fingers spreading (24 → 85 mm, the tower and sun grow in fixed corners), the scale dragged to the Lined up pin and the
  band tapped, the keys. Its words follow the layout chosen in Settings.
- Settings: under How it works, **Guides** with three rows (Line up a street, Build the shot, The viewfinder).

## Decisions for 3.20.0 (2026-11-20)
Owner, from the phone: the notes under When, marked "Bullet point": "make these bullet points with icons, apply app
wise"; then "allow me to move the day by dragging the sun, add north button". Claude's calls (not yet confirmed):
- **Notes are points** (`Ui.note`, `Ui.Note`): one idea each, a small CrossPaths-style icon (16 dp at 12.5 sp, the words'
  colour) then the words. Icons by kind: chart (shaded heights), warning (red: hidden, a building hides the subject, no
  result), height (the subject's top), cube (buildings), hill (the land or the subject's ground hides it), viewfinder
  (tap a time), sun / moon, map (the line), clock (time zone), move (drag a point), info (anything else). Paragraphs
  that held several ideas were split. New icons: `ic_warning` (from the CrossPaths set), `ic_info`, `ic_clock`,
  `ic_hill`, `ic_north` (drawn in its style). Headlines, one-sentence choices and row details stay plain words.
- **Drag the sun on the map**: the sun (or moon) drawn on its day path round you follows the finger; the time is where
  its path (every 10 minutes, read between) passes the finger's bearing, the nearest to the time before, a time below
  the horizon only when none above passes there. Its time is drawn over it while dragged. On the Day chart the
  chart's cursor, the read-out and the shadows follow live; on All times, letting go opens the Day chart at that time
  (the bar says so). Dragging You or the subject still wins when the finger lands on a pin.
- **North up**: a round key under the 3D key, its needle turned to where north is as the map turns; a tap turns the map
  north up and keeps the tilt. Only on the 3D map (MapLibre); the flat map never turns, so it has none.
- Built and tested here for the first time in a while: the Android SDK was installed in the session, the whole test
  suite ran (`ScreensTest.dragTheSunMovesTheDay` new).

## Decisions for 3.19.0 (2026-11-19)
Owner sent a picture of Braun-style small appliances: "Make a new theme inspired by attached", then "just dive in, add it
as a fourth design, and release". Claude's calls (not yet confirmed):
- Named **Appliance**, not after the brand (no maker's name or wordmark in the app; the faces say "SunWhere").
- The sun is the picture's signal orange (#EE5A1E, #FF7A3D at night); the moon stays violet (the accent follows the body,
  3.1). Orange marks the one key that matters: the main button, the round key on each tool, a lit key.
- By day an off-white case with lighter faces; by night the black edition (graphite faces, a black LCD).
- Readout's screens are reused as they are (answer, Your shot, the point steps, Continue); Appliance draws them as an LCD
  and its keys as raised faces. The map is graded like Readout's. The viewfinder, Build the shot and Saved take the colours.
- Pocket map stays the default.

## Decisions for 3.19.1 (2026-11-20)
Owner: the app keeps crashing, "when changing the angle" (the Sun height range), and no log to read. Claude's calls:
- `CrashLog.java`: an uncaught exception is written to `last-crash.txt` as the app dies; the next start shows it (version,
  phone, the stack) with Share, Copy and Close. Failures the app survives are written the same way, marked "caught".
- Shadows (map and viewfinder), the added buildings' outlines and the range slider's rebuild fail soft: caught, written
  down, the app goes on. This hides the symptom, not the cause: the cause is still to be found from the shown text.

## Decisions for 3.18.0 (2026-11-18)
Owner, from the roadmap review: "keep offline maps for later, and users; make the shots shareable by a link that the other
person can get and save; implement the rest" (swap, sun height range, a missing 3D building, the hidden-subject bug).
Claude's calls (not yet confirmed; nothing here was compiled or run: Gradle could not fetch its plugins in the session):
- **Share a shot by a link** (`ShotLink.java`, tested): Your shot has a Share key beside Close. The shot (both points, mode,
  moment, body, name, the subject's name, kind and height, framing) is packed into one short code (deflated, URL-safe,
  about 150 characters) in `https://qawalah.github.io/SunLighn/s/#CODE`; no server, no account (users are not built: a link
  needs none). The page (`docs/s/index.html`, needs GitHub Pages switched on for `docs/`) hands the code to the app as
  `sunwhere://shot?d=CODE`; the manifest takes both (`singleTask`, so a link reaches the open app). The app asks "Save it to
  your shots and open it?" and Save and open adds it to Shots (a new id) and opens Your shot.
- **Sun height is a range** (`RangeBar.java`): Where the sun sits' slider has two thumbs (0° to 20°); the times shown are
  those with the body between them, never narrower than ±0.75° (`ANGLE_SLACK`, a day's change). The pictures still set
  it (Any: the whole slider); a drag lights none. `line_lo`/`line_hi` replace `line_angle` (an old angle becomes a range).
- **Swap You and the subject**: in the three dots (not Your shot, not Build the shot); the points trade places, the kind
  and height stay with the subject, its name goes; Undo in the bar.
- **A missing 3D building** (`UserBuildings.java`, `BuildingDialog.java`): press and hold the map (once the line is set)
  asks Move the subject here / Move You here / Add a building here (or Remove the one added here). A box: height, width,
  depth, turn; kept on this phone; merged into every `Buildings.Area` (the sums, the viewfinder, the shadows); drawn as an
  amber outline on the map. Not stood up in 3D, not shared, mapped buildings cannot be corrected or hidden yet.
- **A building hides the subject** (`Buildings.cover`, tested): the ray from your eye to the subject against the buildings
  between (the subject's own and yours left out, flat ground); the sheet (When, Your shot) and the viewfinder say "A building
  40 m from you (12 m tall) hides the subject's lower 12 m" or "hides the subject from here". Only words: the times are
  about the sun and are not left out. Build the shot does not check it yet.
- Not built, kept for later: offline maps with 3D data; users (accounts).

## Decisions for 3.17.1 (2026-11-18)
Owner: "I want the shadows to reflect in real-time as I change the daytime". The viewfinder already redrew them with
every change of time (its sun and moon move with the ruler). The 3D map now does too: on the Day chart the shadows take
the body's place at the chart's cursor (`MainActivity.updateShadows`, called on every drag, a new shape every half
minute of time); elsewhere they stay at the moment the map shows. Not run here (no build); look on the phone.

## Decisions for 3.17.0 (2026-11-17)
Owner: "Can u cast shadows in the viewfinder", then "only implement the shadow feature, for the 3d map view and
viewfinder". Claude's calls (not yet confirmed):
- `Shadows.java` (tested): flat ground, the shadow runs away from the body, length = height / tan(height of the body),
  cut at 400 m, none under 1°; strength fades to nothing as the body comes down (the moon's is 30% of that). A building
  casts the hull of its footprint and the footprint moved; the subject (a pictogram facing you) casts a parallelogram.
- Viewfinder (`FrameView.drawShadows`): the shot's body (the sun unless the shot is the moon's); one dark shape on the
  ground (overlaps do not double), under the far buildings and the subject; not in AR or Build the shot's planning sky.
  Both layouts.
- 3D map (`LibreMap` layer `shadows`, a `fill` layer under the buildings): only while tilted; for the moment the map
  shows (the soonest of the body chosen, `MainActivity.updateShadows`), from the buildings read for the sums (not every
  building the map draws: the 3D buildings of the style beyond that area cast none) and the subject.
- Settings, **Shadows**: On (default) / Off. Needs no restart.
- Not built: the real build could not run here (Gradle cannot fetch its plugins); only `Shadows`'s own tests ran.
  Look on the phone: the viewfinder's shadow direction against the sun, and the map's shadows against the standing
  buildings.

## Decisions for 3.16.0 (2026-11-17)
Owner: "Redesign the viewfinder as a Braun Dieter Rams design and the tree/building, tower, etc. as well, show me two
concepts", then (with the impeccable skill) asked what to see first (the time and Lined up), what bothered them (not
Braun, crude subjects, too busy, the time control unclear) and which keys they use (all: lens, Compass, Move, Height /
Turn); of the two layouts "both, in the settings switchable", and the pictogram subjects. Mock-ups:
`docs/design/viewfinder-concepts-2026-11-17.html`. Claude's calls inside that (not yet confirmed):
- Braun comes through type, colour, density and one signature each; the controls stay ordinary (no instrument case, no
  poster: the first round's costumes were dropped).
- Answer band is the default. Its band names the lined-up moment nearest the time shown (two days either side, or the
  next one), with the day, the light (the moon: % lit), how long until, the height to a tenth, the bearing and where to
  stand; under the picture one line names the time on screen ("4:05 PM · 7 min before lined up · Golden hour").
- Camera: the status says "● Lined up" at the moment, else "Lined up at 4:12 PM · 7 min later". The lens scale's stops
  are 14, 24, 35, 50, 85, 135, 200, 400 mm; a pinch still sets any length, the yellow mark sits between stops.
- The jump keys of 3.8 ("Sun lined up", "Now") became the band (or the orange key) and, in the band, Now.
- Pictograms (after Otl Aicher): circles and straight edges, solid, at the app's proportions (`KIND_WIDTHS`); the house
  has a 45° roof and an open door; the tower a pod and a mast. Lit on the shot's body's side by a thin edge (from the top
  when the body is straight behind); no edge once the body is 6° below the horizon. Used by Build the shot too.
- In AR the camera fills the picture's own part of the screen (in the Answer band that is not the whole screen).

## Decisions for 3.15.0 (2026-11-17)
Owner: "Can I tilt to see the map in 3D? As an option"; offered a 3D view beside the flat map, the whole map moved to
MapLibre, or a drawn bird's-eye view; the owner chose the whole map ("2 is eventual, so do it"). Design:
`docs/superpowers/specs/2026-11-17-3d-map-design.md`. Claude's calls (not yet confirmed):
- MapLibre Native for Android, BSD and keyless, with the app's raster looks (Map, Terrain, Clear streets) and colours as
  before, and OpenFreeMap's vector buildings as a fill-extrusion layer shown only while tilted (more than 8°). No new
  map style: the owner's looks and colours stay.
- Two fingers tilt and turn it; the **3D key** (a cube under Go to my location) tilts it to 55°, and again lays it flat
  with north up. Tilted, the credit adds "Buildings © OpenMapTiles, OpenFreeMap"; the scale hides (no single scale).
- Settings, **3D map**: On (default where the phone can run it) / Off (the flat map of before, osmdroid). The change
  restarts the screen. A failed start falls back to Off by itself and says so once.
- The screen tests and Ali keep the flat map (MapLibre's engine is native code a computer cannot run); the 3D style is
  checked by `MapStyleTest` and the MapLibre style validator, and was rendered once in Chromium over Al Khobar.

## Decisions for 3.14.1 (2026-11-17)
Owner, from the phone in Al Khobar: "I don't see any" buildings, the subject (a tree) put between buildings on purpose.
Overpass (3.14.0's source) refuses many clients, and the app said nothing when it failed. Claude's calls (not yet
confirmed):
- The buildings come from OpenFreeMap's vector map tiles (the same OpenStreetMap buildings, as map tiles from a CDN;
  free, no key; the address of the week's build read from its TileJSON every 3 days). Credit in Settings.
- In Al Khobar no building has a height or floors mapped (618 in one square kilometre, none with either): the tile's
  stand-in 5 m is read as "not mapped" and 9 m is guessed; the words say so.
- Never silent: When says "Getting the buildings this way…", "Buildings could not be fetched (no network?)", or the
  highest building this way and whether heights are guessed; the viewfinder has one line under the body's
  ("Buildings: 361 near · heights guessed (about 9 m)").

## Decisions for 3.14.0 (2026-11-16)
Owner: buildings as obstacles, in the sums and in the viewfinder, optional; the reason: a sunset the app gave went
behind a block (so the times matter most). Asked whether to switch to Google Maps for its data: no (a key and billing,
and its terms forbid using its buildings in our own sums). Design: `docs/superpowers/specs/2026-11-16-buildings-design.md`.
Claude's calls (not yet confirmed):
- OpenStreetMap buildings through Overpass (replaced by OpenFreeMap tiles in 3.14.1); a failed request is not
  repeated for a minute. Building parts and roof shapes are left out; buildings stand on your ground.
- Line up a street: buildings hide the body like the land does (`hiddenAll`); the sheet names the highest ("A building
  680 m this way, 120 m tall, hides the sun below 9.6°: those times are left out", "about" and "its height is a guess"
  when guessed). The subject's own building (holding the subject point or within 15 m of it) and yours never count.
- Build the shot: only the spot shown is checked, towards the body at its first time, after the picture has stood still
  for 1.2 s; a building in the way is said in the red line, Stand here stays enabled (a height can be a guess). Not on a
  drone.
- Viewfinder and Your shot's picture: each building a flat dark silhouette (one wall per edge) with a faint lit top,
  hazier far away; those beyond the subject drawn behind it, nearer ones in front. Not in AR or Build the shot's
  planning sky. While walking the last buildings fetched are drawn; they are fetched again where you let go.
- Settings, Buildings: Count them (default) / Leave out.

## Decisions for 3.13.3 (2026-11-16)
Owner: "the big sun ... moves fully, to be replaced with the moon". Claude's call (not yet confirmed): during the change
the cover's body slides off past the screen's right edge on a slight arc while the other slides down into the corner
(smooth start and stop), instead of the disc changing colour in place; the moon brings its craters with it.

## Decisions for 3.13.2 (2026-11-16)
Owner on Home: "make a cool transition between the two [Sun / Moon], with intermediate transition in between; also make
the Sun and Where bold and coloured, the rest smaller and keep it in two lines only". Claude's calls (not yet confirmed):
- The change plays for 620 ms before the colours change (Reveal, as before): the body leaving dims (the sun eclipsed by
  a passing shadow), the one arriving brightens with a glow (the sun's rays burst), the underline leaves towards the
  other name and arrives from it, and the cover's disc turns from one colour to the other as the moon's craters fade in
  or out. With animations off it is immediate.
- The cover's words: "Get the sun exactly where / you want it in your photo." on two lines; "sun" (or "moon") and
  "where" 28 sp, bold, in the body's colour; the rest 19 sp.

## Decisions for 3.13.1 (2026-11-16)
Owner on the viewfinder's ruler: "This is confusing"; and "complete the moon circle and put some moon face minimal
details" (Home). Claude's calls (not yet confirmed):
- The ruler (`TimeRuler`): each stop is a pin with its name and time ("Lined up 3:21 PM", "Sunset 4:08 PM") on one
  of two lanes so none hides another; the light strip names its stretches (Day, Golden, Blue, Twilight, Night); the
  time shown is a white playhead with a handle, not the accent the marks use. The marks say "Lined up" (the body is
  the shot's).
- Home (Pocket map, moon): the whole disc, no bite, with five soft craters a shade darker and a faint lit rim.

## Decisions for 3.13.0 (2026-11-16)
Owner, after asking whether the subject's height can be changed: "How about you change angle instead of subject
height?" Claude's calls (not yet confirmed):
- Under Where the sun sits (When, Behind subject) a **Sun height** slider, 0° to 20° in tenths. The pictures move it to
  their angle; a drag picks any angle (`line_sits` 4, `line_angle`), lights no picture, and lists the times the body
  lines up within 0.75° of it (`ANGLE_SLACK`: about a day's change). Under the slider: "From here the subject's top is
  3.6° up and its foot -0.2°", so the height only serves as a guide. The kind and Height keys stay as they were.

## Decisions for 3.12.2 (2026-11-16)
Owner on When's new choice: "This is not clear". Claude's calls (not yet confirmed):
- Where the sun sits (Line up a street) is a row of picture tiles (`Ui.sitsChoice`): the subject as a block on the
  horizon and the body drawn where the choice puts it; under the row one sentence says what the choice means from
  here, in degrees ("Just above the subject's top: 3.6° to 4.6° up"). A short line says what hides low times
  ("Hills this way hide the sun below 1.2°").
- The land's horizon ignores lone spikes: each point counts no higher than the lower of its neighbours, so a tower in
  the surface model is not a hill (the owner's flat coast read "the land rises 3.7°").

## Decisions for 3.12.1 (2026-11-16)
Owner: "why is the building under the horizon?" (Build the shot, a spot 63 m from a 30 m building, "12 m above its
ground"). The 12 m was not ground: Open-Meteo's 90 m model is a surface model that holds roofs in towns, and two
points a cell or two apart can differ by a building. Claude's calls (not yet confirmed):
- Closer than 300 m (`Heights.LEVEL_WITHIN`), you and the subject are on level ground: no rise, no "m above its
  ground" words. Farther, the model's difference is used as before.
- The land's horizon is read from 1 km, not 150 m, so a nearby roof in the model is not taken for a hill.

## Decisions for 3.12.0 (2026-11-16)
Owner asked whether Line up a street should offer where the sun sits on the line, and take the ground's height into
account so a sun "on the horizon" is not hidden. Both were missing: every crossing was listed, and only the subject's
own ground hid a low sun. Claude's calls (not yet confirmed):
- When (Behind subject) has **Where the sun sits**: Any (default), Horizon, Middle, On top (`line_sits`). Horizon =
  up to 2° above what hides it; Middle = within a quarter of the subject's height (at least 0.3°) of its middle; On
  top = from the subject's top to 1° above it. The card's Soonest and All times follow the choice; with none in the
  year the card says so and points to Any.
- The land's horizon (`FrameMath.landHorizon`, tested): the ground's height at 40 points along the way the body must be,
  1 km to 25 km from you (Open-Meteo, one request, remembered; from 1 km since 3.12.1), less the earth's curve with standard refraction; the
  highest angle from your eye. Times with the body lower than it, or than the subject's ground behind the subject, are
  left out, and the list says how high the land rises when it does. Ground only: buildings and trees are not counted.

## Decisions for 3.11.0 (2026-11-16)
Owner, from the phone: an empty area under Your shot's keys when pulled up; the tutorial's Line up a street picture
"empty?"; Build the shot's picture should be a "viewfinder". Claude's calls inside that (not yet confirmed):
- A pulled sheet is never taller than what it holds (its full and half heights stop at its content).
- Tutorial: Line up a street plays the subject tapped, you dropped at the other end, the line, and the sun coming to
  the map's edge along it; Build the shot is a viewfinder (sky, horizon, a tower, frame corners) where the sun comes
  down to sit just on top and its ring shows it placed. Each plays over 2.4 s; a tap plays it again. The street card's
  words follow the subject-first order.

## Decisions for 3.10.0 (2026-11-16)
Owner, from the phone: Build the shot's settings should be a card that goes up and down so the picture is bigger, with
the folded settings (the most important) together with When and Stand here; "ask what target" on Your subject;
"color code" sun and moon in Saved; the keys on Home's crescent unreadable; walking, the small map should point to the
subject. Claude's calls inside that (not yet confirmed):
- Build the shot: the panel is a card with a grab bar. Folded: the answer (where to stand, times, light), When and
  Stand here, the red reason, and the row naming the settings. A drag up (or a tap on the row or the grab bar)
  unfolds Where the sun sits, Light, Look in, How full, Distance and On foot / Drone under it; a drag down folds them.
  The picture takes the room the folded card leaves. Help's words show folded or not.
- Your subject asks **What is it?** once placed (Person, Tree, House, Building, Tower, Peak, each with its usual
  height); the pick sets the kind and the height. The map key names them ("Subject: Big Ben, building 30 m").
- Saved: each shot and trip moment starts with the body drawn in its colour (sun amber, moon violet), and "Sun" or
  "Moon" in its words is in that colour.
- Home (Pocket map): Saved and Settings sit on their own paper keys, readable over the disc and the crescent.
- Walking (Move): the small map turns so the subject is straight up, as the stick's up walks towards it.

## Decisions for 3.9.0 (2026-11-16)
Owner, from the phone, on 3.8: "still no map view of the timeline"; the other times should be grouped ("date from - to /
time from to"), opened when interesting, and split by month; "once done there is no closure, put close as an
option"; on Your shot "I can't see the map and it's messy"; the sun should be at the end of the map, not the line's
own length beyond the subject; rename the app SunWhere. Claude's calls inside that (not yet confirmed):
- The name on the phone and in the app is SunWhere; the package, repository and applicationId stay.
- Day chart: the sheet's head is one line ("Soonest Apr 1 · 05:30", See it), the half height is 60% so the chart and
  the time read above it fit, and the map centres on you, showing the path and the body at the chosen time.
- All times: the year's lined-up times (one a day, `Astro.CHOICES` = 370; a full-year scan, about a third of a second
  on a computer) under month headings; days in a row are one run ("Tomorrow to Nov 30 · 3:21 PM to 3:31 PM · 2° to
  5° up · 14 days", Soonest and Low sun tags) that opens to its days and folds again; a single day is its own row.
- When's card: the day in the label ("Soonest · Tomorrow"), the time alone large, the bearing beside it.
- Your shot pulls like When (half height 50%): the answer, Open the viewfinder and **Close** in its head; the picture,
  where you stand, Save / Remind me / Directions and the other tool under it ("Pull up for the picture, Save, Remind
  me"). It opens at half, so the map shows.
- The sun's badge stands where the line leaves the map's open part (under the bar, above the sheet), not at a distance
  from the line's length; map labels stay inside the map.
- The map's credit and scale sit at the foot of the map, above the sheet; the map keys step aside when the map is too
  short for them. The Clear streets credit is one line ("© OpenStreetMap contributors · Tiles HOT, OSM France").

## Decisions for 3.8.0 (2026-11-16)
Owner, from the phone, after trying 3.7: the flow "my location then target" is inconsistent; the "Use the last" bar is
"so annoying, remove it"; wasted space; the viewfinder not improved at all; nothing seen when moving the day chart
("the map should be squeezed when pulling a card"); the card should hint there is more; "It tells me to use the
soonest but I don't see other options"; Times / Day unclear, "separate tabs"; Build the shot should look like the
other tool, with its icon, on the last page; choosing a day should open the viewfinder at once; "more like the
rendering I sent before"; change the map's style and colours. Claude's calls inside that (not yet confirmed):
- Both tools start with the subject; Line up a street then asks where you stand (Use my location first in its words).
- The map ends where the sheet begins (`squeezeMap`) and the line is fitted again when the sheet comes to rest.
- The sheet says "Pull up for ..." under itself while it hides part of its body; a tap on it pulls it up. A sheet with
  nothing to pull is as tall as its head (the empty band under the keys is gone).
- When: the soonest is a card (rendering-style: the time large, the bearing beside it, where the body stands under);
  its key is **See it in the viewfinder**. Under it two big tabs, **All times** (list icon) and **Day chart** (chart
  icon). All times lists the other lined-up times (up to eight, one a day, `Astro.Found.all`) as ruled rows with a
  viewfinder icon; a tap opens the viewfinder at once (Your shot is under it: Back shows it). The soonest is not
  repeated in the list.
- Your shot from Line up a street ends with **Build the shot** as "The other tool", with its icon.
- Viewfinder: the scene fills the screen; Back, Remind me, Share, Save on top; over the scene at the bottom the lens,
  the time large, the day and the light, where the body is, the ruler, and Compass, Turn, Height, Move.
- Home (Pocket map, Readout): Sun / Moon as two large drawn bodies with their names, the chosen one underlined.
- Map look: the layers key opens a sheet with Style (Map, Terrain, **Clear streets**: the Humanitarian OSM style,
  keyless, hosted by OSM France) and **Colours** (Soft, the design's own; Natural; Paper; Grey; Dark), each seen at
  once on the map; the same two are in Settings.

## Decisions for 3.7.0 (2026-11-16)
Owner asked for a redesign inspired by Braun (two reference images: a Braun-style mock of this app, and Braun
navigation concepts), two choices, then "push both to main, so I can switch between them and test". Kept, as the
owner said: the flow and words, amber sun / violet moon, a dark theme, IBM Plex Sans Arabic; not a copy of the mock.
Claude's calls inside that (not yet confirmed):
- One setting, Design, first after How it works in Settings: Classic, Pocket map, Readout. Pocket map is the default so
  the change is seen at once; Classic stays to compare. A change restarts the screen like Theme.
- Both Braun designs: their own neutrals (Pocket: paper #ECEBE7 / navy night edition; Readout: grey case #E2E1DD /
  black), an orange sun and a violet moon (dark ink on the orange), You blue, the subject an ink mark, the line in the
  body's colour (was green to blue). Only with the accent on Sun and moon; a fixed accent stays as it was.
- Pocket map: Home is a cover (the tagline large, the sun as a quarter disc in the corner, the moon bitten into a
  crescent), the tools as ruled rows; the point steps and Your shot end in a map key (You, Subject, Line; Stand, Sun).
  The map keeps more colour, warmed towards the paper.
- Readout: Continue, the point steps and Your shot lead with a graphite readout (the moment or the subject large,
  three numbers under it); main buttons and fields are round; Save, Remind me, Directions are round keys with their
  names under them; the viewfinder's keys are rings. The map is greyer.
- Not redesigned yet in either: Build it, When, Saved, the viewfinder's scene, the tutorial (they take the colours).

## Decisions for 3.6.0 (2026-11-16)
Owner: "proceed with the mode and other items from the roadmap if they do not conflict" (the roadmap's two tools
instead of three, and its open items). Claude's calls inside that (not yet confirmed):
- The name stays **Build the shot** (the owner knows it; one rename fewer). Home and the tutorial show two tools.
- Where the sun sits is three keys in Build the shot (Horizon, Middle, On top) under the words, always in sight; a
  key snaps the body there (centred on the subject) and keeps it there while the subject, the lens, the height or
  the distance change; dragging the body lights none. Starts on On top (3.0's default, the same `stand_place`).
- Distance (a slider, 20 m to 20 km) and How full (moon: Any, Half or more, Nearly full) are in the folded
  Settings; the summary row names them. Distance is the pinch's other face: it sets the subject's size.
- Not carried over: the lens range (Build the shot has its lens bar; `RangeBar` is gone), the spot list on the map
  (the When chooser lists the days, each with its own spot), the still picture of How you want it, Behind you
  (Build the shot places the body in the picture, so it is always behind the subject).
- A route saved in Where to stand continues in Build the shot (subject → subject, the rest → the compose view,
  Your shot → Your shot).
- Line up a street's link "Where to stand" (under the times) is now "Build the shot", keeping the subject.
- Roadmap items done: When stays on one line (the words shrink to 11.5 sp, then end in an ellipsis; When gets more
  room than Stand here); the trip lists each line's moments once; a subject on higher ground hides the times the
  body is below its ground (behind the subject only), with a line saying so; the Terrain map at night is dimmed less
  (72%, not 55%) so its lines and names stay readable (not yet seen on a phone).

