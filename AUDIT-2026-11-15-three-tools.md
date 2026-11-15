# First-use test, 2026-11-15 (version 3.0.0)

How it was done: as in `AUDIT-2026-11-15-first-use.md`. A Haiku agent saw 12 pictures from `ScreensTest`, with one
neutral line on how each was reached, and no code or docs. Round one: knowing nothing. Round two: told what the
app does. Only still pictures; nothing here is about touch, motion or speed.

Confusion, 0 obvious to 5 lost: Home 2, Your subject 2, How you want it 3, spots 3, Your shot 2, Build the shot 4,
When 3, Day 3, saved 3, viewfinder 3, Move 5, Saved 1. It said it would give up at the Move map (2.1.0: at the
viewfinder).

Each finding: **[x] 3.0.0** fixed, **[ ]** open, **[-]** not a real problem (and why).

- [x] 3.0.0 Move had no way out but Back. It now has a Done button, and the tip names the pin.
- [x] 3.0.0 The viewfinder's bell had no name under it, unlike every other key. Now "Remind me".
- [x] 3.0.0 "4:01 PM to 3:58 PM": a run of days said its times backwards. Now earliest by the clock first.
- [x] 3.0.0 "Shaded red" under Build the shot, but the veil looked grey. Now it is red.
- [-] "Fly here" vs "Stand here": the test pictures were taken with Drone on, where the word is Fly on purpose.
- [-] "300 m ENE" became "451 m NE" after saving: two pictures from two different test lines, not one shot.
- [-] "4 of 4" followed by "3 of 4": the pictures came from different tools, not from one walk.
- [ ] "Line up a street" could say "a street or a gap between buildings" in its title, not only in its text.
- [ ] Next looks disabled before a point is placed and does not say why (the line above it does).
- [ ] "On the horizon", "Use the soonest" and "Soonest" are still read as jargon.
- [ ] The Day chart's colours have a line under the chart, which the cut-off picture did not show.
- [ ] Removing a shot has Undo in the bar but no confirmation; the tester expected one. (Undo is the house rule.)

## Still to test
A real phone: the step bar and Move map by touch, Back on every step, and naming a shot with a network.
