# The 3D map (3.15.0, 2026-11-17)

## Why
Owner: "Can I tilt to see the map in 3D? As an option." Offered three ways (a 3D view beside the flat map; the whole
map moved to MapLibre; a drawn bird's-eye view without a map). Owner: "I think 2 is eventual, so do it."

## Goals
- The main map tilts (two fingers, or the 3D key) and turns; tilted, buildings stand up at the heights the app's sums
  use (OpenFreeMap tiles; not mapped = 9 m).
- Everything on the map works as before: taps, holds, dragging pins, the line, the sun's badge, the Day chart's
  paths, fitting the line, the map looks and colours, the credit.
- Optional: Settings, 3D map, On / Off (Off = the flat map of before).

## Non-goals
- A new map style (vector map look); the viewfinder's small maps (still osmdroid); labels in 3D.

## Approaches
1. A separate 3D view beside the flat map (smaller change; two maps to keep).
2. **The whole main map on MapLibre (picked by the owner)**, with osmdroid kept as the twin the tests run and the Off
   setting.
3. A drawn bird's-eye view, no map underneath.

## Design
- `GeoMap` (abstract): view, projection both ways, centre/zoom (osmdroid's zoom), fit, look, redraw, scale offset,
  bearing, tilt, lifecycle. `OsmMap` wraps the old code; `LibreMap` holds a MapLibre MapView in texture mode with a view
  above it for the app's layer; a frame takes a touch that lands on a pin.
- Colours: the same ColorMatrix filters through `TextureView.setLayerPaint`.
- Style JSON built in code: background, raster (the look's tiles), fill-extrusion from OpenFreeMap's `building` layer,
  visible while tilted more than 8°.
- Crash guard at start (`map3d_starting`); Arm-only native libraries, compressed.

## Verified here
All screen tests on the flat map; the style by MapLibre's validator; the style rendered in Chromium (MapLibre GL JS)
over Al Khobar, tilted. Not verified: MapLibre Native on a phone (no emulator in the build container).
