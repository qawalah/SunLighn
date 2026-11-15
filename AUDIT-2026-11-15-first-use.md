# First-use test, 2026-11-15 (version 2.0.0)

How it was done: a Haiku agent was given 16 pictures of the real screens (from `ScreensTest`) and one neutral line
on how each was reached. It was told nothing about the app and was not allowed to read code or docs. Round one: say
what each screen is for, what you would press, what confuses you. Round two: it was told what the app does and
asked whether each screen was now easy, what the app should have said itself, and how to redo the journey.
It only saw still pictures: nothing here is about touch, motion or speed.

Each finding: **[x] 2.1.0** fixed, **[ ]** open.

## Round one: knowing nothing

It worked out what the app does ("helps me plan a photo by showing where to stand, and at what time, so the sun or
the moon sits exactly behind a landmark I pick"), but only by screen 02, and said it **would have given up at the
viewfinder** (screen 04). Confusion, 0 obvious to 5 lost: start 3, where to stand 4, viewfinder 4, answers 3,
Day 3, search 2, Saved 3, Settings 1.

Worst first:
- [x] 2.1.0 "Subject first / Where I stand first" asked for a choice before either word meant anything.
- [x] 2.1.0 "On the line", "Sun on the line": which line? The idea was never introduced. Now "Lined up".
- [x] 2.1.0 Five unlabelled icons over the viewfinder. Now each has its name under it.
- [x] 2.1.0 "35 mm · 54° × 38°": camera numbers. Now "35 mm lens · normal".
- [x] 2.1.0 The Day chart: two curves and a marker, no legend. Now said under it.
- [x] 2.1.0 "Next · Low", "Golden hour", "Night": no word on what they are or why they matter. "Soonest" and
  "Low sun / Low moon" now, with one line under the answers.
- [x] 2.1.0 "Set the subject's height (the ruler key)": no key of that name. The key is now labelled Height.
- [x] 2.1.0 "Evening sun, Morning sun": now Sunset, Sunrise. The fourth chip was cut off ("Mo"): shorter names fit.
- [x] 2.1.0 Search: "Set as Subject / Set as You" and coordinates as the placeholder; "Nominatim".
- [ ] Saved: "Trip", "− 3 days +", "Moments". Each tab now has a line saying what it is; the stepper stays.
- [x] 2.1.0 The only explanation of the app was "How it works", last in Settings. Now first there, and the first
  sheet says what the app does.
- [x] 2.1.0 Every row of Where to stand said the same thing. Days that share a spot are one row with a date range.
- [ ] A low moon months away ("Feb 25, 2027") appears with no reason on the row itself (the footnote now says why).
- [ ] "Behind subject / Behind you" still has no picture of what changes.
- [ ] Accent colours have no names; "each place's own time" is still unclear wording.
- [ ] "Subject · 96 m" confused it: a height it had not typed (it was set for the test picture). A height taken
  from the subject's kind should say so on the label.

## Round two: told what the app does

Yes on 7 screens, "with effort" on 8, still no on 1 (the Day chart). What it asked the app to say itself is the
list above. Its journey: one opening sentence, pick the subject, a subject sheet that states the answer as a
sentence ("the sun sets behind Big Ben at 3:34 PM tomorrow"), where to stand, preview, save and remind.

Taken: the opening sentence, the renames, labelled keys, legends, help first.
Not taken, and why:
- Renaming Subject and You to "What I photograph" and "Where I stand" everywhere: too long for map labels and
  chips. The first screens now introduce the words instead.
- Removing the Day tab and coordinates in search: the owner asked for the Day view; coordinates are how
  photographers share spots. Both are now explained where they appear.
- Three tutorial cards cut to one: kept at three, rewritten to say what the app does on the first.

## Still to test
A second naive run on 2.1.0 (the new Plan a frame screen has not been seen by a first-time user), and a real phone.
