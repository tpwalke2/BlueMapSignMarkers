# 01 — Core EXTRUDE marker type: create/update/remove happy path

**What to build:** A new `EXTRUDE` marker group type. Placing 3 or more signs that share an `EXTRUDE` group's
prefix and identical label text renders a 3D volume marker spanning the lowest-to-highest member Y, ordered by
placement time. Editing any member's text (same prefix/label) updates the volume's detail popup without breaking
or duplicating the marker. Removing members back down to 2 or fewer removes the marker.

See `../spec.md` for full context (Implementation/Testing Decisions sections) and
`../../../../docs/adr/0001-shape-points-insertion-order-no-validation.md`/`../../../../docs/adr/0002-shape-duplicates-line-pattern.md` for
the design constraints this must follow (same constraints `SHAPE` follows).

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `MarkerGroupType.EXTRUDE` exists alongside `POI`/`LINE`/`SHAPE`.
- [ ] `lineWidth`/`lineColor`/`fillColor` are valid (non-ignored, non-warned) fields on `EXTRUDE` groups, matching
      their existing `SHAPE` behavior.
- [ ] `ExtrudeGroupResolver.members(...)` resolves an extrude marker's members (same parent map, prefix, label),
      ordered by `createdAtMillis`, mirroring `ShapeGroupResolver.members`.
- [ ] `SetExtrudeMarkerAction`/`RemoveExtrudeMarkerAction`/`ExtrudeMarkerIdentifier` (id scheme `"extrude:" +
      label`) exist, mirroring the `Shape` equivalents.
- [ ] `ActionFactory.createSetExtrudeAction(...)`/`createRemoveExtrudeAction(...)` exist, mirroring the `Shape`
      equivalents.
- [ ] `SignTransitionResolver.computeTransitionAction` handles the `EXTRUDE`↔`EXTRUDE` same-group case: join/
      recompute at 1→2 (no-op), 2→3 (first appearance), 3→4+ (recompute); leave/recompute at 4→3 (recompute), 3→2
      (remove), 2→1/1→0 (no-op) — mirroring the existing `SHAPE`↔`SHAPE` branch at the same 3-member threshold.
- [ ] `BlueMapAPIConnector` renders a `SetExtrudeMarkerAction` as a BlueMap `ExtrudeMarker` (polygon from the
      ordered points' x/z, floor Y = minimum across current members, ceiling Y = maximum across current members,
      `fillColor`, `lineWidth`, `lineColor` via the existing `toBlueMapColor` alpha helper) and handles
      `RemoveExtrudeMarkerAction`, with matching `case` arms in both `processMarkerAction` and
      `logProcessingMessage` (per `../../../../AGENTS.md` — `MarkerAction` is unsealed, so a missing case silently falls
      through to `default`).
- [ ] Unit tests: `SignTransitionResolverTest` (new `EXTRUDE` join/leave/recompute rows), new
      `ExtrudeGroupResolverTest`, `ActionFactoryTest` additions for the new create/remove methods,
      `ConfigProviderTest` additions confirming `lineWidth`/`lineColor`/`fillColor` are valid on `EXTRUDE`.
- [ ] Manually verified via `runServer`: placing/editing/removing signs produces the expected volume
      creation/update/removal in BlueMap's web UI, floor/ceiling matching the shortest/tallest member.
