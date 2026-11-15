# SunLighn 3.3: frame the shot yourself, and see every change

Date: 2026-11-15. Owner's request after 3.2.2.

## Why

The owner wants every step to answer three questions at a glance: **what can I do next, what can I adjust, and
will I see it**. The example: in Build the shot you can move the sun but not the picture; the subject is always
centred and the tilt is fixed. And "Sun, Nov 22" on a moon answer reads as the sun (Sun is the weekday).

Success: in Build the shot the owner can put the subject off-centre and tilt for more sky or ground, sees it as
they drag, and finds the same framing in the viewfinder and in the saved shot. No date can be read as the sun.

## Goals

- Build the shot: change the composition (swing left/right, tilt up/down) by dragging the picture.
- The framing carries through: Your shot → viewfinder, and Save / the reminder keep it.
- How you want it (Where to stand): a small live picture of what the settings mean.
- Your subject step (Line up a street): once set, the sheet says so and what Next does.
- Dates never abbreviate the weekday to "Sun".
- Your shot shows the shot as a small picture (tap: the viewfinder), not only words.
- Home fits all three tools on one screen (owner's sketch, 2026-11-15).

## Non-goals

- Panning in the viewfinder by drag (a sideways drag there is time; double tap still aims again).
- Changing where you stand by framing: framing never changes the answer.

## Anti-goals

- A mode switch the user has to remember.
- A drag that moves something the finger did not touch.

## Approaches (picked: A)

- **A. Touch what you want to move** *(picked)*: a drag that starts on the sun's or moon's ring moves the body;
  a drag anywhere else moves the framing; pinch sizes the subject; a "Centre" key resets the framing. Same rule as
  the map (drag a pin to move it, drag the background to move the map).
- B. A "Move: Sun | Frame" switch above the picture: clearer to a first-timer, but one more control and a mode to
  forget.
- Two-finger drag to frame: rejected, it collides with the pinch.

## The design

### Build the shot: framing (`FrameView` compose mode, `ComposeScreen`)

- The body's grab zone is its ring plus about 24 dp (at least 48 dp across). A drag starting there moves the
  body, as now. A drag starting elsewhere frames: horizontal swings the camera (`azCenter` = subject bearing +
  `panDeg`), vertical tilts (`pitch`). Both follow the finger one to one (the scene moves with it, like a map).
- Framing is limited so the subject never leaves the picture (|pan| up to half the picture's width less a margin;
  tilt within what keeps the subject's foot or top in view).
- The words under the picture do not change while framing (the answer depends on the body's place relative to the
  subject and the subject's size, not on where the camera points).
- A small **Centre** key (icon with its name under it, like the other keys on a scene) appears once the framing
  is moved, and puts the subject back in the middle at the default tilt.
- The hint under the picture says all three: "Drag the sun to place it. Drag the picture to frame it. Pinch to
  make the subject bigger or smaller."

### The framing carries through

- `panDeg` and `pitch` are saved with the planned picture (prefs, like lens and orientation) and passed to Your
  shot and the viewfinder: the viewfinder opens with the same composition (its centre = line bearing + `panDeg`,
  its tilt = `pitch`). Double tap there aims again (centred), as now.
- Save and Remind me keep them with the shot (`Shoots`, beside lens and orientation); opening a saved shot or a
  reminder shows that framing. Shots from Line up a street and Where to stand have no framing (centred), as now.

### How you want it: a live picture

- At the top of the sheet's body, a short (about 120 dp) non-interactive picture in the planning sky: the subject
  to scale at its height and the chosen distance, the sun or moon where "Where the sun sits" puts it, the lens from
  the distance line. It redraws as any setting changes (kind, height, where it sits, distance, behind subject/you).
  Built from `FrameView` in a still mode; no touch.

### The three questions, step by step (Line up a street, Where to stand)

| Step | Next | Adjust | See | Change |
|---|---|---|---|---|
| Where you stand | Next (greyed until placed; the words say why) | tap, drag, search, my location | the pin | none |
| Your subject | Next | tap, search, drag | the line | the sheet says it is set (below) |
| When | Use the soonest / a time | side, date, body, drag | the answer | none (the picture comes on Your shot) |
| How you want it | Find spots | eight settings | words only | the live picture (above) |
| Spots | Stand here | a row or a spot | the spot on the map | none |
| Your shot | viewfinder, Save, Remind, Directions | move a point (re-timed) | words only | the shot as a picture (below) |

### Your shot: the picture

- Under the moment, the same still picture as on How you want it, for this moment: the subject to scale, the sun
  or moon where it will be, the framing from Build the shot when there is one. Tapping it opens the viewfinder (the
  "Open the viewfinder" key stays as the main key).

### Home: all three tools on one screen (owner, 2026-11-15)

- Each tool is a compact row card: the name (bold) and its words (two lines at most) on the left, the icon in its
  disc on the right. Home's header, Sun / Moon, Continue and the three cards fit a 360 x 780 dp phone without
  scrolling. This replaces the 3.1 card (icon in the middle, words beneath).
- The Build the shot icon is redrawn as the owner sketched it: the viewfinder's corners, the sun at the top left,
  the subject standing in the middle (CrossPaths style, 1.8 stroke).

### Your subject step (Line up a street)

- Before a subject: "What is at the other end?" (as now). Once set: "Your subject: Big Ben" (or "Your subject is
  on the map"), and "Next shows when the sun lines up with it." The same on Where you stand once placed: "You are
  on the map. Next: your subject."

### Dates

- One day: full weekday, "Sunday, Nov 22"; "Today", "Tomorrow" as now; the year only over 300 days away.
- Where space is tight (the viewfinder's headline, Build the shot's words): "Nov 22 · 4:11 PM" (no weekday).
- Ranges: "Nov 18 to 20", "Nov 30 to Dec 2" (no weekdays).
- `Fmt` gets the three forms; every screen uses one of them. Trip's day headings ("Sunday, November 15") stay.

## Constraints

- Plain Java, custom views, no new libraries; `applicationId` unchanged; one release, 3.3.0.
- Tests: framing gestures in `ScreensTest` (drag off the body frames, on the body moves it, Centre resets, the
  viewfinder opens framed), `Fmt` date forms in `LightFmtTest`, the preview's presence and redraw. Pictures looked
  at. Ali runs the new build once.

## Left to the builder

Exact grab radius, the Centre key's place on the scene, the preview's height and labels, and wording of hints,
within the Words section of CLAUDE.md.
