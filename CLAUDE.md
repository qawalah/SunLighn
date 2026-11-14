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
Photography helper. Tap where you stand, tap your subject; the app says when the sun and the moon will sit exactly
on that line (next time, and next time low in the sky). It gives the answer, not a sun path to read, which is the
point: PhotoPills does the latter.

- `Astro.java` sun and moon positions plus the search. Plain Java, tested in `app/src/test` against PyEphem values.
- `MainActivity.java` map (osmdroid, OpenStreetMap, no key), the line, the answer card, saving as you go.
- `Tutorial.java` first-start tutorial (three cards, Skip always there, replay with the ? button).
- `Ui.java` colours and shapes from the DevStore design language (`docs/design/DESIGN.md` in DevStore): navy night
  and pale day themes, one teal accent, flat bordered surfaces, drawn line icons, route green and blue for the line.
- Map looks: `Map` (OpenStreetMap, tinted navy at night) and `Terrain` (OpenTopoMap), cycled with the layers button and
  remembered. Both need no key. The credit text on the map is required by their licences: keep it, update it per style.
- `store/icon.png` the DevStore icon. Put real phone screenshots in `store/screenshots/` (1.png, 2.png ...).

## Back (test this on every screen after any change)
| Where | Back does |
|---|---|
| Tutorial open | Closes it (same as Skip) |
| Map, with or without a line | Leaves the app. The line and map position are saved as you go, so nothing is lost |

The phone's permission dialogs and the keyboard-free map have no other layers. If a screen is added, add its row.

## Words (one word per idea)
**You** = where the photographer stands. **Subject** = what the sun or moon lines up with. **Line** = the two
together. **Behind subject / Behind you** = which end of the line the sun or moon stands at. **Next** and **Low**
are the two answers per body. Do not introduce other names for these.

## Decisions (Claude's calls on 2026-11-14, not yet confirmed by the owner)
- Answer first, no sun-path drawing. Two taps to the answer, last line remembered.
- Times show in the phone's time zone and the zone name is printed under the answer.
- English only for now (Arabic and right-to-left are in the design language; not done yet).
- "Low" means up to 8 degrees above the horizon; for the moon it also needs 60% or more lit.
- Free tile servers that need no key are few. Do not add CARTO (needs a key since 2026) or Esri imagery (terms unclear).
