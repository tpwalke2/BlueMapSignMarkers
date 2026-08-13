# 11 — `ActionFactoryTest` has no coverage for the line-marker factory methods

**What to build:** `ActionFactory` (`core/bluemap/actions/ActionFactory.java`) gained two methods with the
line-markers feature — `createSetLineAction` (line 75) and `createRemoveLineAction` (line 92) — alongside the
pre-existing `createAddPOIAction`/`createRemovePOIAction`/`createUpdatePOIAction`/`createChangeGroupPOIAction`,
all of which already have `ActionFactoryTest` coverage. The two line methods have none.

Add test methods (following `ActionFactoryTest`'s existing pattern — one method per factory method, asserting the
built action's fields and the `MarkerIdentifier`/`MarkerSetIdentifier` it resolved):
- `createSetLineAction` — builds a `SetLineMarkerAction` with the right `LineMarkerIdentifier`/points/label/detail
  for a `LINE`-type `MarkerGroup`.
- `createRemoveLineAction` — builds a `RemoveLineMarkerAction` with the right `LineMarkerIdentifier` for a
  `(mapId, markerGroup, label)` triple.
- Repeated calls for the same `(mapId, markerGroup)` pair reuse the same `MarkerSetIdentifier` instance via
  `MarkerSetIdentifierCollection` — mirroring the existing assertion already made for the POI-action factory
  methods, now extended to the line ones.

**Blocked by:** None.

**Status:** open

- [ ] `createSetLineAction` has a dedicated test asserting the built `SetLineMarkerAction`'s fields
- [ ] `createRemoveLineAction` has a dedicated test asserting the built `RemoveLineMarkerAction`'s fields
- [ ] Both are covered by the existing `MarkerSetIdentifierCollection` reuse assertion pattern
- [ ] Full test suite still passes

## Comments

Found while updating `agent-context/context/testing.md` for the line-markers feature (2026-08-13) — the doc
update surfaced that `ActionFactoryTest`'s bullet only mentions the four pre-existing factory methods, and
checking the actual test file confirmed the two new ones have no test methods at all, not just an outdated doc
description.
