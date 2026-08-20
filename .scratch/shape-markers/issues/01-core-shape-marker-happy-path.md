# 01 — Core SHAPE marker type: create/update/remove happy path

**What to build:** A new `SHAPE` marker group type. Placing 3 or more signs that share a `SHAPE` group's prefix and
identical label text renders a filled polygon on the map, anchored at the Y height of the first-placed member and
ordered by placement time. Editing any member's text (same prefix/label) updates the shape's detail popup without
breaking or duplicating the marker. Removing members back down to 2 or fewer removes the marker.

See `.scratch/shape-markers/spec.md` for full context (Implementation/Testing Decisions sections) and
`docs/adr/0001-shape-points-insertion-order-no-validation.md` / `docs/adr/0002-shape-duplicates-line-pattern.md`
for the design constraints this must follow.

**Blocked by:** None — can start immediately.

**Status:** ready-for-review

- [x] `MarkerGroupType.SHAPE` exists alongside `POI`/`LINE`.
- [x] `MarkerGroup` gains a `fillColor` field (hex-with-optional-alpha, same format/parsing as `lineColor`); a
      missing or malformed value falls back to a translucent default (e.g. `#FF000033`) and logs a warning on
      malformed input, mirroring `resolveLineColor`'s existing pattern.
- [x] `lineWidth`/`lineColor` are valid (non-ignored, non-warned) fields on `SHAPE` groups, matching their existing
      `LINE` behavior.
- [x] `ShapeGroupResolver.members(...)` resolves a shape's members (same parent map, prefix, label), ordered by
      `createdAtMillis`, mirroring `LineGroupResolver.members`.
- [x] `SetShapeMarkerAction`/`RemoveShapeMarkerAction`/`ShapeMarkerIdentifier` (id scheme `"shape:" + label`) exist,
      mirroring the `Line` equivalents.
- [x] `ActionFactory.createSetShapeAction(...)`/`createRemoveShapeAction(...)` exist, mirroring the `Line`
      equivalents.
- [x] `SignTransitionResolver.computeTransitionAction` handles the `SHAPE`↔`SHAPE` same-group case: join/recompute
      at 1→2 members (no-op), 2→3 (first appearance), 3→4+ (recompute); leave/recompute at 4→3 (recompute), 3→2
      (remove), 2→1/1→0 (no-op) — mirroring the existing `LINE`↔`LINE` branch and its 2-member threshold, but at 3.
- [x] `BlueMapAPIConnector` renders a `SetShapeMarkerAction` as a flat BlueMap `ShapeMarker` (polygon from the
      ordered points' x/z, anchor Y from the tallest member, `fillColor`, `lineWidth`, `lineColor`) and handles
      `RemoveShapeMarkerAction`, with matching `case` arms in both `processMarkerAction` and
      `logProcessingMessage` (per `AGENTS.md` — `MarkerAction` is unsealed, so a missing case silently falls
      through to `default`).
- [x] Unit tests: `SignTransitionResolverTest` (new `SHAPE` join/leave/recompute rows), new `ShapeGroupResolverTest`,
      `ActionFactoryTest` additions for the new create/remove methods, `ConfigProviderTest` additions for
      `fillColor` default/malformed-fallback resolution.
- [ ] Manually verified via `runServer`: placing/editing/removing signs produces the expected polygon
      creation/update/removal in BlueMap's web UI.

## Comments

Implemented all automated-scope items. `MarkerGroupType.SHAPE` added; `MarkerGroup` gained `fillColor` (13th
constructor arg — every call site across main/test updated); `ShapeMarkerIdentifier`/`SetShapeMarkerAction`/
`RemoveShapeMarkerAction`/`ShapeGroupResolver` mirror the `Line` equivalents (`ShapeGroupResolver.members`
delegates straight to `LineGroupResolver.members` since the filter/sort logic is identical - see ADR 0002).
`ActionFactory` gained `createSetShapeAction`/`createRemoveShapeAction`.

`SignTransitionResolver.computeTransitionAction` was refactored around two new dispatch helpers, `leaveEffect`/
`joinEffect`, that switch on `MarkerGroupType` (`POI`/`LINE`/`SHAPE`) instead of hardcoding a POI-vs-LINE binary;
this made the SHAPE↔SHAPE same-group branch a one-line addition to the existing LINE↔LINE check
(`oldType == newType && oldType != POI && sameGroupAndLabel(...)`) and, as a side effect, gave every POI/LINE/SHAPE
type-flip combination for free through the existing generic leave+join bundling path — so ticket 02's scope
(`POI`/`LINE`↔`SHAPE` flips, including the reload/`isReload` variants) is also covered, verified with dedicated
`SignTransitionResolverTest` cases for both directions. `shapeJoinAction`/`shapeLeaveAction` mirror
`lineJoinAction`/`lineLeaveAction` at a 3-member threshold instead of 2.

`BlueMapAPIConnector` renders `SetShapeMarkerAction` via BlueMap's `ShapeMarker` builder: polygon from points'
x/z (`Shape`), anchor Y from the tallest member (`max` across all current points' y, not placement order - a
deliberate deviation from the original plan of anchoring to the oldest/first-placed member, per user feedback),
plus `fillColor`/`lineWidth`/`lineColor`; matching `case` arms added to both `processMarkerAction`'s and
`logProcessingMessage`'s switches per `AGENTS.md`'s unsealed-`MarkerAction` warning.

**Follow-up fix (post-review):** two bugs found in manual review before `runServer` verification:
1. `fillColor`/`lineColor` rendered fully opaque regardless of configured alpha. Root cause: BlueMap's
   `Color(int, int, int, float alpha)` constructor takes alpha in **0-1**, but `ColorUtils.parseHex` returns
   alpha in **0-255** (matching r/g/b) - passing that int straight into the float parameter widens it (e.g.
   `51` becomes `51.0f`) instead of the intended `51/255f ≈ 0.2f`, which BlueMap then clamps to opaque. Fixed
   with a new `toBlueMapColor(int[] rgba)` helper (`rgba[3] / 255f`), used by both `setShapeMarker` and the
   pre-existing `setLineMarker` (same bug, latent there too - just never visible since `LINE`'s default alpha
   is opaque `FF` anyway).
2. Shape Y-anchor changed from "oldest (first-placed) member" to "tallest member" (max Y across all current
   members) per explicit user feedback - a deviation from `spec.md`'s original plan, now updated there too.
   Point order (by `createdAtMillis`) is unchanged and still only governs polygon vertex order, not height.

`ConfigProvider` widened `resolveLineWidth`/`resolveLineColor`'s validation scope from LINE-only to LINE/SHAPE,
added `resolveFillColor` (SHAPE-only, default `#FF000033`), and added the mismatch warnings ticket 03 asked for
(`icon`/`offsetX`/`offsetY` on SHAPE, `fillColor` on POI/LINE) - so ticket 03 is covered too. `README.md` documents
`SHAPE` as a `type` value, `fillColor`, the widened `lineWidth`/`lineColor` scope, and an example `SHAPE` group -
covering ticket 04.

All four `.scratch/shape-markers/issues/0{1,2,3,4}-*.md` checklists reflect this; each now has an equivalent
Comments note. `./gradlew build` (compiles + all 248 unit tests, including new `ShapeGroupResolverTest` and the
`SignTransitionResolverTest`/`ActionFactoryTest`/`ConfigProviderTest` additions) passes. The one item still open
across all four tickets is the manual `runServer` in-game verification (placing/editing/removing signs and
confirming the polygon/type-flip behavior in BlueMap's web UI), which is out of this session's scope per
`AGENTS.md`'s testable-vs-game-coupled split.
