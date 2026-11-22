# Product

<!-- impeccable:product-schema 1 -->

Written 2026-11-16 from `CLAUDE.md` (the product's own record) and the owner's answers that day. Facts not
confirmed by the owner are marked "inferred".

## Platform

android

## Users
Photographers, the owner first, who want the sun or the moon in an exact place in a photo: sitting on a
building's top, at the end of a street, on the horizon behind a subject. They plan at home or on the way, then
use the app on location, often at golden hour or at night, holding the phone in one hand.

## Product Purpose
SunLighn gives the answer, not a sun path to read: pick the subject, and it says where to stand and when the sun
or moon sits exactly on that line, and shows the shot as the camera will see it. Success is a photographer
standing on the right spot at the right minute without having interpreted a chart.

## Positioning
PhotoPills and similar tools draw sun paths for the user to read. SunLighn works backwards from the picture
(Build the shot: place the body, get the spot and the time), from a subject and a height (Find the spot: every spot
and day on the map) and from a line (Line up a street). Now, the one sun path to read, is a side door on Home (3.24).

## Operating Context
Home → one of three tools (Line up a street, Find the spot, Build the shot) or Now → a few steps on a map with a sheet that rises from
the bottom → Your shot (the moment, the viewfinder, Save, Remind me, Directions). The viewfinder is full screen,
with an AR mode over the camera. Saved holds shots and a trip. Released through DevStore to the owner's phone.

## Capabilities and Constraints
- Custom Android Views in Java (no Compose, no Material components); osmdroid with OpenStreetMap / OpenTopoMap.
- Light and dark themes, both kept; dark follows the phone by default.
- The flow, Back behaviour and the vocabulary in `CLAUDE.md` (Words) stay as they are (owner, 2026-11-16).
- The accent follows the body: amber for the sun, violet for the moon (owner, 2026-11-16).
- Font: IBM Plex Sans Arabic, bundled, so Arabic and right-to-left stay possible (owner, 2026-11-16).
- No pills; one shape per meaning (the Controls table in `CLAUDE.md`).

## Brand Commitments
- Name: SunLighn. Store icon: three points on one line (you, subject, sun).
- Redesign 2026-11-16 takes inspiration from Braun (owner's reference images: a Braun-style app mock and Braun
  navigation concepts: folded city maps, map booklets, handheld navigators, a restrained palette of orange, blue,
  yellow, red and grey). The owner does not want a copy of their mock.

## Evidence on Hand
No real phone screenshots in the repo (`store/screenshots/` is empty). Times and places shown in mock-ups are
illustrative.

## Product Principles
1. The answer first: the time and where to stand are never pushed below chrome.
2. Every step answers: what can I do next, what can I change, will I see the change.
3. One shape, one meaning; one word per idea.
4. Colour carries meaning (the body, you, the line, can't), never decoration.

## Accessibility & Inclusion
Used outdoors in low sun and at night: contrast and large targets matter (inferred).
