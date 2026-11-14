# Lessons learned in this app

What this app taught us, so it is not repeated here or in the next app. The shared list for every app is in
DevStore: https://raw.githubusercontent.com/qawalah/devstore/main/docs/lessons/LESSONS.md

Add a lesson in the same change that fixes the mistake: the rule first, then what went wrong, with the date.
Mark a lesson that would help other apps with `→ DevStore`; once it has been added to DevStore's list, change the
mark to `→ DevStore (sent <date>)`. See DevStore `docs/lessons/README.md`.

<!-- Example:
- **Back never throws work away without asking.** Back during a long reading cancelled it silently. (2026-11-14) → DevStore
-->

- **A cloud Claude session has no Android SDK and no way to build until you give it one.** Fetch
  `https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip`, put it in
  `cmdline-tools/latest`, accept licences, install `platforms;android-35` and `build-tools;35.0.0`, set
  `sdk.dir` in `local.properties` (git-ignored). Then run `./gradlew testDebugUnitTest assembleRelease`.
  Gradle downloads through the proxy fail at random when run in parallel: use `--max-workers=1` and just run it
  again. (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **A push to a session branch is not built by CI.** The release workflow runs for `main` and pull requests only,
  so a branch push proves nothing. Build and run the tests locally before the push that releases.
  (SunLighn, 2026-11-14) → DevStore (sent 2026-11-14)
- **Save the user's work as you go, so Back never needs a question.** SunLighn stores the line and map position on
  every change; Back just leaves, and Clear has an Undo instead of a confirmation. (SunLighn, 2026-11-14)
- **When Gradle says "Could not find x.jar" for a file that downloads fine with curl, it has cached the miss.**
  Stop the daemon (`./gradlew --stop`), delete `~/.gradle/caches/modules-2/metadata-*`, and run again. Do not loop
  this: every wipe makes Gradle ask Maven Central for everything again, and after a few rounds it answers 429 Too
  Many Requests; wait a few minutes then. (SunLighn, 2026-11-14) → DevStore
- **No emulator in a cloud session (no KVM): check screens with Robolectric.** A test that launches the real
  activity, presses its keys by text or description, presses Back and saves PNGs (`@GraphicsMode(NATIVE)`) caught
  layout and Back mistakes without a phone. It needs `android.useAndroidX=true` and
  `unitTests.includeAndroidResources = true`. (SunLighn, 2026-11-14) → DevStore
