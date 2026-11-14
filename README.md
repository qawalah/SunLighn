# SunLighn

Tap where you stand, tap your subject, and see when the sun and the moon line up behind it (or behind you).
For photographers who want the answer, not a chart to interpret.

- **Where to stand**: keep your subject, and SunLighn moves you so the evening or morning low sun sits behind it.
- **Trip**: pick the days you are away and the lines you want to shoot, and see every sun and moon moment on them.
- **Reminders**: tap the bell on any moment for a notification 30 minutes before.

Android app, released through [DevStore](https://github.com/qawalah/devstore).
Every push to `main` builds it; bumping `versionCode` in `app/build.gradle`
publishes a signed APK as a GitHub Release, installed on the phone via Obtainium
(add `qawalah/SunLighn`).

Map: OpenStreetMap through osmdroid (no account, no key). Sun and moon maths is built in and works offline;
only the map tiles need the internet.
