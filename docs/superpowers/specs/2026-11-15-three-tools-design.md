# SunLighn 3.0: three tools, one route each

Date: 2026-11-15. Owner's request after using 2.1.0 on a phone. Replaces the "map is home" structure of 2.0.0.

## Why

The owner used 2.1.0 and did not know what to do next. After "Stand here" on the Where to stand sheet
(Moonrise, 350 m W of the subject) nothing said what had happened or what came next. Back minimised the app
from almost every screen. Sun and moon answers were mixed in one list, which made results hard to read.
2.1.0 shows every feature on one map and one sheet, and the route through them is left for the user to find.

Success: someone who opens the app can say within one screen which of three things they want to do. They
reach a finished, saved shot without wondering what to press. Back always goes one step back.

## What the owner decided (2026-11-15)

- **A home screen with three tools**, simplest first, and a Sun / Moon choice at the top. Each tool is a short
  series of steps.
  1. **Line up a street**: you and a subject; when does the sun (or moon) sit on that line.
  2. **Where to stand**: pick a subject and set clear criteria (its height, how high the sun is); get spots.
  3. **Build the shot**: make the picture first by placing the sun or moon. The parts of the sky where it
     cannot be are shown as impossible.
- **Sun and moon separated**: one body at a time through a whole tool.
- **Change your position from inside the viewfinder.**
- **Saving a shot is one press**, and it gets a name from the city or place by itself. Renaming is later and optional.
- **Back steps back** instead of leaving.
- **No pills anywhere.** A choice from a list is a dropdown (or something like it).

## Goals

- One route per tool, with a step bar that says where you are ("2 of 3 · Your subject") and what comes next.
- Every tool ends on the same **Your shot** screen: the moment in large words, and the four things you can do
  next (Open the viewfinder, Save, Remind me, Directions). That screen fixes "now what?".
- Back: closes what is on top, then goes one step back, then to Home. Only Home leaves the app.
- The last unfinished tool is offered on Home ("Continue"), so the answer is still two taps away.

## Non-goals

- Turning the map with two fingers, terrain height, weather, units or reminder lead time in Settings, Arabic
  (still open from the 2.0.0 audit).
- A camera picture behind the viewfinder.
- New map or search services. Reverse naming uses the same Nominatim service and rules as search.

## Anti-goals (a failure even if it works)

- A step that does not say what the next press does.
- Back that throws away a placed point or a setting. Back moves you between steps; what you placed stays.
- Sun and moon answers in the same list.
- A pill, or any control whose shape means two things.
- A save that asks for a name first.

## Constraints

- Plain Java, custom views, no new libraries (as now). `applicationId` unchanged. Release 3.0.0, `versionCode` 12.
- Nominatim: one request per user action, never while typing, a real User-Agent. Reverse naming is one request
  per save.
- Works without a network apart from map tiles, search, time zone and naming. Ground checks with no network
  rule nothing out (as now).
- Everything saved as you go (unchanged lesson), so Back never needs to ask.

## Approaches considered (how it is built)

1. **One activity, one map, a step stack** *(picked)*. MainActivity keeps the map, the two points, the overlays
   and saving. A small `Flow` model (tool, body, step) decides what the sheet shows; each tool's steps live in
   their own class. Back pops the step. The map stays alive between steps, so points and the camera do not jump.
2. Separate activities per tool. Android's Back would work for free, but every tool would rebuild the map, and
   passing points and the time zone between activities adds state to keep in step.
3. AndroidX Fragments + Navigation. A standard back stack, but a new dependency and a rewrite of every custom
   view around fragment lifecycles, for the same result as 1.

Owner chose A (home with three tools) and handed the build to Claude; approach 1 is Claude's call.

## The design

### Home (`HomeScreen.java`, new; what the app opens on)

```
 SunLighn                         [Saved] [⚙]
 ┌ Sun │ Moon ┐                     (either/or)
 ┌────────────────────────────────────────────┐
 │ Continue · Where to stand, Kingdom Tower ›│   only if a tool was left unfinished
 └────────────────────────────────────────────┘
 ┌────────────────────────────────────────────┐
 │ 1  Line up a street                        │
 │    You and your subject. When does the sun │
 │    sit exactly on that line?               │
 └────────────────────────────────────────────┘
 ┌ 2  Where to stand ──────────────────────────┐
 │    Pick a subject and how you want the sun. │
 │    Get the spot and the day.                │
 └────────────────────────────────────────────┘
 ┌ 3  Build the shot ──────────────────────────┐
 │    Place the sun in the picture first. Get  │
 │    where and when.                          │
 └────────────────────────────────────────────┘
   How it works
```

- The Sun / Moon switch is remembered and sets the body for the whole tool. The card words say "sun" or
  "moon" to match.
- Starting a tool clears nothing. The current points are offered as they are. Each tool's first step lets you
  keep or change them, and "Start fresh" is in the three dots.
- Home leaves the app on Back.

### Inside a tool: the step bar and the sheet

The map is on screen for every step that places a point. A **step bar** under the search field shows
`←  2 of 4 · Where to stand    Sun ▾`. The arrow is Back. `Sun ▾` is a dropdown that switches the body
without going Home (the answers recompute; nothing else changes). The sheet (`Sheet.java`, unchanged
mechanics) shows the step's one question and one main key.

Search and "Use my location" stay on the steps that place points. The search sets the point the current step
asks for, so the "It is my subject / It is where I stand" switch goes away.

### Tool 1: Line up a street (`LineTool.java`)

1. **Where you stand**: tap the map, use my location, or search. Main key: Next.
2. **Your subject**: the end of the street, a building. Tap or search. You can still drag You. Main key: Next.
3. **When**: the answers for the chosen body only. Headline: the soonest moment. Behind subject / Behind you
   (either/or). Tabs: **Times** (soonest, low, the list, Pick a date) and **Day** (that body's path only).
   "Never lines up" says why and offers the closest, with Line it up (as now). Tapping a time goes to step 4.
   Main key: "Use the soonest".
4. **Your shot** (shared, below).

### Tool 2: Where to stand (`StandTool.java`)

1. **Your subject**: tap or search. Main key: Next.
2. **How you want it**: the criteria, all dropdowns or values:
   - **Light**: Sunset / Sunrise (or Moonset / Moonrise).
   - **Subject**: kind (Person, Tree, House, Building, Tower, Peak, with their usual heights) and a height
     field. A height from the kind says "usual height" beside it.
   - **Where the sun sits**: *On the horizon* (0.5° up), *Behind the middle* (half the subject's angle),
     *Just on top* (the sun's lower edge on the top of the subject). Default: Just on top. This replaces the
     fixed 4° of `Astro.SUGGEST_ALT`. The altitude follows from height and distance:
     `top = atan(height / distance)`.
   - **Distance**: slider, 50 m to 5 km, with a line under it saying the lens that makes the subject fill a
     third of the frame ("About 135 mm to fill a third of the picture").
   - **Moon only, How full**: Any / Half or more / Nearly full (default Half or more, matching "Low" today).
   - **On foot / Drone** (either/or): on foot keeps spots on land (`Ground.java`, as now).
   Main key: "Find spots".
3. **Spots**: the days, grouped when they share a spot (as now), each with date(s), time and where to stand.
   Spots are on the map. Tap a row or a spot on the map to pick it. The main key is "Stand here", and its line
   says what it does: "Puts you on this spot and shows your shot."
4. **Your shot** (shared).

### Tool 3: Build the shot (`BuildTool.java` + `ComposeScreen.java`)

1. **Your subject**: tap or search. Main key: Next.
2. **Build it**: the full-screen compose view (today's Plan a frame). Drag the sun or moon, pinch the subject
   to size, set lens and height. Light kind and the days to search (Next 2 weeks / Next 6 weeks / Next year)
   are dropdowns. The run of days found is a dropdown when there are several.
   **Impossible sky**: bands of the picture are shaded and labelled where no day in the chosen days works:
   - above the highest the body gets in those days at that light (from `Sky.java`, below);
   - on foot, heights whose every spot falls on water or in a building at the distance the subject's size
     gives (from `Ground`; unknown counts as possible).
   Dragging into a shaded band lets go at its edge and says why in one line ("The sun is never that high at
   sunset in these days"). Main key: "Stand here".
3. **Your shot** (shared).

Left and right placement does not shade: moving the body sideways only moves you a little off the line, which
is always possible.

### Your shot (shared final step)

```
 Thu 19 Nov · 13:29                         ⋮
 Moonrise behind Kingdom Tower · 69% lit
 Stand 350 m W of the subject
 [ Open the viewfinder ]                     (main key)
 [ Save ]  [ Remind me ]  [ Directions ]
```

- **Save** saves at once and says "Saved as Al Shati, Jeddah", with Undo and Rename in the bar.
- **Remind me** is the bell (saves the shot too, then sets the reminder 30 minutes before).
- **Directions** opens the phone's maps app at the spot (`geo:` intent). No new service.
- Back goes to the step before (the spot list, the times, or the compose view).

### Viewfinder: changing position (`FrameScreen.java`)

A new key, **Move** (icon with its name under it, as the others), opens a map panel on the lower third of the
viewfinder with You on it. Drag You. The scene above redraws as you drag, and when you let go the answer for
the new spot is worked out again: the soonest moment, the ruler marks, "lined up". Back or the key closes the
panel. Moving in the viewfinder moves You everywhere (it is the same point). With the panel closed the
viewfinder is as now. The viewfinder also gets **Save** next to Share.

The viewfinder still draws both sun and moon (that is what the camera sees). The ruler's "lined up" stop and
the soonest moment follow the chosen body.

### Saved (`Trip.java`, reworked; `Shots.java` replaces `Shoots.java`)

- Tabs: **Shots** and **Trip**. "Lines" goes: a shot is a line plus, usually, a moment.
- A shot has You, Subject, body, which end, a time (optional; a shot saved from the Times list without picking
  a time has none), lens, orientation, subject height, a name and whether it reminds.
- **Naming**: on save, `Places.reverse()` asks Nominatim for the subject's place (zoom ~14: suburb or
  neighbourhood and city) and names the shot "<subject name or neighbourhood>, <city>". Until the answer
  comes, or with no network, it is "Shot 3". Unnamed shots are named next time Saved opens with a network.
  Rename by tapping the name.
- Migration: saved lines become shots without a time. Planned shoots become shots with a reminder. Reminder
  keys are kept so alarms survive.

### Back (replaces the table in CLAUDE.md)

| Where | Back does |
|---|---|
| Tutorial, Settings, a dropdown list, a dialog, the share menu, the Move panel | Closes it, nothing changed |
| Viewfinder | Back to the step it was opened from |
| Compose view (Build the shot step 2) | Back to step 1, picture kept as you left it |
| Search | Back to the step, nothing set |
| Saved | Back to where it was opened from (Home or a step) |
| Sheet pulled to full | Goes back to half |
| A tool, step 2 or later | The step before. Points and settings stay |
| A tool, step 1 | Home |
| Home | Leaves the app |

Back is handled with `OnBackPressedDispatcher` (AndroidX, already in use), not the deprecated
`onBackPressed`, so it keeps working with predictive back when the target SDK goes up.

### No pills: the dropdown (`Ui.dropdown` + `PickSheet`)

`Ui.dropdown(label, options, selected, onPick)`: a full-width bordered field with the label small above,
the current value, and a chevron. Tapping it opens a short list sheet from the bottom with a tick on the current
value. Back closes it. It replaces every `Ui.chip`:

| Today (pill) | 3.0 |
|---|---|
| Sunset / Sunrise / Moonrise / Moonset (Where to stand, compose) | Light dropdown (two values, for the chosen body) |
| Runs of days (compose) | Days dropdown |
| Subject kinds (height dialog) | Subject dropdown |
| Theme, Map look (Settings) | Dropdowns |
| Accent (Settings) | Dropdown whose rows show the colour and its name |

`Ui.chip` is removed so it cannot come back. The controls table in CLAUDE.md gets a **Dropdown** row
("Pick one from a list") and loses Pills.

### Words

New: **Home**, **Line up a street**, **Where to stand** (now a tool, same meaning), **Build the shot**
(replaces "Plan a frame"), **Your shot**, **Shot** (a saved line and moment; replaces Shoot and saved Line),
**Move**, **Directions**. Kept: You, Subject, Line, Lined up, Behind subject / Behind you, Soonest, Low sun /
Low moon, Times, Day, Viewfinder, Reminder, Saved, Trip, On foot / Drone. CLAUDE.md's Words section is
updated in the same change.

### Code layout

- `Flow.java` (new, plain Java, unit-tested): tool, body, step, back. Saved in prefs so the app reopens on Home
  with Continue.
- `HomeScreen.java`, `StepBar` (in `Ui`), `LineTool.java`, `StandTool.java`, `BuildTool.java`, `ShotStep.java`
  (new). Sheet rendering moves out of `MainActivity` (2290 lines) into these. MainActivity keeps the map,
  points, overlays, search, persistence and Back.
- `Sky.java` (new, plain Java, tested): for a place, body, light (rising/setting) and days, the highest and
  lowest altitude the body reaches on the line at that light, and per altitude whether any day works. Used by
  compose shading and the "never that high" words.
- `Places.reverse(lat, lon)`, `Shots.java` with migration, `Ui.dropdown` + `PickSheet`.
- Tests: `Flow` and `Sky` unit tests. `ScreensTest` presses through Home → each tool → Your shot → Save, and
  presses Back on every step, checking the table above. Pictures saved and looked at.
- A first-use test with a Haiku agent on the 3.0 pictures (as in `AUDIT-2026-11-15-first-use.md`), findings
  fixed or listed.

## Claude's calls (not yet confirmed by the owner)

- Approach 1 (one activity, a step stack).
- Default "Where the sun sits" = Just on top; the three choices and their maths.
- Moon "How full" dropdown, default Half or more.
- Tool 1 starts from where you stand (the street), and tools 2 and 3 from the subject.
- Your shot as the shared last step with Save / Remind me / Directions.
- Move in the viewfinder as a map panel on the lower third, not arrow keys.
- Impossible sky shown as horizontal bands only (height), with left and right always possible.
- "Lines" folded into Shots. Naming from the subject's place, zoom ~14.
- Day tab shows only the chosen body.
- Tutorial cut to the three tools (one card each) plus Skip, since Home now explains itself.

## Left to the builder

Exact spacing and type sizes (DevStore design language, as now), icons for Home cards and Move (CrossPaths set
style), the sheet heights per step, and the wording of one-line hints, following the Words section.
