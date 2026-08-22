# Shape (polygon) markers

Status: ready-for-agent

GH issue: https://github.com/tpwalke2/BlueMapSignMarkers/issues/8

## Problem Statement

Players can already mark a single point (`POI`) or a path (`LINE`) on the BlueMap map by placing prefixed signs.
There's no way to highlight a bounded region — a claim, a district, a farm's territory — without placing a `LINE`
around its border that doesn't actually enclose or fill anything.

## Solution

A third marker group type, `SHAPE`, works like `LINE` (signs sharing a group's prefix and label become one marker,
recomputed as members are added/removed) but renders a closed, filled polygon instead of an open path. Placing 3+
signs with the same `SHAPE`-group prefix and label text draws a filled polygon connecting them; editing, moving, or
removing any member sign updates or removes the shape the same way editing a `LINE` member does today.

## User Stories

1. As a server admin, I want to define a `SHAPE`-type marker group in `BMSM-Core.json`, so that I can let players
   mark out regions the same way `POI`/`LINE` groups already let them mark points/paths.
2. As a player, I want to place 3 or more signs with the same `SHAPE` group's prefix and identical label text, so
   that a filled polygon marker appears on the map once the third sign is placed.
3. As a player, I want the polygon to disappear once membership drops back to 2 or fewer signs, so that a shape I'm
   dismantling doesn't leave a stale marker behind.
4. As a player, I want editing any one member sign's text (while keeping the same prefix/label) to update the
   shape's detail popup without breaking or duplicating the marker, so that in-place edits behave the same as they
   do for `POI`/`LINE`.
5. As a player, I want to change a member sign's label (splitting it off into a different shape or a plain `POI`),
   so that the polygon recomputes to exclude that point (or disappears if that drops membership below 3), and no
   orphaned marker is left under the old id.
6. As a player, I want to change a sign's prefix so it moves from a `POI`/`LINE` group into a `SHAPE` group (or vice
   versa), so that the old marker is removed and the new one appears, with no duplicate or leftover marker in
   BlueMap's UI.
7. As a server admin, I want an edited `SHAPE` group's `type` flip (e.g. `SHAPE` → `LINE`) to take effect on
   `/bluemap reload` without a server restart, and without leaving the old shape/line marker behind — matching how
   `POI`↔`LINE` type flips already self-heal on reload.
8. As a server admin, I want to configure a `SHAPE` group's `fillColor` (hex, with alpha) independently of its
   `lineColor`/`lineWidth` border styling, so that I can make a shape's interior translucent while its border stays
   opaque (or vice versa).
9. As a server admin, I want `lineWidth`/`lineColor` to be valid fields on both `LINE` and `SHAPE` groups (not just
   `LINE`), so that I don't have to duplicate border-styling config between the two multi-point group types.
10. As a server admin, I want a sane default `fillColor` (translucent, not opaque) when I don't set one, so that a
    `SHAPE` group I configure without styling doesn't blot out the map underneath it.
11. As a server admin, I want a malformed `fillColor`/`lineColor`/`lineWidth` value in config to fall back to its
    default and log a warning rather than crash the server or fail to load, matching existing `LINE` field
    validation behavior.
12. As a server admin, I want to see a warning (not a crash) if I set a `POI`/`LINE`-only field (`icon`,
    `offsetX`/`offsetY`) on a `SHAPE` group, or set `fillColor` on a `POI`/`LINE` group, so that misconfiguration is
    visible without breaking the server.
13. As a player, I want the shape's points to reflect the order I placed the member signs in, so that placing signs
    in a walk around the region's perimeter produces the polygon I expect (and placing them out of order produces a
    self-intersecting shape I can fix by re-placing/editing signs) — the same placement-order contract `LINE`
    already has.
14. As a player, I want the shape's rendered height (Y) to be the height of whichever member sign is tallest,
    so that the polygon sits at one consistent Y that clears every member sign's build, even though member signs
    may be at different heights.
15. As a player, I want the shape's detail popup to show the members' shared label text, so that hovering/clicking
    the polygon tells me what it represents (matching `LINE`'s current detail behavior).
16. As a server admin, I want persisted sign data and existing region-sharded storage/versioning to require no
    changes for this feature, so that upgrading to a version with `SHAPE` support doesn't require a migration.
17. As a developer, I want `SHAPE`'s group-membership resolution, join/leave/recompute logic, and marker-action
    construction tested at the same seams `LINE`'s equivalents already are, so that the transition table and
    supporting pure functions have direct unit coverage without needing a live BlueMap/Minecraft environment.

## Implementation Decisions

- **New `MarkerGroupType.SHAPE`** value, alongside existing `POI`/`LINE`.
- **Membership rule**: identical to `LINE` — all signs sharing a `SHAPE` group's prefix and the same label text
  (post-prefix-strip) are members of one shape; that `(prefix, label)` pair is the shape's identity.
- **Render threshold**: a shape marker is created once 3+ members exist (vs. `LINE`'s 2), and removed once
  membership drops back to 2 or fewer. `isFirstAppearance` (log-only, mirrors `LINE`'s convention) is true the first
  time membership reaches exactly 3.
- **Point order**: members ordered by `createdAtMillis` (placement time), same as `LineGroupResolver.members` — no
  spatial sorting, no self-intersection/degeneracy validation. This carries `LINE`'s existing documented limitation
  forward; see ADR `0001-shape-points-insertion-order-no-validation`.
- **Geometry**: a flat BlueMap `ShapeMarker` (2D polygon, single Y for the whole shape, with a border and a fill) —
  not an extruded 3D volume.
- **Shape Y height**: taken from the tallest member (maximum Y across all current members), recomputed on every
  join/leave/recompute — independent of placement order/point order, which is still by `createdAtMillis`.
- **Marker detail**: the shape's detail popup shows the members' shared label text (same value used for the label),
  mirroring `LINE`'s current `createSetLineAction`-style behavior of passing the label as both label and detail.
- **Config fields on `MarkerGroup`**:
  - `lineWidth`/`lineColor` scope widens from "`LINE` only" to "`LINE`/`SHAPE` only" (border styling, reused as-is).
  - New `fillColor` field (`SHAPE`-only), same hex-with-optional-alpha string format as `lineColor`
    (`ColorUtils.parseHex`/`isValidHex`, unchanged — no new parsing logic needed).
  - Default `fillColor` when unset: a translucent value (e.g. `#FF000033`) rather than `LINE`'s opaque
    `#FF0000FF` default — a highlighted region should default to not obscuring the map.
  - Field-mismatch warnings (`ConfigProvider`) extend to cover `SHAPE`: `icon`/`offsetX`/`offsetY` set on a `SHAPE`
    group warn-and-ignore (same as they already do for `LINE`); `fillColor` set on a `POI`/`LINE` group warns and is
    ignored; malformed `fillColor`/`lineWidth`/`lineColor` on a `SHAPE` group falls back to its default and logs a
    warning (same pattern as existing `resolveLineWidth`/`resolveLineColor`).
- **New types mirroring `LINE`'s existing structure** (duplicated pattern, not a generic abstraction — see ADR
  `0002-shape-duplicates-line-pattern`):
  - `ShapeGroupResolver.members(...)` — same shape as `LineGroupResolver.members`.
  - `SetShapeMarkerAction` / `RemoveShapeMarkerAction` — mirror `SetLineMarkerAction`/`RemoveLineMarkerAction`;
    `SetShapeMarkerAction` carries label, detail, ordered points, `fillColor`, `lineWidth`, `lineColor`, and the
    log-only `isFirstAppearance` flag.
  - `ShapeMarkerIdentifier` — mirrors `LineMarkerIdentifier`, id scheme `"shape:" + label`.
  - `ActionFactory.createSetShapeAction(...)` / `createRemoveShapeAction(...)` — mirror the `Line` equivalents.
  - Where the resulting logic is genuinely identical between `LINE` and `SHAPE` (e.g. the join/leave/threshold
    recompute shape), factor it into shared pure functions rather than copy-pasting, without introducing a generic
    "multi-point group" type.
- **`SignTransitionResolver.computeTransitionAction`**: extend the type-pair branching (currently `POI`/`LINE`
  only) to a 3-way switch covering every `POI`/`LINE`/`SHAPE` combination, including `SHAPE`↔`SHAPE` same-group
  recompute (mirroring the existing `LINE`↔`LINE` branch) and `SHAPE` join/leave helpers (mirroring
  `lineJoinAction`/`lineLeaveAction`) used both for live sign edits and the `/bluemap reload` self-heal path.
- **`BlueMapAPIConnector`**: add a `case SetShapeMarkerAction` / `case RemoveShapeMarkerAction` arm to both
  `processMarkerAction`'s switch and `logProcessingMessage`'s switch (required per `../../../AGENTS.md` — `MarkerAction` is
  an unsealed abstract class, so a missing case silently falls into `default` instead of failing to compile);
  `setShapeMarker` builds a `de.bluecolored.bluemap.api.markers.ShapeMarker` from the ordered points (x/z only,
  BlueMap's `Shape` type), the anchor Y, `fillColor`, `lineWidth`, and `lineColor`.
- **No persistence/schema changes**: `SHAPE` groups use the same `SignEntry`/region-sharded storage as `POI`/`LINE`
  today; no new sign-persistence field or `SignFileVersions` bump is needed.
- **Docs**: `../../../README.md`'s config reference gains `SHAPE` as a valid `type`, documents `fillColor` (and the widened
  `lineWidth`/`lineColor` scope), and an example `SHAPE` group block alongside the existing `LINE` example.

## Testing Decisions

Good tests here exercise externally-observable behavior (given a set of signs/config, what marker action comes
out) rather than internal wiring — same standard the existing `LINE` tests already hold to. Four seams, matching
the ones already established for `LINE` (no new seams introduced):

- **`SignTransitionResolverTest`** (primary seam) — table-driven, one test per new branch/sub-case: `SHAPE` join at
  membership 1→2 (no-op, below threshold), 2→3 (first appearance), 3→4+ (recompute); `SHAPE` leave at 4→3
  (recompute), 3→2 (remove), 2→1/1→0 (no-op); `SHAPE`↔`SHAPE` same-group detail-only edit; every `POI`↔`SHAPE` and
  `LINE`↔`SHAPE` type-flip pair (both directions), including the `isReload` variants exercised by the existing
  `LINE`/`POI` reload tests.
- **`ShapeGroupResolverTest`** — new file mirroring `LineGroupResolverTest`: filters by parent map, prefix, and
  label; orders by `createdAtMillis`.
- **`ActionFactoryTest`** — extend with `createSetShapeAction`/`createRemoveShapeAction` construction cases
  (correct id scheme, correct field passthrough), mirroring the existing `createSetLineAction`/
  `createRemoveLineAction` cases.
- **`ConfigProviderTest`** — extend with `SHAPE` field resolution/defaulting: `fillColor` default and malformed-hex
  fallback (mirroring `resolveLineColor`'s existing test cases), `lineWidth`/`lineColor` now valid (no warning) on
  `SHAPE`, and the new `fillColor`-on-`POI`/`LINE` and `icon`/`offsetX`/`offsetY`-on-`SHAPE` mismatch warnings.

No new tests needed for `ColorUtils` (already generically covered by `ColorUtilsTest`) or `BlueMapAPIConnector`
(game-coupled, verified manually via `runServer` per `../../../AGENTS.md`, same as `LINE`'s `setLineMarker`).

## Out of Scope

- Extruded/3D volume shapes (BlueMap's `ExtrudeMarker`) — flat `ShapeMarker` only.
- Spatial point ordering, convexity enforcement, or self-intersection detection/warnings.
- Per-member detail aggregation (richer shape detail text beyond the shared label) — this would be a separate
  enhancement, applicable to `LINE` too if ever wanted.
- A generic "multi-point marker group" abstraction shared between `LINE` and `SHAPE`.
- Any sign-persistence schema change or migration.
- Manual/in-game verification of the `BlueMapAPIConnector` rendering path (tracked as a manual `runServer` check
  per `../../../AGENTS.md`'s testable-vs-game-coupled split, not part of this spec's automated test scope).

## Further Notes

- ADRs recorded during design: `../../../docs/adr/0001-shape-points-insertion-order-no-validation.md`,
  `../../../docs/adr/0002-shape-duplicates-line-pattern.md`.
- Glossary terms (`Shape`, `Label`, `Representation`) recorded in `../../../CONTEXT.md`.
- Original GH issue (#8, title `[area] builds area markersets`) had no body — the "area" language in its title was
  deliberately renamed to `SHAPE` in code/config/docs to match BlueMap's own marker naming and to distinguish the
  concrete geometric object (a polygon) from the vaguer goal it achieves (highlighting an area).
