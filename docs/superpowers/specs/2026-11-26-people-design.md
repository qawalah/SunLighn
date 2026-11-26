# People: profiles, handles, follow and friends, likes, admin stats (design)

Approved by the owner 2026-11-26 ("Both" for follow and friends, then "Proceed"). Builds on
`2026-11-22-social-guides-design.md` (server 0.1, app 3.36). Server 0.2 first, then the app 3.37.

## Intent
Make SunWhere a small social app: people with a handle and a profile, who can follow and befriend each other, share
guides at three levels, like guides and their photos, and an owner who can see how the server is doing.

## Goals
- A unique `@handle` per account, apart from the display name (which may be Arabic).
- Profiles with public stats, in the app and on the web (`/u/{handle}`).
- Follow (one way, public guides) and friends (request and accept, friends-only guides), blocking.
- Likes on guides and on a guide's photos.
- One place in the app (People on Home) for me, friends, finding people and guides.
- An admin page with the server's numbers and two actions (hide a guide, ban an account).

## Non-goals / anti-goals
No comments, chat, push notifications, feed algorithm, email. Nothing that makes the app need the server: every
People feature says "needs a network" and the rest of the app is unchanged. Private shots are never sent.

## Approaches
(a) Extend the current Javalin + SQLite server in place with new tables; (b) split into services; (c) a hosted backend
(Firebase etc.). Chosen: (a), because it is one machine and a few thousand users, the image and Fly setup already
exist, and (b)/(c) add cost and a second place for the data for no gain now.

## Design
**Accounts.** `account` gains `handle` (unique, lowercase `[a-z0-9_]{3,20}`, reserved words refused), `bio` (160),
`handle_changed` (time), `banned`. `name` becomes the display name. Migration: handle = slug of the name (Latin only,
else `user`) + a number until free. A handle can change once in 30 days.
**Follow / friends / block.** `follow(a, b)`; `friend(a, b, state)` with `pending` (from a to b) and `accepted`;
`block(a, b)`. Blocking deletes follows and friendship both ways and hides each from the other (profiles 404, no
likes, no follow, no request). You cannot follow or befriend yourself.
**Guide levels.** `guide.listed` becomes `level`: `link` (by link only), `public` (city pages, sitemap, followers),
`friends` (friends only; a page for anyone else is 404, no preview). Existing: listed to public, else link. The app's
Publish adds "Friends".
**Likes.** `like(account, guide, photo)` where photo is 0 for the guide or 1..3 for its photos; one per account and
target; not your own; counts on the guide JSON and page; liking needs a signed-in account and visibility of the guide.
**Profiles.** Stats computed: guides (public), cities, likes received (guides and photos), followers, following,
friends. A profile shows public guides; a friend also sees friends-level ones. Private details never leave.
**Web.** `/u/{handle}` profile page; guide pages show author handle (linked) and likes; like needs the app (the page
says so): web sign-in is out of scope.
**Admin.** `GET /api/admin/stats` (X-Admin-Token): accounts total and new in 7 days, guides by level, photos, likes,
follows, friendships, pending requests, open reports, hidden guides, banned accounts, storage bytes, db bytes, top
ten cities, requests today. `/admin` is a small page that asks for the token, keeps it in the tab, and shows this.
`POST /api/admin/accounts/{id}/ban` and `unban`: a banned account's token stops working and its guides are hidden.
**API (new).** `PUT /api/me/profile {handle?, name?, bio?}`; `GET /api/people/{handle}`; `GET /api/people?q=`
(handle prefix, 20); `POST/DELETE /api/people/{handle}/follow`; `POST /api/people/{handle}/friend` (request or accept),
`DELETE` (cancel, decline, unfriend); `POST/DELETE /api/people/{handle}/block`; `GET /api/me/network` (followers,
following, friends, requests in and out, blocked); `POST/DELETE /api/guides/{id}/like` and `/photos/{n}/like`;
`GET /api/feed` (public guides of people I follow and friends' guides, newest first, 30).
**App (3.37).** `Social` gets the calls above. **People** key on Home opens `PeopleScreen` with tabs Me (profile,
stats, edit handle, name and bio), Friends (requests with Accept and Decline, friends, following, followers) and Find
(search by handle; cities and their guides; like keys). Cached last answers show offline with "Not updated". Words:
Handle, Profile, Follow, Friends, Like, Block. Back closes People to Home.

## Constraints
Offline-first stays. Rate limits on follow/friend/like per account per day. Existing 3.36 phones keep working: the
`listed` flag is still accepted on `PUT /api/guides/{id}` and maps to the level.

## Left to the builder
Exact page styling, the limits' numbers, the app screen's layout (follows the Pocket-map look and the three questions
table), search ranking beyond prefix.
