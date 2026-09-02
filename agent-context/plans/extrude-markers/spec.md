# Extrude (3D volume) markers

Status: ready-for-agent

GH issue: 196

## Problem Statement

`SHAPE` groups render a flat polygon at one Y height (the tallest member). There's no way to mark a bounded region
as a 3D volume spanning a height range — e.g. a building's full footprint, a claim's vertical extent, a mine's
tunnel network — without it collapsing to a flat cap.

## Solution

A fourth marker group type, `EXTRUDE`, reuses `SHAPE`'s exact membership model (signs sharing a group's prefix and
label, 3+ members, points ordered by placement time) but renders BlueMap's `ExtrudeMarker` — the same polygon
extruded as a solid volume between a minimum and maximum Y — instead of a flat `ShapeMarker`. Where `SHAPE` takes
its single Y from the tallest member, `EXTRUDE` takes two: the lowest member's Y as the floor, the tallest member's
Y as the ceiling.

## User Stories

1. As a server admin, I want to define an `EXTRUDE`-type marker group in `BMSM-Core.json`, so that I can mark
   regions as 3D volumes the same way `SHAPE` groups mark them as flat polygons.
2. As a player, I want to place 3 or more signs with the same `EXTRUDE` group's prefix and identical label text, so
   that a 3D volume marker appears once the third sign is placed — same threshold and membership rule as `SHAPE`.
3. As a player, I want the volume's floor and ceiling to be set from the lowest and highest member sign Y values
   respectively, so that placing signs at a building's base and roof captures its full height without extra config.
4. As a player, I want removing/editing/re-labeling member signs to update or remove the volume the same way it
   does for `SHAPE` (join/leave/recompute, detail-only edits, orphan-free type flips), so that `EXTRUDE` behaves
   consistently with the other multi-point group types.
5. As a server admin, I want an edited `EXTRUDE` group's `type` flip (to/from `POI`/`LINE`/`SHAPE`) to self-heal on
   `/bluemap reload` without leaving an orphaned marker, matching every other type-flip pair today.
6. As a server admin, I want `lineWidth`/`lineColor`/`fillColor` to apply to `EXTRUDE` groups the same way they
   apply to `SHAPE` (border + fill styling), so that I don't configure volume styling differently from flat shapes.
7. As a server admin, I want a warning (not a crash) for `EXTRUDE`-group field mismatches (`icon`/`offsetX`/
   `offsetY` set on `EXTRUDE`), matching the existing `SHAPE` mismatch-warning pattern.
8. As a server admin, I want persisted sign data and existing region-sharded storage/versioning to require no
   changes for this feature, so that upgrading to a version with `EXTRUDE` support doesn't require a migration.
9. As a developer, I want `EXTRUDE`'s membership resolution, join/leave/recompute logic, and marker-action
   construction tested at the same seams `SHAPE`'s equivalents already are.

## Implementation Decisions

- **New `MarkerGroupType.EXTRUDE`** value, alongside existing `POI`/`LINE`/`SHAPE`.
- **Membership rule, render threshold (3+), point order (`createdAtMillis`)**: identical to `SHAPE` —
  `ExtrudeGroupResolver.members(...)` delegates straight to `LineGroupResolver.members` (same pattern `SHAPE` used;
  see `../../../docs/adr/0002-shape-duplicates-line-pattern.md`).
- **Two Y values instead of one**: floor = minimum Y across all current members, ceiling = maximum Y across all
  current members (mirrors `SHAPE`'s existing max-Y computation in `BlueMapAPIConnector.setShapeMarker`, adding a
  matching min-Y computation). Recomputed on every join/leave/recompute, independent of placement order.
- **Config fields on `MarkerGroup`**: `lineWidth`/`lineColor`/`fillColor` scope widens from "`LINE`/`SHAPE`" (or
  "`SHAPE`" for `fillColor`) to include `EXTRUDE` — same hex/int parsing, same defaults, no new parsing logic.
  `icon`/`offsetX`/`offsetY` field-mismatch warnings extend to `EXTRUDE` (warn-and-ignore, same as `SHAPE` today).
  If `../marker-polish`'s `depthTest` field has landed by the time this is implemented, extend its scope to
  `EXTRUDE` too (`ExtrudeMarker.Builder` has the identical `depthTestEnabled(boolean)` method `LineMarker`/
  `ShapeMarker` have) — otherwise leave a short TODO note for that follow-up.
- **New types mirroring `SHAPE`'s existing structure** (duplicated pattern, consistent with ADR 0002):
  - `ExtrudeGroupResolver.members(...)`.
  - `SetExtrudeMarkerAction`/`RemoveExtrudeMarkerAction` — mirror `SetShapeMarkerAction`/`RemoveShapeMarkerAction`;
    `SetExtrudeMarkerAction` carries label, detail, ordered points, `fillColor`, `lineWidth`, `lineColor`, and the
    log-only `isFirstAppearance` flag (same fields as `SetShapeMarkerAction` — no min/max Y field needed on the
    action itself, since `BlueMapAPIConnector` computes both from the ordered points the same way it computes
    `SHAPE`'s single Y today).
  - `ExtrudeMarkerIdentifier` — mirrors `ShapeMarkerIdentifier`, id scheme `"extrude:" + label`.
  - `ActionFactory.createSetExtrudeAction(...)`/`createRemoveExtrudeAction(...)` — mirror the `Shape` equivalents.
- **`SignTransitionResolver.computeTransitionAction`**: extend the `POI`/`LINE`/`SHAPE` 3-way switch to a 4-way
  switch covering `EXTRUDE`, including `EXTRUDE`↔`EXTRUDE` same-group recompute (mirroring the `SHAPE`↔`SHAPE`
  branch) and `extrudeJoinAction`/`extrudeLeaveAction` at the same 3-member threshold as `SHAPE`.
- **`BlueMapAPIConnector`**: add `case SetExtrudeMarkerAction`/`case RemoveExtrudeMarkerAction` arms to both
  `processMarkerAction`'s and `logProcessingMessage`'s switches (required per `../../../AGENTS.md` — `MarkerAction` is
  unsealed); `setExtrudeMarker` builds a `de.bluecolored.bluemap.api.markers.ExtrudeMarker` via
  `ExtrudeMarker.builder().shape(shape, minY, maxY)...` from the ordered points' x/z, `fillColor`, `lineWidth`,
  `lineColor` — same alpha-conversion helper (`toBlueMapColor`) `SHAPE`/`LINE` already use.
- **No persistence/schema changes**: same as `SHAPE` — no new `SignEntry` field or `SignFileVersions` bump.
- **Docs**: `../../../README.md` gains `EXTRUDE` as a valid `type`, notes it shares `SHAPE`'s config fields plus its own
  floor/ceiling behavior, and an example `EXTRUDE` group block.

## Testing Decisions

Same four seams `SHAPE` established, extended one type further:

- **`SignTransitionResolverTest`**: `EXTRUDE` join at 1→2 (no-op), 2→3 (first appearance), 3→4+ (recompute);
  leave at 4→3 (recompute), 3→2 (remove), 2→1/1→0 (no-op); `EXTRUDE`↔`EXTRUDE` same-group detail-only edit; every
  `POI`/`LINE`/`SHAPE`↔`EXTRUDE` type-flip pair (both directions), including `isReload` variants.
- **`ExtrudeGroupResolverTest`**: mirrors `ShapeGroupResolverTest` — filter by parent map/prefix/label, order by
  `createdAtMillis`.
- **`ActionFactoryTest`**: `createSetExtrudeAction`/`createRemoveExtrudeAction` construction cases.
- **`ConfigProviderTest`**: `lineWidth`/`lineColor`/`fillColor` now valid (no warning) on `EXTRUDE`; new
  `icon`/`offsetX`/`offsetY`-on-`EXTRUDE` mismatch warnings.

No new tests for `ColorUtils` (generic, already covered) or `BlueMapAPIConnector` (game-coupled, manual `runServer`
verification only, per `../../../AGENTS.md`).

## Out of Scope

- `holes` (BlueMap's polygon-hole support on `ExtrudeMarker`) — no membership model produces a hole.
- Spatial point ordering, convexity enforcement, or self-intersection detection — same limitation `LINE`/`SHAPE`
  already carry (ADR `0001-shape-points-insertion-order-no-validation`).
- A generic "multi-point marker group" abstraction shared across `LINE`/`SHAPE`/`EXTRUDE`.
- Any sign-persistence schema change or migration.
- Manual/in-game verification of the `BlueMapAPIConnector` rendering path — tracked as a manual `runServer` check,
  not part of this spec's automated test scope.

## Further Notes

- Field/API verification: extracted `bluemap-api-2.8.0.jar` from the Gradle cache and ran `javap` against
  `ExtrudeMarker`/`ExtrudeMarker$Builder` to confirm `shape(Shape, float minY, float maxY)`, `depthTestEnabled`,
  `lineWidth`, `lineColor`, `fillColor` are all present with the same shape as `ShapeMarker$Builder`.
- Raised alongside `../marker-polish` while auditing BlueMap's marker API for v1.0 completeness. If
  `marker-polish` lands first, this spec's `depthTest` note applies; if this lands first, `marker-polish`'s ticket
  02 should extend its own `depthTestEnabled` wiring to cover `EXTRUDE` too.
- Sized comparably to `../shape-markers` (`SHAPE`'s own build-out) — expect a similar-sized change: new
  type, new resolver/actions/identifier, transition-table extension, config validation, docs.
