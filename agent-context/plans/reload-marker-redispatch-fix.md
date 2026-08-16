# Plan: fix markers disappearing on `/bluemap reload`

## Bug

Every `/bluemap reload` wipes all markers from the map, silently (no console errors), on
`feature/tpwalke2/7-line-markers`. Confirmed by live A/B test: `main` reloads fine (console log
entries per sign, markers stay visible); this branch drops everything.

## Root cause

1. `/bluemap reload` runs `Plugin.unload()` then `Plugin.load()` — a brand-new `BlueMapAPIImpl` gets
   registered, firing every plugin's `onDisable` then `onEnable` (confirmed from BlueMap's own
   compiled classes + API Javadoc: "The Consumer can be called multiple times if BlueMap disables
   and enables again, e.g. if BlueMap gets reloaded!"). This mod never calls
   `MarkerSet`/`Marker.save()`, so nothing it added survives that swap — every marker is gone once
   the old API instance is torn down.
2. `SignManager.reset()` (fired from `onEnable`) is the only thing that recreates them: it diffs
   every cached sign's old vs. new `Representation` through
   `SignTransitionResolver.computeTransitionAction` and dispatches whatever action comes back.
3. Two commits on this branch (`c0800e2`, `1b7a0af`) made that diff return `null` (no dispatch)
   whenever a sign's representation is unchanged. Correct for the live-edit path
   (`addOrUpdateSign`/`removeByKey`/`CHUNK_LOAD`, where BlueMap's marker state is intact and
   skipping redundant work is right) — wrong for `reset()`, where "unchanged" doesn't mean "BlueMap
   still has it."
4. Even without the no-op-skip, the POI unchanged branch dispatches `createUpdatePOIAction`, and
   `BlueMapAPIConnector.updateMarker()` (`BlueMapAPIConnector.java:216`) silently no-ops if the
   marker isn't already present in the map (`if (marker.isEmpty()) return;`). So `reset()` must force
   an **Add**, not an Update, for that branch. `SetLineMarkerAction`'s handler
   (`setLineMarker`, `BlueMapAPIConnector.java:240`) already does an unconditional `put`, so LINE is
   fine once the no-op-skip stops blocking it.

## Fix

Add an `isReload` boolean parameter to `SignTransitionResolver.computeTransitionAction`, passed
`true` only from `SignManager.reset()`'s diff loop (`reloadConfig`); every other call site
(`addOrUpdateSign`, `removeByKey`) passes `false`.

- Unchanged-POI branch (`SignTransitionResolver.java:75-82`): when `isReload` is `true`, return
  `actionFactory.createAddPOIAction(...)` instead of `null`.
- Unchanged-LINE branch (`SignTransitionResolver.java:85-89`): when `isReload` is `true`, return
  `actionFactory.createSetLineAction(...)` instead of `null`.
- All other branches (add/remove/group-transition/leave-join) already dispatch real actions
  regardless of this flag — no change needed there.

Live-edit call sites keep passing `false`, so their no-op-skip (and the perf win from `c0800e2`'s
`allSigns` snapshot threading) is untouched.

## Test

Extend `SignTransitionResolverTest` with two new cases:

- Same POI group/label/detail, `isReload=true` → returns `createAddPOIAction` (not `null`).
- Same LINE group/label/detail, `isReload=true` → returns `createSetLineAction` (not `null`).

Existing `poiToPoiSameGroupAndLabelTextUnchangedIsNoOp` and
`lineToLineSameGroupAndLabelDetailUnchangedIsNoOp` cases stay as-is (call with `isReload=false`),
asserting the live-edit path still no-ops.

## Verification

`./gradlew test`, then manually via `runServer`: place a sign, `/bluemap reload`, confirm the
marker survives and a log line for it appears — matching `main`'s observed behavior.
