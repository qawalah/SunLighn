# SunLighn design (short)

**Why.** Photographers plan shots where the sun or moon sits behind a landmark. PhotoPills shows sun and moon
paths and leaves the reading to you. SunLighn answers directly.

**What.** Tap the map for where you stand ("You"), tap again for the "Subject". The line's compass bearing is
the direction to match. The card shows, for the Sun and for the Moon, the next time its azimuth equals that
bearing while above the horizon, and the next time it does so low (up to 8 degrees; moon also 60% or more lit).
A switch says whether the body is behind the subject or behind you.

**Goals.** Two taps to the answer. Works offline apart from map tiles. Looks like the other DevStore apps.
**Non-goals.** Sun path drawing, augmented reality, weather, golden-hour tables, accounts.
**Anti-goal.** A screen the user has to interpret.

**Approaches considered.**
1. Native Android, plain Java, osmdroid for the map, own sun/moon maths. *Chosen:* matches the existing DevStore
   skeleton, no API key, tiny APK, no new toolchain.
2. Google Maps SDK: needs an API key and billing. Rejected.
3. WebView with Leaflet and a JS astronomy library: more moving parts and slower start. Rejected.

**How.** Sun: low-precision series (about 0.01 degrees). Moon: main perturbation terms plus parallax (about
0.1 degrees); both checked against PyEphem. The search steps three minutes at a time over 400 days, finds sign
changes of (azimuth - bearing), refines by bisection, keeps those with altitude of at least -1 degree.
It runs on a background thread; the sun shows first.

**Left to the builder / later.** Arabic and right-to-left. Real phone screenshots in `store/screenshots/`.
Time zone of the place (today: the phone's). Dragging the markers.
