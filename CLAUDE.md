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

## SunLighn: what it is
Photography helper. Pick your subject (search for it or tap it); the app says where to stand and when the sun and
the moon will sit exactly on that line, and shows the shot as the camera will see it. It gives the answer, not a
sun path to read, which is the point: PhotoPills does the latter. Rebuilt as 2.0.0 after the audit in
`AUDIT-2026-11-14.md`, and as 3.0.0 around Home and three tools (`docs/superpowers/specs/2026-11-15-three-tools-design.md`;
read it before changing screens).

**The route (3.0).** The app opens on **Home** (`HomeScreen.java`): Sun / Moon (each with its icon), a Continue card
for the tool left unfinished, and three tools, simplest first, each a row card with its name and words on the left
and its icon on the right, all on one screen (3.3, owner's sketch; was icon in the middle, 3.1). Each tool is a few steps (`Flow.java`: tool, body, step; Back walks
back). Every tool ends on **Your shot**: the moment, Open the viewfinder, Save, Remind me, Directions.
1. **Line up a street**: Where you stand → Your subject (the other end) → When (that body's times; Times / Day) → Your shot.
2. **Where to stand**: Your subject → How you want it (Light, Subject kind and height, Where the sun sits: on the
   horizon / behind the middle / just on top, Distance with the lens that fills a third, How full for the moon,
   On foot / Drone, Behind subject / Behind you) → the spots by day → Your shot.
3. **Build the shot**: Your subject → the compose view (drag the body from its ring; drag anywhere else to frame
   the picture, swing and tilt, with Centre to reset; pinch the subject; heights it can never reach in the days
   chosen, or with every spot on water or in a building, are shaded red) → Your shot. The framing travels to Your
   shot, the viewfinder and the saved shot (3.3).
The step bar on top of the map says `2 of 4 · How you want it`, has Back and a Sun / Moon dropdown. Sun and moon
are never in one list. Saved and Settings are on Home.

- `Astro.java` sun and moon positions plus the search. Plain Java, tested in `app/src/test` against PyEphem values.
- `MainActivity.java` the map (osmdroid, OpenStreetMap, no key), the step bar and search, the map keys, the line,
  each step's sheet (one section per tool), Your shot, and saving as you go.
- `Sheet.java` the sheet that rises from the bottom: a grab bar, a head that always shows (the answer in one line
  and the main key) and a body that scrolls. Three heights (peek, half, full); drag the head, fling, or tap the bar.
  With nothing to pull (the first steps) it is as tall as its words.
- `Sky.java` where the sun or moon can and cannot be for Build the shot (tested). `PickSheet.java` the list a
  dropdown opens.
- `FrameScreen.java` + `FrameView.java` + `TimeRuler.java` the **viewfinder**: full screen and always dark.
  The scene (sky colour and glow from the sun's height, a deep blue night that is never black, stars, the sun and moon at real size on their paths with
  the hours marked, the land, the subject to scale when its height is set, a compass along the top, heights down
  the side, bright corners for what the lens takes in). Pinch or the − + keys change the lens (snaps to 14 to
  400 mm), drag the sky or the ruler for time (the ruler settles on "on the line", sunrise, sunset), up/down tilts,
  double tap aims again. Keys, each with its name under it: Compass (follows the phone's compass and tilt, no camera), Turn
  (portrait/landscape), Height, Move (a joystick to walk where you stand: up towards the subject, sideways
  sideways; the scene follows live, the moment is worked out again on letting go; 3.4), Share (text or
  calendar), Save. The bell sets the moment's reminder. It shows only the shot's body: nothing about the other
  (3.4). Maths in `FrameMath.java`, tested.
- `Light.java` the names of the light by the sun's height (Daylight, Golden hour, Blue hour, Twilight, Night) and
  one colour for each, used on the ruler, the answers and the Day read-out.
- `Heights.java` the ground's height above the sea where you and the subject stand (Open-Meteo elevation, free,
  no key, a 90 m terrain model: ground, not buildings; up to 100 points per request; remembered per spot of ~11 m).
  `FrameMath.rise` = the subject's ground less yours less your eye (1.6 m). Used by Where to stand (each day worked
  out again for its spot's ground), Build the shot (the distance), Your shot (the words) and the viewfinder (the
  subject's foot and top, a hill under a subject on higher ground). Without a network the ground is level.
- `Fmt.java` words for times, days, distances. `Zones.java` the place's time zone (Open-Meteo, free, no key,
  remembered per place; the phone's zone until the answer comes or without a network).
- `Places.java` + `SearchScreen.java` search by name (OpenStreetMap Nominatim: one search per press, never while
  typing, a real User-Agent) or typed coordinates; the result is the point the step asks for. `Places.reverse`
  names a saved shot by its place (one request per save, only with a network).
- `Trip.java` the **Saved** screen, two tabs: **Shots** (saved shots: a line, a moment or "any time", a name, the
  bell; press and hold to rename) and **Trip** (the days away and every moment on the saved shots' lines). A shot
  opens Your shot at its moment. `Shoots.java` keeps what a reminder needs (lens, place) by its key.
- `Reminders.java` the bell: a notification 30 minutes before a planned shoot; alarms set again after a restart.
- `Tutorial.java` first-start tutorial (four cards: Sun or moon, whose picture shows the switch and the colours
  changing in a circle from the finger, then the three tools with their Home icons; Skip always there, replay from
  Settings, "How it works"; a tap on a picture plays it again).
- `Reveal.java` the change of colours (Sun / Moon, Theme, Accent): a picture of the old screen lies on top and a
  circle grows from the finger until the new one fills it (560 ms, CrossPaths ease-out). With animations off it is
  immediate. Sun / Moon changes in place (`Palette.setMoon`); Theme and Accent restart the screen and reveal after.
- `SettingsSheet.java` the gear (on Home): How it works, then Theme, Accent and Map look as dropdowns. Accent starts on
  **Sun and moon** (amber for the sun, violet for the moon), then the five fixed accents.
  A theme or accent change restarts the screen (`recreate()`); everything is saved as you go and the sheet reopens.
- Icons are the CrossPaths set (`docs/design/reference/mobile/icons.js` in DevStore) as vector drawables
  (`res/drawable/ic_*.xml`, same path data, 1.8 stroke), tinted from code. Add new icons the same way. SunLighn's
  own, drawn in that style (3.1): `ic_sun`, `ic_moon`, `ic_tool_line` (a street running to the sun on the horizon),
  `ic_tool_stand` (a question mark over a cross: the spot), `ic_tool_build` (a viewfinder, the sun just on top of
  the subject). The sun and moon
  pictures (`Ui.GlyphView`) and the pin (`Ui.drawPin`) are drawn in the same style. Font: IBM Plex Sans Arabic,
  bundled in `assets/fonts` (SIL OFL, licence beside it); weight carries the hierarchy, never a second typeface.
- `Ui.java` colours and shapes from the DevStore design language (`docs/design/DESIGN.md` in DevStore): navy night
  and pale day themes, one accent (rewritten in place when the body changes; never captured), flat bordered surfaces, drawn line icons, route green and blue for the line.
- Map looks: `Map` (OpenStreetMap, tinted navy at night) and `Terrain` (OpenTopoMap), cycled with the layers key and
  remembered. Both need no key. The credit text on the map is required by their licences: keep it, update it per style.
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
| Where you stand | Next (greyed until placed; the words say why) | tap, drag, search, my location | the pin; the sheet says "You are on the map. Next: your subject." |
| Your subject | Next | tap, search, drag either pin | the line; "Your subject: Big Ben. Next shows when the sun lines up with it." |
| When | Use the soonest, or a time | Behind subject / you, date, Sun / Moon, drag a pin | the answer and the line redraw |
| How you want it | Find spots | Light, subject, height, where the sun sits, distance, lens, how full, on foot / drone, side | the still picture of the shot redraws |
| Spots | Stand here | a row or a spot on the map | the spot on the map |
| Build the shot | Stand here (beside When) | drag the body, frame (drag the picture), pinch, lens, height, turn, When, Settings (folded: Light incl. golden hour, Look in, On foot / Drone), Help | the picture itself; one line of words |
| Your shot | Open the viewfinder (or tap the picture), Save, Remind me, Directions | move a point (a new moment, said in the bar) | the still picture of the shot |
| Viewfinder | Back, Save, the bell | lens, time, tilt, height, turn, Move (joystick: walk), compass | the scene, live as you walk |
| Saved | open a shot, the bell | rename (hold), remove (Undo), trip days | the list |

## Choices have "Any", results are grouped by type (owner, 2026-11-15; keep it so)
Where a choice narrows the answer (Light, How full, ...), it offers **Any** unless Any means nothing (Where the sun
sits, a lens). With Any, the results come **grouped by type**, each group under its name (Sunset, then Sunrise),
never mixed in one list. Done for Light in Where to stand and Build the shot (3.4.1).

## Starting clean (owner, 2026-11-15)
A tool started from Home starts with no points where it asks for one (no old You pin when picking the subject);
the bar offers **Use the last** to bring the last points back in one tap. Continue keeps everything as it was.

## Controls (one shape, one meaning; keep it so)
| Shape | Means | Where |
|---|---|---|
| Words with a line under one (`Ui.tabs`) | Switches what is shown below | Times, Day; Shots, Trip |
| Two parts in one track (`Ui.segmented`) | An either/or setting | Sun / Moon on Home (with their icons); Behind subject / Behind you; On foot / Drone |
| Dropdown (`Ui.dropdown`, opens `PickSheet`) | Pick one from a list | Light, Subject, Where the sun sits, Lens, How full, Look in, When; Theme, Accent, Map; Sun / Moon in the step bar |
| A row that names settings, with a chevron | Unfolds them in place | Build the shot's Settings |
| Slider, pinch, ruler | A value | Distance; lens; time |
| Filled accent button | The one main action of the step | Next; Use the soonest; Find spots; Stand here; Open the viewfinder |
| A key over the viewfinder | Always an icon with its name under it | Back, Compass, Turn, Height, Move, Share, Save |
| The bell / Remind me | The shot's reminder (saves the shot too); lit when set | Your shot, Shots, Trip moments, the viewfinder |
| Three dots | What is done to the line as a whole | Save, Clear |
| A row card: name and words on the left, icon on the right | Starts a tool | Home |
| A small still picture of the shot | What the settings or the moment look like; on Your shot a tap opens the viewfinder | How you want it, Your shot |
| The bar above the sheet (`say`) | What just happened, with Undo | Cleared; You off the map; reminders; hints |

**No pills** (owner, 2026-11-15): a pick from a list is always a dropdown; `NoPillsTest` keeps `Ui.chip` out.

Map touches: on a step that asks for a point (Where you stand, Your subject) a **tap** places or moves that
point, and so does a press and hold. On other steps a tap moves nothing. **Drag** a point to move it (it rides above the finger). **Press and
hold** the map (past the point steps) to be asked which point moves there. Tap the sun badge at the end of the line to send it to the
other end. On the spots step, tap a spot on the map to pick it.

## Back (test this on every screen after any change)
| Where | Back does |
|---|---|
| A dropdown list, the tutorial, Settings, a dialog, the share menu, the joystick | Closes it, nothing changed |
| Viewfinder | Back to the step it was opened from (Your shot). Lens, orientation and height are saved as you go |
| Your shot opened from Saved | Back to Saved (3.2.2) |
| Build the shot, compose step | One step back (Your subject); the picture's points stay |
| Search | Back to the step, nothing set |
| Saved | Back to Home (where it is opened). Saved as you go |
| Sheet pulled to full | Goes back to half |
| A tool, step 2 or later | The step before. Points and settings stay |
| A tool, step 1 | Home |
| Home | Leaves the app (the system default for the first screen) |

Entering Where to stand or Build the shot sets You aside (they exist to find it): "Stand here" places a new You,
and back on Home the old one returns. Build it is kept as it was left while Your shot shows. A screen restart
(dark mode at sunset) returns to the same step. Switching Sun / Moon on Your shot steps back to choose again.
The phone's permission dialogs have no other layers. If a screen is added, add its row.

## Words (one word per idea)
Changed in 2.1.0 after a first-time-user test (`AUDIT-2026-11-15-first-use.md`): the words on the left of each
arrow are gone from the screens.
**You** = where the photographer stands. **Subject** = what you photograph, the thing the sun or moon lines up
with (never "object"); the first screens say "what you want to photograph" before using the word.
**Line** = the two together. **Behind subject / Behind you** = which end of the line the sun or moon stands at.
**Lined up** (was "on the line") = the body is exactly on your line. **Soonest** (was "Next") and **Low sun /
Low moon** (was "Low") are the two answers per body. **Times** and **Day** are the two tabs of the sheet.
**Viewfinder** (was "Frame") = the full-screen view of the shot. **Frame** (verb, 3.3) = swing and tilt the
picture in Build the shot; **Centre** = the key that undoes it. **Dates** (3.3): one day "Sunday, Nov 22" (the
weekday in full, never "Sun"), "Nov 22" where a line is short, ranges "Nov 18 to 20"; "Today", "Tomorrow". **Saved** = the screen with the tabs **Shots**
and **Trip** (the days away). **Moment** = one time the sun or moon sits on a line. **Reminder** = a shot's
notification. **Where to stand** = the tool that finds a spot for You, by light: **Sunset, Sunrise** or
**Moonset, Moonrise** (for the body chosen). **On foot /
Drone** = whether spots are kept to land. **Compass** (was "Live") = the viewfinder follows the phone.
Changed in 3.0.0: **Home** = the first screen. **Line up a street**, **Where to stand**, **Build the shot** = the
three tools (Build the shot replaces "Plan a frame"). **Your shot** = the last step of every tool. **Shot** = a
saved line with a moment (replaces "Shoot" and saved "Line" in Saved). **Move** = the viewfinder key that moves
You. **Directions** = opens the phone's maps app at the spot. **Where the sun sits** = on the horizon, behind the
middle, just on top. **How you want it** = the criteria step.
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

