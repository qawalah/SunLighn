# SunLighn roadmap

What is planned or proposed, newest thinking first. Done work moves to the decisions in `CLAUDE.md`.

Done in 3.6.0 (see its decisions): two tools instead of three (Where to stand folded into Build the shot); When on
one line; the trip lists a line once; a sun behind the subject's ground is left out; the Terrain map at night.

## Waiting for the owner (3.7)

- Pick a design (Pocket map or Readout, or a mix) after trying both on the phone; then carry it into Build it, When,
  Saved, the viewfinder and the tutorial, and drop the others (or keep Classic as a setting).

Done in 3.17.0: shadows in the viewfinder and on the 3D map (see its decisions); the AR and flat-map cases are left.

Done in 3.18.0 (see its decisions): share a shot by a link, sun height as a range, swap You and the subject, a missing 3D
building (added by hand, on this phone), and a building that hides the subject (said, not yet left out of the times).

## Kept for later (owner, 2026-11-18)

- **Offline maps with 3D data.** Let the owner download an area (map tiles in the chosen look, plus OpenFreeMap's
  building tiles and the ground heights) to use with no network. To discuss: how the area is picked (a box on the map,
  round a saved shot, along a trip); size and storage limits; the tile servers' terms for bulk download (OSM's tile
  servers forbid it, so a keyless source that allows it is needed); what the sums need offline (heights, buildings,
  time zone, search); updating and deleting areas; whether MapLibre's offline region API fits our raster looks.
- **Users (accounts).** Not needed for sharing by a link (3.18). Only if shots must be edited together, or kept on a server.
- **Building follow-ups.** Stand the owner's buildings up on the 3D map; correct a mapped building's height or hide one that
  is gone; share the added buildings (with a shot's link); the hidden-subject check in Build the shot and in the times.
- **Share links need GitHub Pages on** for `docs/` (Settings, Pages), or the https link opens nothing; `sunwhere://` works at
  once.

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
