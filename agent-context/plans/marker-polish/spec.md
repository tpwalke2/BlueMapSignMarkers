# Marker polish: sorting, toggleable, depth-test, style classes

Status: ready-for-agent

GH issue: 197

## Problem Statement

BlueMap's marker/marker-set API exposes several fields the mod never sets, so every group silently takes BlueMap's
default and admins have no config knob to change it: marker-set menu order (`sorting`), whether a marker-set can be
hidden by players at all (`toggleable`), whether a `LINE`/`SHAPE` marker renders through terrain (`depth-test`), and
custom CSS hooks on `POI` markers (`classes`). Verified against `bluemap-api-2.8.0`'s actual method signatures, not
just the wiki doc.

## Solution

Four independent `MarkerGroup` config fields, each a thin passthrough to an existing BlueMap builder call:

- `sorting` (int) — marker-*set* ordering in the map's layer menu. All group types.
- `toggleable` (boolean) — whether the marker-set can be hidden/shown by a player at all. All group types.
- `depthTest` (boolean) — whether terrain can occlude the marker. `LINE`/`SHAPE` only (`POI` has no such field in
  BlueMap's API — it's a billboard icon, always on top).
- `cssClasses` (list of strings) — CSS classes added to the marker element, for admins who style BlueMap's UI via
  its `custom.css`. `POI` only (`LineMarker`/`ShapeMarker` don't implement BlueMap's `ElementMarker` interface).

No new marker-group *type*, no persistence change, no sign-text change — every one of these is set once per group in
`BMSM-Core.json` and never touched by a player.

## User Stories

1. As a server admin, I want to set a `sorting` value on a marker group, so that I can control which group's layer
   appears first in BlueMap's menu instead of relying on whatever order groups happen to load in.
2. As a server admin, I want to set `toggleable: false` on a marker group, so that I can force a marker-set to
   always stay visible and prevent players from hiding it (e.g. safety-relevant markers).
3. As a server admin, I want `toggleable` to default to `true` (BlueMap's own default) when unset, so that existing
   configs behave exactly as they do today after upgrading.
4. As a server admin, I want to set `depthTest: false` on a `LINE`/`SHAPE` group, so that a marker representing an
   underground trail or mineshaft stays visible instead of being hidden under BlueMap's rendered terrain mesh.
5. As a server admin, I want `depthTest` to default to `true` when unset, matching BlueMap's own default and today's
   existing (unset) behavior.
6. As a server admin, I want to set `cssClasses` on a `POI` group, so that I can target that group's markers with
   custom rules in BlueMap's `custom.css` for icon-level styling BlueMap's own config doesn't expose.
7. As a server admin, I want a warning (not a crash) if I set `depthTest` or `cssClasses` on a group type they don't
   apply to (e.g. `cssClasses` on a `LINE`/`SHAPE` group, or `depthTest` on a `POI` group), matching the existing
   field-mismatch warning pattern for `icon`/`fillColor`/etc.
8. As a server admin, I want a malformed `sorting` value (non-integer) to fall back to its default and log a
   warning rather than fail config load, matching existing malformed-field handling.

## Implementation Decisions

- **`MarkerGroup` record** gains four fields: `sorting` (int, default `0`, all types), `toggleable` (boolean,
  default `true`, all types), `depthTest` (boolean, default `true`, `LINE`/`SHAPE` only), `cssClasses`
  (`List<String>`, default empty, `POI` only).
- **`ConfigProvider`**: add `resolveSorting`/`resolveToggleable`/`resolveDepthTest`/`resolveCssClasses`, mirroring
  the existing `resolveLineWidth`/`resolveLineColor` pattern — malformed input falls back to default and logs a
  warning; field-mismatch warnings extend to cover `depthTest`-on-`POI` and `cssClasses`-on-`LINE`/`SHAPE`.
- **`BlueMapAPIConnector`**:
  - `getOrCreateMarkerSet` (where `MarkerSet.builder()` is called, ~line 443-447): add
    `.sorting(markerGroup.sorting())` and `.toggleable(markerGroup.toggleable())` alongside the existing
    `.defaultHidden(...)` call.
  - `setLineMarker`/`setShapeMarker`: add `.depthTestEnabled(markerGroup.depthTest())` to the existing
    `LineMarker.builder()`/`ShapeMarker.builder()` chains.
  - POI creation path: add `.styleClasses(markerGroup.cssClasses().toArray(new String[0]))` to the existing
    `POIMarker.builder()` chain (only when non-empty, since BlueMap's builder treats an empty varargs call as a
    no-op either way).
- **If `EXTRUDE` markers (`../extrude-markers`) land as a separate `MarkerGroupType`**, extend `depthTest`'s
  scope to include `EXTRUDE` (same field, same builder method — `ExtrudeMarker.Builder` has an identical
  `depthTestEnabled(boolean)`) and wire it the same way as `LINE`/`SHAPE`. Whichever of the two features lands
  second picks this up as part of its own change.
- **No persistence/schema changes**: these are group-config fields only, resolved fresh from `BMSM-Core.json` on
  every load/reload; no `SignEntry`/`SignFileVersions` involvement.
- **Docs**: `../../../README.md`'s config reference gains all four fields with their type-scope and defaults, following the
  existing field-list format.

## Testing Decisions

- **`ConfigProviderTest`**: new cases for each of the four `resolve*` methods — default-when-unset, valid value
  passthrough, malformed-value fallback-and-warn (`sorting`, since it's the only one with a "malformed" shape —
  booleans/lists just take whatever JSON gives or default), and the two new field-mismatch warnings.
- No new test seams needed elsewhere — these fields don't affect `SignTransitionResolver`, `LineGroupResolver`/
  `ShapeGroupResolver`, or `ActionFactory`, since they're passthrough styling/behavior config, not part of any
  transition or membership decision.
- `BlueMapAPIConnector`'s builder wiring is game-coupled with no unit coverage (per `../../../AGENTS.md`); verify manually
  via `runServer`: set each field, `/bluemap reload`, confirm menu order/toggle-ability/depth-test/CSS-class
  behavior in BlueMap's web UI and `custom.css`.

## Out of Scope

- Per-marker `sorting` (BlueMap's `Marker.setSorting`, distinct from `MarkerSet.setSorting`) — no good source for
  the value from sign text; `createdAtMillis` (placement order) is the only value already available, and it's not
  worth inventing a UX for typing an arbitrary sort integer on a sign.
- `link`/`new-tab` (clickable markers) and HTML markers — sign text is too length-constrained to carry a URL
  meaningfully; explicitly ruled out by the mod's owner.
- `holes` (polygon holes) on `SHAPE`/`EXTRUDE` — no membership model produces a hole today.
- `EXTRUDE` as a marker-group type — tracked separately in `../extrude-markers`.

## Further Notes

- Field verification method: extracted `bluemap-api-2.8.0.jar` from the Gradle cache and ran `javap` against
  `Marker`, `MarkerSet`, `LineMarker`, `ShapeMarker`, `POIMarker`, `ElementMarker` to confirm exact method
  signatures (`setSorting`, `isToggleable`/`setToggleable`, `isDepthTestEnabled`/`setDepthTestEnabled`,
  `getStyleClasses`/`setStyleClasses`) rather than relying on the wiki doc's prose alone.
- Raised alongside `../extrude-markers` while auditing BlueMap's marker API for v1.0 completeness; scoped as
  two separate efforts since this one is four cheap config passthroughs and extrude is a new marker-group type.
