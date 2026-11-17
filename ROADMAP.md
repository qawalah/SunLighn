# SunLighn roadmap

What is planned or proposed, newest thinking first. Done work moves to the decisions in `CLAUDE.md`.

Done in 3.6.0 (see its decisions): two tools instead of three (Where to stand folded into Build the shot); When on
one line; the trip lists a line once; a sun behind the subject's ground is left out; the Terrain map at night.

## Waiting for the owner (3.7)

- Pick a design (Pocket map or Readout, or a mix) after trying both on the phone; then carry it into Build it, When,
  Saved, the viewfinder and the tutorial, and drop the others (or keep Classic as a setting).

## Proposed (2026-11-17, owner; to discuss, nothing built)

- **Offline maps with 3D data.** Let the owner download an area (map tiles in the chosen look, plus OpenFreeMap's
  building tiles and the ground heights) to use with no network. To discuss: how the area is picked (a box on the map,
  round a saved shot, along a trip); size and storage limits; the tile servers' terms for bulk download (OSM's tile
  servers forbid it, so a keyless source that allows it is needed); what the sums need offline (heights, buildings,
  time zone, search); updating and deleting areas; whether MapLibre's offline region API fits our raster looks.
- **Users, to share shots.** Accounts so a shot (line, moment, name, framing) can be sent to or shared with other
  people. To discuss: what a "user" is (sign-in, or just a name and a link); the app has no server and no account
  today, so who hosts it and what it costs; privacy of places and saved shots; sharing as a link or a file instead
  (no accounts); who can see, edit or only open a shot; reminders for shared shots; the DevStore release path (Obtainium)
  for people who do not have the app yet.
- **Add a missing 3D building.** Let the owner add a building the map lacks (OpenStreetMap is thin in places: in Al
  Khobar none has a height), so the viewfinder and the sums count it. To discuss: how it is drawn (tap a spot, then
  footprint size, height and turn; or trace the outline on the map); kept on the phone only, or shared (ties to the
  users item); also correcting a wrong height or hiding a mapped building that is gone; whether it shows in 3D map
  and the viewfinder's silhouettes; feeding it into `Buildings.horizon` so a sunset time changes; sending the fix to
  OpenStreetMap; how it sits with the offline download item.
- **Sun height as a range (When, Line up a street).** The owner's sketch (2026-11-17) marks the Sun height slider with
  two ends, labelled "Range": instead of one angle (3.13's single thumb, times within 0.75° of it), pick a low and a
  high angle and list every time the body lines up between them. To discuss: a two-thumb slider as in the old lens
  range (`RangeBar`, gone in 3.6); whether Horizon / Middle / On top set the range or stay as they are; what Any means
  (the full range); the range's ends against what hides low times (hills, buildings: shade the part the land hides);
  how the card's Soonest, All times and the words under the slider ("From here the subject's top is...") follow it;
  whether Build the shot's Where the sun sits wants the same.
- **Swap subject and you.** The owner's sketch (2026-11-17, on When) shows an arrow between the subject's pin and
  You, marked "Swap": one press puts You where the subject is and the subject where You were. To discuss: where the
  key lives (on the map between the two, in the three dots, on When and Your shot); the sun's badge already swaps the
  body's end of the line (Behind subject / Behind you), so how the two differ and whether one word covers both; what
  is recomputed (the times, the ground heights, the subject's kind and height, which stay with the subject, not the
  spot); Undo in the bar; whether it works in Build the shot (there You is set aside) and on Your shot.
- **Shadows in the viewfinder.** Cast the subject's (and the buildings') shadow on the ground in the scene, from the
  shot's body. To discuss: the sun's shadow is a clear case (length from height, direction opposite the bearing),
  the moon's is faint and may be left out or drawn softly; what the scene can show from where You stand (a shadow
  falling towards or away from you, foreshortened, since the camera looks along the line); the pictogram subjects and
  the building silhouettes as casters; flat ground only (the ground is level in the scene); soft edges and fading near
  sunset; AR (no drawn land to fall on) and Build the shot's planning sky; both layouts (Answer band, Camera); and
  whether a "Shadows" setting or key is needed to keep the picture uncluttered.

## Open from earlier audits and reviews

- Arabic and right-to-left (in the design language, not done).
- Turning the map with two fingers; weather; units and reminder lead time in Settings.
- Compass and AR (the camera, the calibration) need testing on a real phone.
- The 3D map (3.15) has never run on a phone before this release: no emulator could run here. Look at: the map shows
  and moves, pins drag, taps place points, the colours (Soft, Paper, Grey, Dark, night) look as on the flat map, the 3D
  key, the buildings while tilted, the line and the sun's badge while tilted and turned. Off in Settings if it fails.
- The viewfinder's small maps (Move, Calibrate the compass) are still the flat osmdroid map; move them to MapLibre
  once the main map has proved itself, then osmdroid can go (the screen tests would need another way to run).
- Buildings (3.14.1) need a look on the phone: the guessed 9 m where no height is mapped (all of Al Khobar), and
  whether a lone tree or a subject without a building is ever hidden by its neighbours.
- The Terrain map at night (3.6 dims it less) needs a look on a real phone.
- A first-time-user run (Ali) over the two-tool Home and Build the shot's new keys.
- Not carried over from Where to stand, if missed: the lens as a range, every day's spot on the map at once.
