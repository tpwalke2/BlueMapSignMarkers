# 02 — Type-flip transitions: POI/LINE ↔ SHAPE, and reload self-heal

**What to build:** Moving a sign between a `POI`/`LINE` group and a `SHAPE` group (by editing its prefix so it now
matches a different group) removes the old marker and adds/updates the new one, with no orphaned marker left behind
in BlueMap's UI — matching the existing `POI`↔`LINE` type-flip behavior. Separately, editing a `SHAPE` group's
`type` in config (e.g. `SHAPE` → `LINE` or vice versa) and running `/bluemap reload` self-heals every affected sign
without a server restart, the same way `POI`↔`LINE` type flips already self-heal today.

See `../spec.md` for full context.

**Blocked by:** 01 — requires the core `SHAPE` join/leave/recompute logic and `ShapeMarkerIdentifier`/action types.

**Status:** ready-for-review

- [x] `SignTransitionResolver.computeTransitionAction` handles every remaining `POI`/`LINE`/`SHAPE` combination
      (`POI`→`SHAPE`, `SHAPE`→`POI`, `LINE`→`SHAPE`, `SHAPE`→`LINE`, in both directions), dispatching a
      `GroupTransitionMarkerAction` bundling the old representation's leave-effect and the new representation's
      join-effect, mirroring the existing `POI`↔`LINE` generic branch.
- [x] A sign moving out of a `SHAPE` group correctly recomputes/removes the old shape based on remaining membership
      (same leave semantics as ticket 01's `SHAPE`↔`SHAPE` leave case).
- [x] A sign moving into a `SHAPE` group correctly recomputes/creates the new shape based on resulting membership
      (same join semantics as ticket 01's `SHAPE`↔`SHAPE` join case).
- [x] `SignManager.reloadConfig()`'s reload self-heal path (`IResetHandler.reset()`) correctly diffs old-vs-new
      representation for every cached sign when a `SHAPE` group's `type` changes in config, dispatching the same
      transition actions as a live prefix edit would.
- [x] Unit tests: `SignTransitionResolverTest` rows for every `POI`/`LINE`↔`SHAPE` combination, including the
      `isReload` variants (mirroring the existing `POI`↔`LINE` reload test coverage).
- [ ] Manually verified via `runServer`: editing a sign's prefix across `POI`/`LINE`/`SHAPE` groups, and flipping a
      `SHAPE` group's `type` in config followed by `/bluemap reload`, leave no orphaned markers in BlueMap's UI.

## Comments

Completed as a side effect of ticket 01: `SignTransitionResolver.computeTransitionAction` was refactored so
`leaveEffect`/`joinEffect` dispatch on `MarkerGroupType` generically (POI/LINE/SHAPE) rather than a POI-vs-other
binary, so the existing leave+join bundling path already covers every `POI`/`LINE`/`SHAPE` type-flip combination
without type-pair-specific code. `SignManager.reloadConfig()`'s self-heal needed no changes - it was already
generic over `Representation`/`computeTransitionAction`, with no hardcoded `MarkerGroupType` branching of its own.
Added dedicated `SignTransitionResolverTest` cases: `poiToShapeBundlesRemovePoiAndSetShape`,
`shapeToPoiBundlesSetShapeAndAddPoi`, `lineToShapeBundlesRemoveLineAndSetShape`,
`shapeToLineBundlesRemoveShapeAndSetLine`, and an `isReload` variant of each four. See ticket 01's Comments for
the full implementation summary. Manual `runServer` verification still outstanding.
