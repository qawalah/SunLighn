# Sweep of 3.24.2 (2026-11-23, computer only)

A second bug hunt, on the newest release, 3.24.2 (versionCode 63, `main` at `7fe38ea`). It ran on a computer with
Robolectric, with no phone. No app code was changed. Two kinds of test were added:

- **`MonkeyTest`**: a seeded monkey. Each step is one random action: a tap on anything that can be pressed (now and then a
  double tap), the step's main key, Back, a tap or a press and hold on the map, a sheet pulled, a slider moved, a dialog
  or menu answered, a wait with the clock moved on, a screen restart, or process death. It fails on an exception, an
  entry in the app's crash log, or a Back that never reaches Home. A watchdog stops the run and writes the stuck stack to
  `app/build/monkey-hang.txt` if the main thread stalls for a minute. There is one test per seed, and a failure replays
  exactly from its seed.
- **`Sweep324Test`** (18 tests): what 3.24 added (Find the spot, Now, the far end of a street, the guides said once,
  one tool leading to another) through Back, restarts, process death, double taps, odd places and the ends of every
  slider.

**How to run**

The suite (the monkey runs 6 seeds × 300 steps in it, about 70 s):

```bash
./gradlew testDebugUnitTest
```

More monkey:

```bash
MONKEY_SEEDS=$(seq -s, 1 30) MONKEY_STEPS=400 ./gradlew testDebugUnitTest --tests '*MonkeyTest*'
```

The test that shows C1 (skipped unless asked for):

```bash
SWEEP_KNOWN_BUGS=1 ./gradlew testDebugUnitTest --tests '*Sweep324Test*'
```

## Suite result

- **3.24.2 as released:** 219 tests, **0 failed**, 2 skipped (AliDriver and BuildingsLiveTest, which need settings).
- **This branch:** 243 tests, **0 failed**, 3 skipped (the 2 above plus C1's test).
- **Monkey:** 60 seeds × 400 steps (24,000 random steps), plus the 6 × 300 default: no crash, no failure the app caught,
  no hang, no dead end. The last 30 seeds spent their steps across every screen. Viewfinder 1,892 steps, Home 1,683,
  Your subject 1,532, Build the shot 1,265, the guides 1,088, search 874, Now 694, Settings 690, The far end 514, Where
  to stand 502, Where you stand 369, How you want it 312, Saved 292, Your shot 279, When 252.
- **The first sweep's bugs (B1 to B10)** stay fixed: `SweepTest`'s tests for them pass in the suite.

## Bugs

### C1 (low). The crash handler is installed again at every screen restart

- **Steps:** open the app and let the screen restart a few times (dark mode switching, or a Theme, Accent or Design change,
  each of which calls `recreate()`). Then the app crashes.
- **What happens:** `CrashLog.install` (`CrashLog.java:31-37`) runs from every `MainActivity.onCreate`
  (`MainActivity.java:148`) and wraps whatever handler is already there, so each restart adds a layer. After 6 screens a
  crash goes through 6 nested handlers and writes `last-crash.txt` 6 times (measured: 6 layers after 6 screens, 21 after
  21). Nothing breaks until there are very many restarts, but it is wasted work at the worst moment, and the chain only
  grows while the process lives.
- **What should happen:** installed once per process (a static flag, or install it from an `Application` subclass).
- **Test:** `Sweep324Test.crashHandlerInstalledOnce` (`SWEEP_KNOWN_BUGS=1`).

No other bug was found. The 3.24 additions held up in every case tried (below).

## Noted, not counted as bugs

- **Memory in long test runs.** One monkey test of 12,000 steps ran out of memory, and across one-test-per-seed runs the
  heap after GC grew by about 12 MB a seed. I traced it: old screens are kept alive by **delayed** messages on the main
  thread (a ScrollView's scrollbar fade, the bar's 4–15 s timers, a background job's result). Robolectric's clock does
  not move unless a test moves it, so those messages never run. With the clock moved on 60 s, 19 of 20 old screens were
  freed; the last one was waiting on a background job's result. A plain `Activity` as a control kept none. On a phone,
  time passes and they run: **not an app leak**. The monkey now moves the clock on during its waits.
- **Find the spot, a run already past.** Static reading only, not reproduced. `computeSpots` keys its cache on the day
  (`Fmt.midnight`), not on the time. If Where to stand stays open from morning to evening without a restart, a run
  holding only that morning's moment still shows as "Soonest · Today …". `nextDay` then falls back to `r.last()`, a
  moment already gone, and Stand here opens Your shot in the past. That needs the same screen open for hours; a restart
  or a new day works it out again.
- **Times in the phone's zone.** With no network (the zone service unreachable), McMurdo's and Taveuni's times show in the
  phone's time zone (here UTC), as designed until the place's zone is known.
- **Test isolation.** Buildings read by one test stayed in `Buildings`' static caches and changed another test's answer
  (`SweepTest.noNetworkThroughBothTools`, run after the street test). Both sweep classes now clear those caches before
  each test.

## What the targeted tests cover and found sound

- **Find the spot:**
  - Back, a restart and process death at each of its four steps. Double taps on Next, Change and Stand here (one step
    each; Stand here opens Your shot only, not also the viewfinder).
  - Polar night (Tromsø), the midnight sun (McMurdo), the North Pole and across the date line, for sun and moon, each
    with an answer or "Not in the coming year" plus the key that works ("Put the sun on the horizon").
  - At sea on foot: "This spot is on water", Stand here dimmed, and a tap says why and opens nothing.
  - Every Where it sits × Light, the height slider at 0 and at its top, Fixed distance at both ends, Behind you, for
    sun and moon. The answer's height matches the one asked (6.0°), with the distance Auto works out.
- **Now:**
  - With no location yet (it says how to choose one), then a tap; Look through the camera (a double tap opens one
    viewfinder, one Back closes it); restart and process death.
  - The North Pole, McMurdo and the date line, for sun and moon, with the day keys.
  - It never touches a tool: Continue afterwards opens the tool as it was.
- **Line up a street, the far end:** a 120 m block 60 m ahead hides every time. The card offers Move along the street,
  which moves You along the street's own line, and Undo puts You back exactly (also after a double tap).
- **Guides (3.24.2):** from a first start, never two guides at once; Back closes each; each mode's guide comes once; a
  restart or process death with a guide open leaves at most one.
- **One tool to another:** Find the spot → Your shot → Build the shot (the subject kept), and Line up a street's Your
  shot offering Build the shot. Saved reopens each tool's shot, and Back returns to Saved.
