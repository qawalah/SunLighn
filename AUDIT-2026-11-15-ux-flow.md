# UX and flow audit, 2026-11-15 (3.2.1 → 3.2.2)

Owner asked for a full review of the process flow with a UX audit, and a re-run of the first-time tester.
Two sources:
- **Claude**: the screens (ScreensTest pictures) and the code, against the 3.0 design spec
  (`docs/superpowers/specs/2026-11-15-three-tools-design.md`), CLAUDE.md's Back table and DevStore's shared lessons.
- **Ali** (a Haiku tester driving the real app through `AliDriver`, told the design): about 195 steps on 3.2.1,
  every tool with the sun and the moon, Saved, Settings, the viewfinder, Back everywhere, reopen, dark mode.

Every finding was checked against the code before fixing; some came from the driver, not the app.

## Fixed in 3.2.2

| # | Finding | Source | Fix |
|---|---|---|---|
| 1 | Moving You (hold or drag on the map, Move in the viewfinder) on Your shot kept a moment that no longer lines up; "Sun lined up" went to a stale time | Ali | The moment is worked out again for the new spot; the bar says so; an open viewfinder goes to it |
| 2 | Location asked right after the tutorial, on Home, before anything needs it | Claude, Ali | Asked once, when the first tool's map opens |
| 3 | "Never lines up" had a greyed main key; the way on was a small key lower down | Claude | "Line it up" is the main key, with a line saying what it does; the second key is gone |
| 4 | "Line it up" named one day, the sheet showed another | Ali | The bar says no date; the sheet's head has the soonest |
| 5 | Build the shot said "Fly" with no way to change it (inherited from Where to stand) | Claude | Its own On foot / Drone switch, shared with Where to stand |
| 6 | Build the shot said "Sunset" for a sun 9° up at 2:45 PM | Ali | The words say the light and the height: "Daylight, 9° up" |
| 7 | Back from Your shot re-picked the first row of the spots | Ali | The picked row stays while the spots asked for are the same |
| 8 | Back from a shot opened in Saved went into the tool | Ali | Back goes to Saved, then Home |
| 9 | Saved had a second save key, "Save the line on the map", for a line not in sight | Claude | Removed; Save on Your shot is the one way to save |
| 10 | Two shots named from one place had the same name | Ali | The second is "… 2" |
| 11 | The viewfinder's time broke onto a second line beside the light tag | Claude, Ali | The light sits under the time |
| 12 | Hour labels on the sun's path ran under the Back key and over the compass numbers | Claude, Ali | Not drawn above the compass strip |
| 13 | The Continue card said "your subject" and opened Where you stand | Ali | It names the step it opens: "Line up a street, Where you stand" |
| 14 | "Go back and change how you want it" did not say what to change | Ali | Names it: Where the sun sits, a longer distance, or Drone |
| 15 | The tutorial's first card never said what the app is for | Ali | Its first sentence does |
| 16 | Home and the tutorial listed different sun positions for Where to stand | Ali | Both list all three |
| 17 | Settings read "Map: Map" | Claude | "Map look: Map", the same name as the map key |
| 18 | "The closest it comes is below" | Ali | "… in the list below" |

## Not app bugs (the driver's)

- Hold to rename "did nothing" on a card: the driver sent the lift of the holding finger into the rename box it
  had just opened, and Android closes a dialog on a lift outside it. Fixed in `AliDriver`.
- A blank strip of map tiles: tiles still loading in the test.

## Left open (with the reason)

- "Sun, Nov 22" on the moon: Sun is the weekday. Full weekday names do not fit the viewfinder's headline; to look
  at with the owner.
- Picking a subject kind replaces a typed height with the kind's usual one, as designed (the hint says "usual").
- The first answer takes about 6 presses on Line up a street when you search both points; the last line is
  remembered and Continue brings it back in one.
- Terrain map at night is hard to read (OpenTopoMap under the night grading).
- Compass needs a real phone; not testable here.
- Trip lists a moment once per saved shot, so two shots on one line list it twice.
