# Escape HTML/JS in marker detail popups

## Context

BlueMap's marker API treats a `POIMarker`'s `detail` field as raw HTML — unlike `label`, which
`de.bluecolored.bluemap.api.markers.Marker.setLabel()` auto-escapes (`&`, `<`, `>`) before storing, `detail` is
stored verbatim (see `DetailMarker`/`ObjectMarker` in `bluemap-api`). The BlueMap API javadoc explicitly warns
implementers to escape any user-supplied text placed into `detail` to avoid XSS.

This mod currently violates that: sign text is directly player-controlled, and it flows unescaped from
`SignEntryHelper.getDetail()` all the way into `POIMarker`'s `detail` via `BlueMapAPIConnector.addMarker()` /
`updateMarker()`. Any player who can place a sign matching a configured marker-group prefix (e.g. `[poi]`) can
currently inject arbitrary HTML/JS into the popup shown to every visitor of the server's BlueMap web page. This is
a real, live XSS vector, not just a theoretical API footnote.

A secondary, related rendering bug: `detail` for multi-line signs is built by joining lines with `\n`
(`ParsingContext.appendDetail`), but since `detail` is raw HTML, plain `\n` characters don't produce visible line
breaks in the browser popup — multi-line sign text currently renders as one run-on line.

This was raised and intentionally deferred while this session's work stayed scoped to adding unit test support.
It should be its own focused change.

## Goal

Escape untrusted, player-supplied text before it crosses into BlueMap's HTML-rendering `detail` field, and restore
correct multi-line rendering — without touching sign persistence, parsing, or anything already covered by BlueMap's
own `label` escaping.

## Changes

1. **New utility: `src/main/java/com/tpwalke2/bluemapsignmarkers/common/HtmlUtils.java`**
   - `escape(String text)` — escape HTML metacharacters: `&` → `&amp;`, `<` → `&lt;`, `>` → `&gt;`, `"` → `&quot;`,
     `'` → `&#39;`. (Same characters BlueMap's own `Marker.setLabel()` escapes, plus quotes for defense-in-depth
     since this mod controls the exact rendering context, not just label text.)
   - `toHtmlDetail(String text)` — calls `escape(...)`, then replaces `\n` with `<br>` so multi-line sign detail
     renders as separate lines in the popup. Escaping must happen *before* the `<br>` substitution, or the inserted
     tags would themselves get escaped.
   - Follows the same plain-Java, no-Minecraft-types style as `SignLinesParser`, so it's trivially unit-testable
     (see `FileUtils` in the same `common` package for the existing style/location convention).

2. **`src/main/java/com/tpwalke2/bluemapsignmarkers/core/bluemap/BlueMapAPIConnector.java`** — apply escaping at
   the boundary where text is handed to the BlueMap API (the actual rendering sink), not earlier in the pipeline:
   - In `addMarker(...)`: wrap `addAction.getDetail()` with `HtmlUtils.toHtmlDetail(...)` before
     `.detail(...)` on the `POIMarker.builder()`.
   - In `updateMarker(...)`: wrap `updateAction.getNewDetails()` with `HtmlUtils.toHtmlDetail(...)` before
     `poiMarker.setDetail(...)`.
   - No change needed for `label` — `Marker.setLabel()` in the BlueMap API already escapes `&`/`<`/`>` on every
     path this mod uses (`POIMarker.builder().label(...)` and the direct `marker.get().setLabel(...)` update path),
     so labels are already safe from markup injection.
   - No change needed for `icon` — icon paths/URLs come from server-configured `MarkerGroup`s (trusted config),
     never from sign text.

3. **Why escape at this boundary and not in `SignEntryHelper`/`SignManager`:** `SignEntry` (and the persisted
   `signs.json`) should keep storing the raw, unescaped sign text — that's the source of truth for what's actually
   written on the sign. Escaping only at the BlueMap-API call site keeps that fidelity intact and follows
   "encode at the point of use" — if another consumer of label/detail is ever added that isn't rendering HTML,
   it won't have to un-escape anything.

4. **New test file: `src/test/java/com/tpwalke2/bluemapsignmarkers/common/HtmlUtilsTest.java`**
   Following the `SignLinesParserTest` pattern (plain JUnit 5, no mocking needed). Planned cases:
   - `escape` neutralizes `&`, `<`, `>`, `"`, `'` individually and in combination (e.g. a full `<script>` payload).
   - `escape` leaves ordinary text untouched (no spurious changes to plain sign text).
   - `escape` is applied before tag substitution — verify `toHtmlDetail` on input containing literal `<br>` text
     renders the user's angle brackets as escaped entities, not as a live tag (i.e. no injection via a sign that
     itself contains the string `<br>`).
   - `toHtmlDetail` converts `\n` to `<br>`, including multiple consecutive newlines and text with no newlines at
     all (no-op on the line-break front).

5. **`gradle.properties`** — bump `mod_version` patch segment (`26.2-0.16.0` → `26.2-0.16.1`): a user-facing bug
   fix, not a new feature or breaking change. No sign-persistence version bump is needed (raw text on disk is
   unaffected), and no changes to `SignLinesParser`, `SignEntryHelper`, `SignManager`, or the mixins — this is
   purely a rendering/output-safety fix at the BlueMap API boundary.

## Verification

- New unit tests: `./gradlew test` covers `HtmlUtilsTest` alongside the existing `SignLinesParserTest`.
- Manual/in-game check via `./gradlew runServer`: place a sign with a matching prefix and a line containing
  `<script>alert(1)</script>` (or similar), confirm the BlueMap web popup displays the literal text rather than
  executing it, and that a multi-line sign now renders with visible line breaks in the popup.
