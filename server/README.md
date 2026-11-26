# SunWhere server

Guides shared from the app (a shot or a trip), their result photos and card, ratings and reports, and the web pages
that open them (city and month pages, link previews for X and the rest). One process, one SQLite file, one folder of
media: one Docker image that runs on Fly.io or on any machine.

An independent Gradle build (not part of the app's `settings.gradle`), so the app's release is never touched by it.

## Run it here

```
../gradlew -p . test            # 41 tests against the real server on a random port
ADMIN_TOKEN=changeme PUBLIC_URL=http://localhost:8080 ../gradlew -p . run
```

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | 8080 | where it listens |
| `DATA_DIR` | `data` (`/data` in the image) | the database, `media/`, `backup/` |
| `PUBLIC_URL` | `http://localhost:8080` | the address people reach it by (links, og:image, sitemap) |
| `APP_LINK` | the existing share page | the page that opens a shot in the app |
| `ADMIN_TOKEN` | empty (admin off) | header `X-Admin-Token` for hide / unhide |
| `TRUST_PROXY` | false | true behind Fly or any proxy: the client's address comes from `Fly-Client-IP` / `X-Forwarded-For` |
| `HOLD_SECONDS` | 600 | a new account's listed guides show in city pages after this long |

## Any machine

```
docker build -t sunwhere-server server/
docker run -d --restart unless-stopped -p 8080:8080 -v sunwhere-data:/data \
  -e PUBLIC_URL=https://guides.example.com -e ADMIN_TOKEN=... -e TRUST_PROXY=true sunwhere-server
```
Put any TLS proxy in front (Caddy, nginx, Traefik). Back up the volume: the server also writes a copy of the database
to `/data/backup/` every day and keeps seven.

## Fly.io

```
cd server
fly launch --copy-config --no-deploy      # keep fly.toml; pick a free app name and edit PUBLIC_URL to match
fly volumes create sunwhere_data --region fra --size 1
fly secrets set ADMIN_TOKEN=$(openssl rand -hex 24)
fly deploy
fly certs add guides.example.com          # when you have a domain; then set PUBLIC_URL and deploy again
```
Keep exactly one machine (`fly scale count 1`): SQLite is one file on one volume.
Moving later is: `fly ssh sftp` (or `fly volumes snapshots`) the `/data` folder to the new machine and start the image.

## The API (first slice)

Accounts have no email: `POST /api/accounts {name}` gives a **token** (kept on the phone) and a **recovery code**
(shown once). `POST /api/accounts/recover {name, recovery}` gives a new token. `Authorization: Bearer <token>`.

| | |
|---|---|
| `PUT /api/guides/{id}` | publish or update: `{title, city, country?, month, year, summary?, listed, shots:[{name, code}]}` (`code` = the app's shot link code); `listed:false` is link-only |
| `PUT /api/guides/{id}/card` | the share card PNG (the preview picture), at most 1.5 MB |
| `PUT` / `DELETE /api/guides/{id}/photos/{1-3}` | result photos, JPEG, at most 3 MB |
| `DELETE /api/guides/{id}` | take it down |
| `POST /api/guides/{id}/rating {stars}` | 1 to 5; your own guide cannot be rated |
| `POST /api/guides/{id}/report {reason}` | three accounts' reports hide it |
| `GET /api/guides/{id}`, `/api/cities`, `/api/cities/{slug}/guides` | what the app reads |
| `DELETE /api/me` | the account, its guides and media, gone |
| `GET /`, `/c/{city}`, `/g/{id}`, `/g/{id}/card.png`, `/sitemap.xml` | the web pages |
| `POST /api/admin/guides/{id}/hide` and `/unhide` | with `X-Admin-Token` |

## People (server 0.2)

Accounts have a unique **handle** (`@handle`, 3 to 20 lowercase letters, digits or underscores; changed once in 30 days) beside
the display name, a bio (160), and public stats. Guides have a **level**: `link`, `public` (city pages, followers' feeds) or
`friends` (friends only; 404 on the web). The old `listed` flag still works (true is public).

| | |
|---|---|
| `PUT /api/me/profile {handle?, name?, bio?}`, `GET /api/me` | my profile |
| `GET /api/people?q=` | search by handle prefix (20) |
| `GET /api/people/{handle}` | profile, stats and my relation (404 when blocked either way) |
| `POST` / `DELETE /api/people/{handle}/follow` | follow (instant; public guides) |
| `POST` / `DELETE /api/people/{handle}/friend` | ask or accept; cancel, decline or unfriend |
| `POST` / `DELETE /api/people/{handle}/block` | block cuts follow and friendship both ways |
| `GET /api/me/network` | followers, following, friends, requests in and out, blocked |
| `POST` / `DELETE /api/guides/{id}/like`, `.../photos/{n}/like` | like (not your own) |
| `GET /api/feed` | newest 30: public guides of people I follow, public and friends-level of friends |
| `GET /u/{handle}` | the profile page |
| `GET /admin` | the owner's page (asks for `ADMIN_TOKEN`); `GET /api/admin/stats`, `POST /api/admin/accounts/{handle}/ban` and `/unban` |

A 0.1 database is migrated when 0.2 starts (handles are made from the names; `listed` becomes `level`).
