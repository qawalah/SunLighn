# SunLighn

Tap where you stand, tap your subject, and see when the sun and the moon line up behind it (or behind you).
For photographers who want the answer, not a chart to interpret.

Android app, released through [DevStore](https://github.com/qawalah/devstore).
Every push to `main` builds it; bumping `versionCode` in `app/build.gradle`
publishes a signed APK as a GitHub Release, installed on the phone via Obtainium
(add `qawalah/SunLighn`).

Map: OpenStreetMap through osmdroid (no account, no key). Sun and moon maths is built in and works offline;
only the map tiles need the internet.
