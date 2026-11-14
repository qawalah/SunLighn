# SunLighn

Pick your subject, and see where to stand and when the sun and the moon line up behind it (or behind you).
For photographers who want the answer, not a chart to interpret.

- **Search or tap** your subject; a sheet rises with the next good light and where to stand for it.
- **Frame**: a full-screen viewfinder of the shot. Pinch for the lens, slide through time, see the sun and moon on
  their paths and the subject to scale. **Live** follows the phone's compass on location.
- **Times and Day**: the next and the low moments for sun and moon, with golden and blue hour, from any date.
- **Saved**: keep lines, set your trip days, and see every moment on them. Tap the bell for a reminder 30 minutes
  before; share a moment as text or to your calendar.

Android app, released through [DevStore](https://github.com/qawalah/devstore).
Every push to `main` builds it; bumping `versionCode` in `app/build.gradle`
publishes a signed APK as a GitHub Release, installed on the phone via Obtainium
(add `qawalah/SunLighn`).

Map: OpenStreetMap through osmdroid (no account, no key). Sun and moon maths is built in and works offline;
the map tiles, place search (OpenStreetMap Nominatim) and the place's time zone (Open-Meteo) need the internet.
