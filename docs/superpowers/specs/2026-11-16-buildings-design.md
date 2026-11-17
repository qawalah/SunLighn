# Buildings hide the sun (3.14.0, 2026-11-16)

## Why
Owner: a sunset the app gave went behind a block of buildings (answer "1": the times must be right). The app only
knew the land (hills, `FrameMath.landHorizon`) and the subject's ground; buildings were left out on purpose in 3.12.1
because the terrain model's roofs are not ground.

## Goals
- Line up a street leaves out the lined-up times when the sun or moon is behind a building, like it does for hills,
  and says so ("A building 240 m this way hides the sun below 6.2°").
- Build the shot checks the spot it gives and warns when a building is in the way (it does not refuse the spot).
- The viewfinder (and Your shot's still picture) draws the buildings as dark shapes on the land.
- Optional: Settings, **Buildings**: Count them (default) / Leave out.

## Non-goals
- Google Maps or any keyed or paid source (owner asked; answer: key, billing, terms forbid using its data in our own
  sums; the app's rule is keyless sources).
- Trees, terrain under buildings (buildings stand on your ground), building parts, roofs' shapes.
- Checking every spot Build the shot tries (one request per spot is too slow).

## Approaches
1. **OpenStreetMap buildings through Overpass (picked).** Free, no key, the same data as the map. Heights from
   `height`, else `building:levels` × 3 m, else a guess by kind (about 3 floors).
2. Google Maps / Photorealistic 3D Tiles: needs a key and billing; terms forbid extracting heights. Rejected.
3. The terrain model's surface heights (already fetched): 90 m cells, roofs blur into hills; 3.12.1 removed exactly
   this. Rejected.

## Design
- `Buildings.java`: one Overpass request per place and direction: every building within 300 m of you, every
  building in a corridor 80 m wide along the direction out to 1.5 km, and tall ones (40 m or 12 floors and more)
  out to 5 km. Kept in memory and in the cache folder; remembered per spot of ~11 m and whole degree.
  `horizon(...)`: the highest angle, from your eye, of a building's top that the ray along the direction crosses;
  skips the building you stand in and the subject's own (it contains the subject point or lies within 15 m of it).
  Plain Java maths, tested.
- MainActivity: `hiddenAll()` takes buildings as well; the times are found again when the buildings come.
- ComposeScreen: for the chosen spot and the body's direction at its first time; the warning goes in the red
  reason line; Stand here stays enabled (heights can be guessed).
- FrameView: buildings drawn as walls (one quad per edge), far ones before the subject, near ones after; not in AR
  and not in the planning sky.
- Without a network, or with the setting on Leave out, nothing changes.

## Left to the builder
Colours of the silhouettes, the guessed heights per kind, the wording.

## 3.14.1 (2026-11-17)
Overpass refused the phone in Al Khobar and nothing said so. The source is now OpenFreeMap's vector tiles (zoom 14,
read by hand), and When and the viewfinder say what the buildings are doing.
