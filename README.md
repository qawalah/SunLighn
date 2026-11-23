# SunLighn

Pick your subject, and see where to stand and when the sun and the moon line up behind it (or behind you).
For photographers who want the answer, not a chart to interpret.

- **Line up a street**: tap a street's far end and where you stand; the app says when the sun or moon sits exactly
  down it, at the height you pick, and where along the street it clears the buildings.
- **Find the spot**: your subject and where the sun should sit on it; every spot to stand and every day, on the map.
- **Build the shot**: drag the sun or moon to where you want it in the picture, size the subject, pick the lens;
  the app says where to stand and on which days.
- **Now**: the sun and moon where you are, through the day, with the buildings' shadows and the camera.
- **Viewfinder**: the shot full screen. Pinch for the lens, slide through time, see the sun and moon on their
  paths and the subject to scale. **AR** follows the phone on location, with the camera behind; its compass is set from the sun, the moon or a landmark.
- **Times and Day**: the soonest and the low moments for sun and moon, with golden and blue hour, from any date.
  A line that can never line up says so and offers the closest.
- **Saved**: your planned **Shoots** (press the bell on any moment; you are reminded 30 minutes before), your
  **Lines**, and your **Trip** days with every moment on them. Share a moment as text or to your calendar.

Android app, released through [DevStore](https://github.com/qawalah/devstore).
Every push to `main` builds it; bumping `versionCode` in `app/build.gradle`
publishes a signed APK as a GitHub Release, installed on the phone via Obtainium
(add `qawalah/SunLighn`).

Map: OpenStreetMap through osmdroid (no account, no key). Sun and moon maths is built in and works offline;
the map tiles, place search (OpenStreetMap Nominatim) and the place's time zone (Open-Meteo) need the internet.
