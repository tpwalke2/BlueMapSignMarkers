# 04 — Marker-action model: line action types, generalized identifier

**Spec:** `.scratch/line-markers/spec.md` §4 ("`MarkerAction`/`ActionFactory`: two new subtypes, generalized identifier")

**Blocked by:** None (independent of 01-03; only touches action/identifier types).

**What to build:**
- New `core/markers/LinePoint.java` — plain record `(int x, int y, int z)`.
- New `core/markers/DispatchedMarkerIdentifier.java`:
  ```java
  public interface DispatchedMarkerIdentifier {
      MarkerSetIdentifier parentSet();
      String getId();
  }
  ```
- `core/markers/MarkerIdentifier.java` — implements `DispatchedMarkerIdentifier` (unchanged otherwise).
- New `core/markers/LineMarkerIdentifier.java` — `record LineMarkerIdentifier(String label, MarkerSetIdentifier parentSet) implements DispatchedMarkerIdentifier`, `getId()` → `"line:" + label`.
- `core/bluemap/actions/MarkerAction.java` — field type widens from `MarkerIdentifier` to
  `DispatchedMarkerIdentifier`; drop `getX/Y/Z()` (only ever used for logging) in favor of
  `getMarkerIdentifier(): DispatchedMarkerIdentifier`. `AddMarkerAction`/`UpdateMarkerAction`/`RemoveMarkerAction`
  need no changes beyond this — they already just pass a `MarkerIdentifier` to `super()`, which still satisfies
  the narrower interface.
- New `core/bluemap/actions/SetLineMarkerAction.java` — `label`, `detail`, `List<LinePoint>`, `lineWidth`,
  `lineColor`, and a log-only `boolean isFirstAppearance` flag (not a distinct subtype).
- New `core/bluemap/actions/RemoveLineMarkerAction.java` — just the identifier.
- `core/bluemap/actions/ChangeGroupMarkerAction.java` → replace with new
  `core/bluemap/actions/GroupTransitionMarkerAction.java(List<MarkerAction> effects)` (0-2 entries) — generalizes
  the fixed 2-action remove+add bundle to a variable count, for the same "no ordering guarantee between
  independently-submitted `ReactiveQueue` messages" reason `ChangeGroupMarkerAction` already existed.
- `core/bluemap/actions/ActionFactory.java` — add `createSetLineAction(mapId, markerGroup, label, detail, points, isFirstAppearance)` and `createRemoveLineAction(mapId, markerGroup, label)`, following the existing `create*POIAction`
  pattern; `createChangeGroupPOIAction` → builds a `GroupTransitionMarkerAction`.

**Status:** done

- [x] `LinePoint`, `DispatchedMarkerIdentifier`, `LineMarkerIdentifier` added
- [x] `MarkerAction` widened to `DispatchedMarkerIdentifier`, `getX/Y/Z()` removed, existing subtypes compile
      unchanged
- [x] `SetLineMarkerAction`, `RemoveLineMarkerAction` added
- [x] `ChangeGroupMarkerAction` replaced by `GroupTransitionMarkerAction`; all call sites updated
- [x] `ActionFactory` gains `createSetLineAction`/`createRemoveLineAction`; `createChangeGroupPOIAction` updated
- [x] `./gradlew test` and `./gradlew build` pass (note: `BlueMapAPIConnector`'s switch statements won't have cases
      for the new action types yet — that's ticket 05; confirm they still compile via the existing `default`
      fallthrough, not a compile error, per `AGENTS.md`'s note that `MarkerAction` isn't sealed)
