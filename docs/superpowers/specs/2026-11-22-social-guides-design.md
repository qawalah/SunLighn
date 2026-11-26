# SunWhere social: guides, friends, calendar feed, offline maps (design, 2026-11-22)

Status: **draft, waiting for the owner's approval.** Nothing here is built yet.

## Why

The owner wants strangers to find a city's shots: someone going to Lisbon in March opens SunWhere (or finds a web
page through a search engine) and sees what other photographers worked out there: where to stand, when, how it
looks. They rate it and save the good ones into their own Shots or Trip. Friends are a smaller circle inside that:
people you follow and share with privately. A trip's moments go into the planner the person already uses, through a
calendar feed. And because shots are taken where there is often no signal, a city's map can be downloaded first.

Success: a stranger lands on `…/lisbon/march`, sees three rated guides, taps Open in SunWhere, saves one, and at the
spot with no network the map, the moment and the viewfinder all work.

## Goals

1. **Accounts** with a public name, only for the social parts.
2. **Share** a shot or a trip at one of three levels: **private** (the phone only, as today), **friends**, **public**.
3. **Guides**: a public shot or trip, browsable **by city, then by month**, rated with stars, savable into Shots / Trip.
4. **Web pages** for each city, month and guide, readable without the app and indexable by search engines, with
   Open in SunWhere.
5. **Friends**: follow, friend (mutual follow), share to a friend directly.
6. **Calendar feed**: each trip gets a private subscription link (`.ics`) that any calendar or planner can subscribe
   to; it updates when the trip changes. Plus a calendar file saved on the phone for use offline.
7. **Offline maps**: download a city (or an area round a trip or a guide) with its map, buildings, ground heights,
   time zone and place names, so the app works fully with no network there.
8. **One Docker setup** the owner runs: `docker compose up`.

## Non-goals (this round)

- Video, galleries beyond three photos a shot, editing a photo in the app (crop only).
- Comments, chat, likes beyond the star rating, notifications of social activity beyond a friend request and a shot
  sent to you.
- Backing up all of a person's shots to the server. Only what is shared, published or given a feed goes there.
- Two-way planner sync, per-app planner integrations (Google Calendar API, Notion, TripIt).
- Arabic, right-to-left (still open from before).

## Anti-goals (a failure even if it works)

- **The app needs an account or a network for anything it does today.** Everything personal stays offline-first.
- **Something becomes public that the person did not make public.** The default level is private, always.
- **The phone, the web page and the calendar disagree about a moment.** One copy of the sums (see Shared core).
- **A public feed that fills with spam** with no way for the owner to take it down.
- **An exact home location leaks**: a public guide shows the spot to stand, which is the point, but never the
  owner's own location or a "Now" point; friends lists are not public.

## Constraints (from the project)

- `applicationId` stays `dev.qawalah.sunlighn`; every user-facing change bumps `versionCode`/`versionName`; no
  release `signingConfig`; CI (`qawalah/devstore` android-release workflow) builds `:app` on every push to `main`.
  Adding Gradle modules must not break that build: `:core` and `:server` must build with the same Gradle wrapper,
  and the server's build must not be needed for `assembleRelease`.
- No keyed or paid services (CLAUDE.md decisions): OpenStreetMap, Open-Meteo, OpenFreeMap, Nominatim. Nominatim's
  rules apply on the server too: at most one request a second, a real User-Agent, results cached.
- The words table in CLAUDE.md: new names below are added to it; no other names for these ideas.
- The three-questions table, the Back table and the Controls table get rows for every new screen.
- Today's storage: everything in `SharedPreferences("sunlighn")` as JSON (`trip_lines`, …); a shot is
  `Trip.Line`; a link is `ShotLink` (deflated, URL-safe, `https://qawalah.github.io/SunLighn/s/#CODE` and
  `sunwhere://shot?d=CODE`). Old links must keep opening.

## Approaches considered

- **A. A Java server sharing the app's own code (chosen by the owner).** Javalin + PostgreSQL/PostGIS + Caddy in one
  compose file. The sums (`Astro`, `Spots`, `Street`, `FrameMath`) and the shot format move into a plain-Java module
  both the app and the server compile, so the calendar feed and the web pages give the phone's exact moments. Costs:
  sign-in, feeds and moderation are our own code.
- **B. A self-hosted backend (PocketBase or Supabase).** Sign-in and an admin panel free, but the feed and the pages
  need the sums anyway, so a second service in another language and a port of `Astro` that can drift. Supabase is
  about a dozen containers. Not chosen.
- A hosted service (Firebase and the like): left out, the owner wants a container they control.

## Architecture

```
phone (SunWhere)                    server (docker compose)
  :app  ── uses ──> :core  <── uses ── :server (Javalin)
   │  offline-first, local JSON          │  REST /api/v1, web pages, /cal/*.ics, /packs/*
   └── HTTPS (when online) ──> caddy ──> server ──> postgres + postgis
                                           └──> volume: packs/, dem/, cache/
```

### Repository layout (Claude's call)

The server lives in this repository, so `:core` is shared without publishing a library.

```
settings.gradle        include ":app", ":core", ":server"
core/                  java-library, no Android: Astro, Spots, Street, FrameMath, Light (sums only),
                       ShotCodec (the pure half of ShotLink), model (Shot, Trip, Guide), Ics (calendar text)
server/                Javalin app, Flyway migrations, Pebble templates for web pages, Dockerfile
deploy/                docker-compose.yml, Caddyfile, .env.example, README (how to run, back up, update)
```

- The `:core` move is mechanical: those classes have no Android imports today (checked: `Astro`, `Spots`, `Street`,
  `FrameMath`). `ShotLink` uses `android.util.Base64` and `android.net.Uri`: split into `core/ShotCodec`
  (`java.util.Base64`, URL-safe, the same bytes) and a thin Android wrapper. Their tests move with them and keep
  passing against the PyEphem values. `Fmt` stays in `:app` (it uses `Context`); the server gets its own small
  formatter for English times, days and distances, tested against `Fmt`'s outputs for the same inputs.
- `:core` targets Java 8 bytecode features the app's `minSdk 24` desugars; no `java.time` beyond what desugaring
  gives (or keep the `Calendar`/`TimeZone` code as it is).
- The server's Docker image is built from the repo root with a multi-stage `Dockerfile`
  (`./gradlew :server:installDist`, then a JRE 21 image). CI for the app is unchanged; a separate workflow may build
  and push the server image to GHCR (`ghcr.io/qawalah/sunwhere-server`) on tags `server-v*`. It must not touch the
  DevStore release.

### Docker setup

`deploy/docker-compose.yml`, four services:

| Service | Image | Notes |
|---|---|---|
| `caddy` | `caddy:2` | 80/443, automatic HTTPS for `${DOMAIN}`, static `/packs/*` straight from the volume |
| `server` | built here | Javalin on 8080, reads `.env`; runs Flyway migrations at start |
| `db` | `postgis/postgis:16-3.4` | named volume `pgdata`; nightly `pg_dump` to `backups/` by a small cron sidecar or the server |
| `packs` (job) | same image, `--packs` | builds offline packs on demand from a queue table; same volume |

`.env`: `DOMAIN`, `SMTP_HOST/PORT/USER/PASS/FROM`, `ADMIN_EMAILS`, `NOMINATIM_UA`, `PROTOMAPS_BUILD`, `DATA_DIR`.
No secret is committed; `.env.example` lists them.

The owner needs: a machine with Docker, a domain name pointing at it, and an outgoing mail account (SMTP) for the
sign-in codes. The share-link page moves from GitHub Pages to `https://${DOMAIN}/s/#CODE`; the app accepts both
hosts forever.

## Accounts and sign-in

- **Sign-in by email code** (Claude's call): enter an email, get a 6-digit code (10 minutes, 5 tries), done. No
  password, no Google or Apple developer setup. The server keeps the email for sign-in only; it is never shown.
- After the first sign-in, pick a **public name** (unique, 3–30 characters, changeable once a month) shown on guides.
- The app gets a long-lived **session token** (random 256-bit, hashed in the database), stored in the app's private
  prefs; sign out deletes it on both ends. Sessions listed and revocable in Account.
- **Delete account**: removes the person, their guides, ratings, follows, feeds; their saved copies on other phones
  stay (they are copies).
- Where in the app: Settings gets an **Account** row (Sign in / your name). The first time someone presses Publish,
  Share to friends, Rate, or Calendar feed while signed out, the sign-in sheet opens with a line saying why; nothing
  else asks.

## Sharing levels

Every shot and trip on the phone has a level, **Private** by default (a dropdown, per the Controls table):

| Level | Who sees it | Where it lives |
|---|---|---|
| Private | only this phone | the phone (as today) |
| Friends | your friends, in their Friends tab and by a direct send | phone + server |
| Public | anyone: Guides in the app, the web pages | phone + server |

Changing the level is the only thing that uploads. Changing back to Private deletes it from the server (copies
already saved by others stay theirs). A shot's link (`ShotLink`) keeps working at any level: a link is not a level.

What is uploaded is the shot's own data (both points, mode, moment, body, name, subject name/kind/height, framing,
the lens) and for a trip its days and its shots. Never: Now's point, Continue, reminders, the location history.

## Guides

A **guide** is a public shot or trip, with a title (defaults to the shot's name) and up to 500 characters of notes
(the person's own tips: "stand on the low wall", "crowded at weekends").

### City and month

- **City**: the server reverse-geocodes the subject point (or a trip's first shot) through Nominatim, zoom 10, once
  per guide, cached by ~1 km cell; it stores `city` (city, town or village, else county), `country_code`, and a
  `city_slug` (`lisbon-pt`). The person can pick a different nearby name from the same reverse result if it is wrong.
- **Month**: the months the moment happens. A shot with a moment counts for that month. A shot with "any time" (no
  moment) counts for every month in which it lines up at least once, worked out by `:core`'s `Astro.search` for the
  next year on publish (and once a year after). A trip counts for the months of its days.
- **Grouping**: Guides → a list of cities (near you first when location is allowed, else most guides) → a city →
  months (12 headings, the current one first, empty months greyed) → guides, sun and moon as separate groups (never
  mixed, as the rest of the app), sorted by rating then newest.

### Rating

- 1 to 5 stars, one per person per guide, changeable; not on your own guides.
- Shown as the mean with the count ("4.6 · 23"). Sorting uses a Bayesian mean (prior of 3.5 with a weight of 5) so
  one 5-star guide does not top a city.
- **"I shot it"** (Claude's call): a toggle beside the stars meaning the rater went there; shown as a count. It is
  the only signal beyond stars.

### Saving a guide

Save copies the guide into Shots (or a trip into Trip) as a normal private shot with `source_guide_id`; it then
works offline like any shot. The guide's page shows "Saved by 140".

### Moderation (anti-spam)

- **Report** on every guide and every public name: a reason (spam, wrong place, offensive, other).
- Three reports from different accounts hide a guide until the owner looks; the owner (any `ADMIN_EMAILS` account)
  sees a **Reports** list on a web admin page (`/admin`, signed in) with Hide, Restore, Ban name.
- Limits: 20 publishes a day per account, 5 sign-in codes an hour per email and per IP, 60 API calls a minute per
  token. New accounts' first 3 guides wait 1 hour before showing (a simple spam brake).

## Friends

- **Follow** anyone by their public name (from a guide or by search). **Friends** = both follow each other.
- Friends-level shots and trips show to friends in a **Friends** tab in Saved (Shots, Trip, **Friends**).
- **Send to a friend**: on Your shot and Trip, a direct send lands in the friend's Friends tab as "Sent by Sara", and
  as a notification if they allow it (checked on app start and every few hours when online; no push service:
  Claude's call, no Google account needed).
- Follower lists are private to the person; only counts are public.

## Web pages

Server-rendered HTML (Pebble templates), plain and read-only, the DevStore design language's colours and IBM Plex
Sans Arabic, light and dark by the visitor's setting, phone-width first.

| URL | Shows |
|---|---|
| `/` | what SunWhere is, the cities with most guides, Get the app (the Obtainium / DevStore link) |
| `/c/{city_slug}` | the city: months with counts, top guides of the season |
| `/c/{city_slug}/{month}` (`/c/lisbon-pt/march`) | that month's guides, sun then moon |
| `/g/{id}-{slug}` | one guide: the title, the notes, a small map (OSM tiles, Leaflet, both points and the line), the next moments (from `:core`), where to stand, the stars, **Open in SunWhere** (`sunwhere://guide?id=…`, falling back to the share page) |
| `/u/{name}` | a public name's guides |
| `/s/#CODE` | the share-link page (moved from GitHub Pages; same behaviour) |
| `/sitemap.xml`, `robots.txt` | every public city, month and guide |

Each page has a title and description naming the place and the month ("Sunset lined up with the Ponte 25 de Abril,
Lisbon, in March"), Open Graph tags, and a still picture rendered on the server for link previews (Claude's call:
a simple SVG-to-PNG of the map line and the sun's direction; not the viewfinder).

Rating, reporting and saving happen in the app; the pages say "Open in SunWhere to rate or save".

## Calendar feed

- A trip (any level, even Private) can be given a **Calendar feed**: the trip is uploaded as private (visible to
  nobody), and the server returns `https://${DOMAIN}/cal/{token}.ics` (token random 160-bit). The app offers Copy
  link, Open in calendar (`webcal://`), and Stop feed (deletes the token and the uploaded copy).
- The feed lists every moment of the trip's lines in its days (the same list as the Trip tab, worked out by
  `:core`), each a 15-minute event at the moment, titled "Sunset lined up · Ponte 25 de Abril", with the location
  (where to stand, as `GEO` and text), the description (the time, the light, the height and bearing, distance from
  the subject, Directions link, `sunwhere://` link back) and a `VALARM` 30 minutes before (as the app's reminder).
  `UID` is stable per moment so calendars update rather than duplicate. Times in UTC with the place's zone named.
- Edits on the phone re-upload the trip when online; the feed is regenerated on request (cached 15 minutes).
  Calendars poll on their own schedule (Google every few hours): the Trip tab says so in a note.
- **Offline**: Trip also offers **Save calendar file**, an `.ics` built on the phone by the same `:core` `Ics` class,
  shared through the system share sheet. No account needed.

## Offline maps

### What a pack holds

A **pack** is one area (a city, or a box round a trip or a guide, up to 30 × 30 km), one file per part, kept in the
app's files folder:

| Part | Source | Used for |
|---|---|---|
| map | vector tiles z0–15 cut from the Protomaps daily planet build (`pmtiles extract … --bbox`, range requests, no full planet download; ODbL) | the map when offline |
| buildings | the same tiles' building layer | `Buildings` (heights, the 3D map, shadows) |
| ground | a height grid at 90 m from Copernicus GLO-90 (the model Open-Meteo uses; free, on AWS open data) | `Heights`, `FrameMath.landHorizon` (the grid extends 25 km past the area for the horizon) |
| time zone | the zone at the area's centre and any others inside (from `timezone-boundary-builder` data on the server) | `Zones` |
| places | the tiles' place and POI names with points, as a small index | `Places` search offline |

A city pack is typically 20–80 MB. The server builds packs on demand (queue, one at a time) and keeps built ones a
month; the most requested cities are rebuilt monthly.

### In the app

- Settings, **Offline maps**: the packs on the phone (name, size, date), Delete, Update. Download from: the city of
  a guide (a key on the guide), round a saved shot or a trip (a key in Trip), or **Pick an area** (a box on the map).
  Downloading needs a network and no account (Claude's call).
- In a pack's area, every module reads the pack first when it has no network: `Heights`, `Buildings`, `Zones` and
  `Places` gain a pack lookup before their network request. Online they behave as today (pack data may be older).
- **The map offline**: the app's looks today are raster tiles from OSM / OpenTopoMap / HOT, which may not be bulk
  downloaded. Offline, the map shows the pack's vector tiles in an **Offline** look (a MapLibre style drawn to match
  Pocket map's paper and greys, the same colour matrices where they apply). The bar says "Offline map" once. Online,
  the chosen look returns. Only the 3D map (MapLibre) can show packs; on the flat map (Settings, 3D map: Off, or no
  OpenGL ES 3) a pack still serves heights, buildings, zone and search, and the map shows only cached tiles, and
  Settings says so.
- The pack's buildings use Protomaps' schema (`height`, `min_height` in the `buildings` layer), not OpenFreeMap's:
  `Buildings` gains a second reader for it, tested on a fixed tile.

## Server data model (PostgreSQL + PostGIS)

```
account(id, email_hash, email_enc, name, created, banned, is_admin)
session(id, account_id, token_hash, created, last_seen, device)
login_code(email_hash, code_hash, expires, tries)
item(id, account_id, kind {shot,trip}, level {friends,public,feed}, payload jsonb,   -- the core model as JSON
     title, notes, subject geography(Point), body {sun,moon}, created, updated, hidden)
photo(item_id, n 1..3, bytes, width, height, sha256, created, hidden)        -- files under data/photos/
guide_place(item_id, city, city_slug, country_code)          -- public items only
guide_month(item_id, month 1..12)
rating(item_id, account_id, stars 1..5, shot_it bool, updated)
save_count(item_id, n)
follow(follower_id, followee_id, created)
sent(id, from_id, to_id, item_id, created, seen)
feed(token_hash, item_id, created)
report(id, item_id or account_id, reporter_id, reason, created, resolved)
pack(id, bbox geography(Polygon), name, status, path, size, built, requested)
geocode_cache(cell, result jsonb, fetched)
```

The item payload is the `:core` model serialised by one codec shared with the app (the same JSON the phone keeps),
versioned (`"v": 1`).

## API (`/api/v1`, JSON, token in `Authorization: Bearer`)

```
POST /auth/code {email}                 POST /auth/verify {email, code} -> {token, account}
GET/PATCH/DELETE /me                    GET/DELETE /me/sessions/{id}
PUT /items/{client_id} {kind, level, payload, title, notes}   -- idempotent upsert by the phone's own id
DELETE /items/{client_id}
GET /cities?near=lat,lon | ?q=          GET /cities/{slug}/months
GET /guides?city=&month=&body=&cursor=  GET /guides/{id}
PUT /guides/{id}/rating {stars, shot_it}       POST /guides/{id}/save
POST /reports {item_id | account, reason}
GET /people?q=                          PUT/DELETE /follow/{name}
GET /friends/items?since=               POST /send {to, item_id}     GET /inbox?since=
PUT /items/{client_id}/photos/{n} (multipart JPEG)   DELETE /items/{client_id}/photos/{n}
GET /g/{id}/card.png (the 1200x675 link-preview card)
POST /feeds {item_id} -> {url}          DELETE /feeds/{item_id}
POST /packs {bbox | city_slug} -> {id, status}   GET /packs/{id}  (then the files under /packs/{id}/)
```

Errors are `{error, message}` with words the app can show as they are.

## The app side

- **Sync queue**: a small outbox (in prefs, JSON) of uploads, deletes, ratings, follows, sends; flushed when online
  (on start, on network return, after each change), retried with backoff; each entry idempotent. The app never waits
  on it: changes show at once.
- **New places in the UI** (each gets its rows in CLAUDE.md's tables):
  - Home: a **Guides** key beside Now (a single row, like Now: icon, name, words, chevron).
  - **Guides** screen: cities → months → guides; a guide opens Your shot (read-only until saved) with the stars,
    I shot it, Save, Report, the author's name (Follow), Download the city map.
  - Your shot and Trip: the **level** dropdown (Private / Friends / Public), Send to a friend, Calendar feed / Save
    calendar file.
  - Saved: a **Friends** tab.
  - Settings: **Account**, **Offline maps**.
- **Back**: Guides → Home; a city → Guides; a month list → the city; a guide → where it was opened; the sign-in sheet
  closes with nothing changed.
- **Words** (to add): **Guide** = a public shot or trip; **Private / Friends / Public** = the levels; **Rate**,
  **I shot it**; **Friends** (the tab and the circle); **Send to a friend**; **Calendar feed**; **Calendar file**;
  **Offline maps**, **Pack** (code only; the screen says "Lisbon map, 48 MB"); **Account**, **Public name**.

## Result photos (added 2026-11-26, owner: "possibility to add result picture")

A shot can carry **result photos**: the picture you actually took at the moment the app gave you. This is what makes
a guide worth following, and what a share card shows.

- **Phone first, no server.** Photo is a key on Your shot and on a saved shot: pick from the gallery (the system
  photo picker, so no storage permission) or take one with the camera. Up to **3 per shot**, kept in the app's files
  (`files/photos/<shot id>-N.jpg`), shown on the shot's row in Saved and on Your shot. A private shot's photos never
  leave the phone.
- **Made safe on the phone before anything is shared**: scaled to 1600 px on the long side, JPEG ~85%, **all EXIF
  removed** (location, time, camera serial), because a public photo must never give away where a person lives or was.
- **Upload only at Friends / Public**, with the shot (`PUT /items/{id}/photos/{n}`, multipart, 10 MB limit, JPEG
  magic-number checked server-side, re-encoded on the server too). Stored on the server volume `data/photos/`, served by
  the web server with long cache headers and a resized thumbnail (`.../{n}-640.jpg`). Changing the shot back to
  Private deletes them from the server.
- **Moderation**: the same Report as guides (a photo can be reported alone); a hidden guide hides its photos;
  per-account upload limit (30 a day); optional later: an automatic nudity/violence check on upload.
- **Saved copies**: Saving a stranger's guide copies its text only; its photos are shown on the guide's page and in the
  app while online, never copied into the saver's shots (they stay the author's).
- A photo **belongs to its author**: Publish asks "You confirm you took this photo and may share it"; the guide page
  credits the public name.

## Share out (added 2026-11-26, owner: "share as a whole to social app like X")

Any shot, guide or trip can be sent to **X, Instagram, WhatsApp, Telegram, Messages** and any other app on the phone
through the system share sheet, as a picture plus words plus a link. Nothing here needs an account.

- **The share card** is an image the phone draws itself (`ShareCard.java`, tested): 1200 x 675 (the shape X shows
  whole in its feed), in the app's own design: the result photo as the large part when there is one (else the shot's
  still picture from the viewfinder), the city name and the title, **the moment** ("Sun behind subject · Sat 28 Nov ·
  17:12 · 3° up"), the two points as a small line sketch with the sun, the star rating once a guide has one, and
  "SunWhere" with the link's short address. A trip card is a 2 x 2 collage of its shots under the city and the dates.
  A **story size** (1080 x 1920) is an option for Instagram and X's stories.
- **The words** that go with it: a line in the shot's own voice ("Golden hour behind Kingdom Centre, Sat 28 Nov, 17:12.
  Where to stand and when, in SunWhere:") plus the link, ready to edit in the app that opens. Hashtags are left to the
  person.
- **The link**:
  - *With the server*: `https://${DOMAIN}/g/{id}-{slug}` (a guide) or `/t/{id}` (a trip). The page carries Open Graph
    and **Twitter Card** tags (`summary_large_image`, the 1200 x 675 card rendered server-side from the same
    description, or the author's photo), so X, WhatsApp and Telegram unfold it into a large picture with the title.
    Tapping it opens the page, which offers Open in SunWhere (`sunwhere://guide?id=`).
  - *Without the server* (not set up yet, or the shot is private): the link is the 3.18 share link (`ShotLink`), which
    opens in SunWhere for people who have it. Private shots are shared as image + link, which discloses the place only
    to those who get the link: the card shows the city, not the coordinates.
- **Privacy on the card**: no home address, no exact "You" point on a public card (the standing spot is the point of a
  guide; a personal "You" on a private share is shown only as the line's direction), no account email, no EXIF.
- X's own limits: the app hands X an image and text through the system share (no X sign-in, no X API key, no cost);
  X decides how the text is cut. Posting to X from the server (automatic, "post my guides") is **not** built.

## Build order

Each phase is releasable on its own and leaves the app working offline as before.

0. **Photos and Share card (no server, can ship first)**: Photo key (up to 3, EXIF stripped, on the phone), Share card
   (shot and trip, 16:9 and story), the system share sheet with image + words + the existing link. Works for everyone
   today and is the part people will actually post; the later phases turn its link into a page X can unfold.
1. **Shared core**: `:core` module, the moves and the `ShotCodec` split, tests green; app unchanged to the user
   (release with a version bump only if something visible changes).
2. **Server foundation**: `:server`, compose, Caddy, migrations, sign-in by email code, `/me`, items upsert, photo
   upload and thumbnails, the share page moved to the domain with the Twitter Card tags. App: Account in Settings, the
   sync queue, the level dropdown (Private / Public only at first), photos uploaded with a shared shot.
3. **Guides and web pages**: city and month, the Guides screen, rating, I shot it, save, report, admin page, the
   web pages and sitemap.
4. **Calendar**: the feed and the calendar file (the file can ship in phase 1 since it needs no server).
5. **Friends**: follow, the Friends level and tab, send to a friend.
6. **Offline maps**: the pack builder, the Offline look, the pack readers in Heights, Buildings, Zones, Places.

Tests: `:core` unit tests (moved and new: `Ics`, month membership); server tests with Testcontainers PostGIS (API,
permissions: a private or friends item never in a public list, rate limits, feed contents equal to `:core`'s
moments); `ScreensTest` extended for every new screen; Ali run after phases 3 and 6.

## Left to the builder

Library versions; template and CSS detail within the design language; the exact rate-limit numbers' tuning; the
pack builder's tooling (the `pmtiles` CLI in the image, GDAL for the DEM cut); the server-side still picture's look;
pagination sizes; the admin page's layout.

## Open for the owner

1. ~~Photos with guides?~~ **Yes** (owner, 2026-11-26), above.
2. The domain name and the machine to run it on, and an SMTP account: needed by phase 2, not for the design.
3. Whether the Share card and Photo (phase 0) ship **before** the server (recommended: it needs nothing from the
   server and gives the owner something to post today), or wait for the server.
