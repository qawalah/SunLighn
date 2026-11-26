# Photos and Share card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A shot can carry up to 3 result photos on the phone, and any shot or trip can be sent to X, Instagram, WhatsApp and the like as a drawn card (picture + words + link), with no account or server.

**Architecture:** `Photos` keeps cleaned JPEGs in `files/photos/<shot id>-N.jpg`. `ShareCard` draws a 1200x675 (or 1080x1920 story) PNG with Canvas in the app's Pocket-map look. `MainActivity` adds the Photo row to Your shot, a third Share choice, and a Trip-tab key. Release 3.35.0.

**Tech Stack:** Android framework only (Canvas, StaticLayout, `android.media.ExifInterface`, the system photo picker via `ACTION_OPEN_DOCUMENT`), Robolectric native graphics for tests. No new dependency.

**Spec:** `docs/superpowers/specs/2026-11-22-social-guides-design.md` (sections "Result photos", "Share out").

## Global Constraints

- Never change `applicationId` (`dev.qawalah.sunlighn`); bump `versionCode` 77 -> 78 and `versionName` "3.34.0" -> "3.35.0".
- Photos: at most 3 per shot, long side 1600 px, JPEG quality 85, **all EXIF removed** (re-encode from a Bitmap).
- Share card: 1200 x 675; story 1080 x 1920; app name "SunWhere" only, never a maker's name.
- Private shots show the city, never coordinates, on a card; no "You" point on the card (only the line's direction).
- Pocket map look: paper #ECEBE7, orange #EE5A1E quarter disc, IBM Plex Sans Arabic via `Ui.font(c, weight)`.
- English words; one word per idea (CLAUDE.md "Words"): Shot, Moment, Saved, Trip.
- No pills; Back closes a dialog; every new key says what it does (CLAUDE.md three questions).

## Review Focus

- A photo whose EXIF says "rotated 90" must come out upright with no EXIF left (`PhotosTest.rotatedPhotoComesOutUpright`).
- A huge image (12000 x 9000) must not run out of memory: decode with `inSampleSize` (`PhotosTest.hugeImageIsScaledNotLoadedWhole`).
- A file that is not an image (a PDF picked by mistake) must be refused, not crash (`PhotosTest.notAnImageIsRefused`).
- A very long Arabic or English title must shrink to fit the card, never run off it (`ShareCardTest.longTitleFits`).
- A trip of 1 shot and of 5 shots must both draw (collage with 1 and with 4 tiles) (`ShareCardTest.tripOfOneAndFive`).
- Removing a saved shot deletes its photos (`PhotosTest.removingTheShotRemovesItsPhotos`).

---

### Task 1: Photos (storage and cleaning)

**Files:**
- Create: `app/src/main/java/dev/qawalah/sunlighn/Photos.java`
- Modify: `app/src/main/java/dev/qawalah/sunlighn/Trip.java` (`removeShot`: call `Photos.removeAll`)
- Test: `app/src/test/java/dev/qawalah/sunlighn/PhotosTest.java`

**Interfaces:**
- Produces:
  - `static final int MAX = 3, SIDE = 1600`
  - `static byte[] clean(java.io.InputStream in)` -> JPEG bytes (no EXIF, upright, long side <= 1600) or `null` when not an image
  - `static java.io.File add(Context c, String shotId, android.net.Uri src)` -> the new file, or `null` when full or unreadable
  - `static java.util.List<java.io.File> list(Context c, String shotId)` (sorted by N)
  - `static void remove(java.io.File f)`; `static void removeAll(Context c, String shotId)`
  - `static android.graphics.Bitmap decode(java.io.File f, int maxSide)`

- [ ] **Step 1: Write the failing tests** `PhotosTest` (Robolectric, native graphics): `rotatedPhotoComesOutUpright` (a 400x200 JPEG with EXIF orientation 6 written with `ExifInterface` -> `clean` gives a bitmap 200x400, and `new ExifInterface(new ByteArrayInputStream(out)).getAttribute(TAG_ORIENTATION)` is null or "1"), `hugeImageIsScaledNotLoadedWhole` (a 6000x4000 JPEG -> longest side 1600), `notAnImageIsRefused` (`clean` of "%PDF-1.4" bytes returns null), `fourthPhotoIsRefused` (add x4 -> 4th returns null, `list` size 3), `removingTheShotRemovesItsPhotos` (`Trip.addShot` then `Trip.removeShot` -> `list` empty).
- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests '*PhotosTest*'`; expect compile failure (`Photos` undefined).
- [ ] **Step 3: Implement `Photos`**: read the stream to bytes (refuse over 40 MB), `BitmapFactory.Options.inJustDecodeBounds` then `inSampleSize` powers of two until the long side < 2x1600, decode, read orientation from `android.media.ExifInterface(new ByteArrayInputStream(bytes))`, rotate with a `Matrix`, scale to long side 1600, `compress(JPEG, 85)`. Files named `<shotId>-<n>.jpg` in `getFilesDir()/photos`; `add` picks the lowest free n (1..3).
- [ ] **Step 4: Run the tests**; expect PASS.
- [ ] **Step 5: Commit** `feat: result photos kept on the phone, cleaned of EXIF`.

### Task 2: ShareCard (drawing and sending)

**Files:**
- Create: `app/src/main/java/dev/qawalah/sunlighn/ShareCard.java`
- Modify: `app/src/main/java/dev/qawalah/sunlighn/ShotFileProvider.java` (`getType`: `image/png` for `.png`)
- Test: `app/src/test/java/dev/qawalah/sunlighn/ShareCardTest.java`

**Interfaces:**
- Consumes: `Ui.font(Context, int)`, `ShotFileProvider.uriFor(Context, String)`, `ShotFileProvider.DIR`.
- Produces:
  - `static final int W = 1200, H = 675, SW = 1080, SH = 1920`
  - `static final class Data { String title, city, moment, detail; boolean moon; android.graphics.Bitmap photo; double youLat, youLon, subLat, subLon; boolean hasLine; }`
  - `static Bitmap shot(Context c, Data d, boolean story)`
  - `static Bitmap trip(Context c, String city, String dates, java.util.List<Data> shots, boolean story)` (1 to 4 tiles shown; more shots say "+N more")
  - `static boolean send(android.app.Activity a, Bitmap card, String words)` (writes `card.png` to the shared cache dir, `ACTION_SEND` `image/png` with `EXTRA_STREAM` and `EXTRA_TEXT`, read grant)

- [ ] **Step 1: Write the failing tests** `ShareCardTest` (native graphics): `shotCardHasTheRightSize` (1200x675, story 1080x1920), `withAndWithoutPhoto` (both draw; the photo one's right half is not the paper colour at its centre), `longTitleFits` (a 160-character mixed Arabic/English title draws and the pixel column x = W-1 stays paper/photo, no exception), `tripOfOneAndFive` (1 and 5 `Data` both draw; sizes right). Each test saves its PNG to `app/build/screens/card-*.png` for a person to look at.
- [ ] **Step 2: Run** `--tests '*ShareCardTest*'`; expect compile failure.
- [ ] **Step 3: Implement `ShareCard`** in the Pocket-map look: paper background, orange quarter disc top-right (as `Covers.draw`), left column: city (muted, 30 px), title (bold, `StaticLayout`, start 76 px shrinking in 4 px steps until it fits 3 lines in 520 px), moment and detail lines, a small sketch box (subject dot, direction line toward the sun disc; `hasLine` only), "SunWhere" at the foot; right: the photo centre-cropped in a 24 px rounded rect, else a larger sketch. Story size stacks photo (square) above the text. Trip: 2x2 tiles of photos or sketches under the city and dates.
- [ ] **Step 4: Run the tests**; expect PASS; open the PNGs and check them.
- [ ] **Step 5: Commit** `feat: share card drawn on the phone`.

### Task 3: In the app (Photo row, Share choice, Trip key) and release

**Files:**
- Modify: `app/src/main/java/dev/qawalah/sunlighn/MainActivity.java` (Your shot sheet near `addOtherTool(under)` calls; `shareShot`; `onActivityResult`; `Trip.Host` implementation), `Trip.java` (`Host.shareTrip(List<Line>)`, a key under the Trip tab's days), `app/build.gradle`, `CLAUDE.md`
- Test: `ScreensTest.photoRowAndShareCard`

**Interfaces:**
- Consumes: `Photos.add/list/remove`, `ShareCard.shot/trip/send` (Tasks 1, 2), `savedShot()`, `saveShot(boolean)`.
- Produces: `Trip.Host.shareTrip(List<Line> shots)`; request code `PICK_PHOTO = 42`.

- [ ] **Step 1: Write the failing test** `ScreensTest.photoRowAndShareCard`: on Your shot of a saved shot there is a key described "Add a result photo"; after `Photos.add` of a generated JPEG the row shows its thumbnail (described "Result photo 1") and the key still reads "Add a result photo"; pressing Share offers an item "Share as a picture" (dialog items checked via `ShadowAlertDialog`).
- [ ] **Step 2: Run**; expect FAIL (no such key).
- [ ] **Step 3: Implement**: a Photo row under the Save / Remind / Directions row (a small key "Photo" opening `ACTION_OPEN_DOCUMENT` `image/*`; thumbnails 56 dp, tap = dialog Remove / Cancel; saving the shot first when it is not saved); `shareShot` gets a third item "Share as a picture (X, Instagram, ...)" building a `ShareCard.Data` (title: the shot's name or subject; city: the saved shot's `city`; moment from `Fmt.when`; photo: its first photo) and calling `ShareCard.send` with the words and the link; Trip tab: a key "Share as a picture" calling `host.shareTrip`; `Trip.removeShot` already clears photos (Task 1).
- [ ] **Step 4: Run** the new test, `*ScreensTest*`, `*CityGroupTest*`, `*CoversTest*`, and `assembleRelease`; expect all PASS and the APK built.
- [ ] **Step 5: Bump the version, add the 3.35.0 decisions and the Saved/Your shot table rows to `CLAUDE.md`, commit, push to the branch and `main`.**
