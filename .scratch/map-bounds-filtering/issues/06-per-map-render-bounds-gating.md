# 06 — Per-map render-bounds gating in BlueMapAPIConnector

**What to build:** The full end-to-end feature: a sign's marker only exists on a given BlueMap map
if its position (POI) or at least one member point (LINE/SHAPE, all-or-nothing per map — no
per-point clipping) is inside that map's render bounds, using ticket 05's evaluator. This requires
restructuring `markerSetsCache`/`getMarkerSets`/`applyToMarkerSets` so each cached `MarkerSet`
retains its originating real `BlueMapMap` id through to apply-time (today it flattens to a bare
list, losing per-map identity), then gating every add/update/set against that map's render-mask
result — cached per real map id and invalidated alongside `markerSetsCache` (on config reload and
on a genuine BlueMap disable/enable cycle). When the gate fails, it must actively remove the
marker id from that map's `MarkerSet` rather than merely skip the add/update/set — this is what
sweeps a marker that was already sitting on that map before this feature shipped, since
`SignManager.reset()` already force-re-dispatches an add/set for every sign on every
`/bluemap reload`. Explicit remove actions (a sign's representation genuinely leaving) are
unaffected — they continue to remove unconditionally on every real map, no masking needed. No
changes to `MarkerSetIdentifier`, `ActionFactory`, `SignTransitionResolver`, `SignManager`, or
persisted sign data.

**Blocked by:** 05

**Status:** done — implemented and fully manually verified (POI gating, reload re-evaluation,
LINE/SHAPE gating, upgrade sweep) via `runServer` using
`world_the_nether.conf`/`world_nether_roof.conf`.

- [x] A `[poi]` sign placed outside a map's configured render bounds does not get a marker created
      on that map, while an identical sign inside those bounds does.
- [x] The same sign's marker is gated independently per map — present on a map whose bounds include
      its position, absent from another map (same world) whose bounds exclude it.
- [x] A map with no `render-mask` configured behaves exactly as before this feature — every sign
      gets a marker there, regardless of position.
- [x] Moving a sign (remove + replace) from inside a map's bounds to outside them removes its
      marker from that map; moving the reverse direction adds it.
- [x] Editing a map's `render-mask` and running `/bluemap reload` re-evaluates every existing sign
      against the new bounds on that map, with no server restart required.
- [x] A LINE marker appears on a map if any one of its member signs is inside that map's bounds,
      and is removed from that map once none of its members are inside those bounds. SHAPE behaves
      the same way. (Manually verified via `runServer`.)
- [x] A marker manually placed on a map (simulating one created by a pre-this-feature version of
      the mod) outside that map's configured bounds is removed the next time `/bluemap reload`
      runs — the "existing marker sweep on upgrade" behavior from
      `.scratch/map-bounds-filtering/issues/04-existing-marker-sweep-on-upgrade.md`. (Manually
      verified via `runServer`.)
- [x] A plain server restart (no `/bluemap reload`) does not attempt this sweep — documented, not
      changed, per ticket 04's caveat; the mod's user-facing docs/changelog note that
      `/bluemap reload` must be run once after upgrading to sweep pre-existing out-of-bounds
      markers.
- [x] Verified manually via `runServer` (no automated coverage added for `BlueMapAPIConnector`,
      consistent with its existing convention) per the plan's Testing Decisions.

**Implementation notes:**
- `markerSetsCache` now caches `List<MappedMarkerSet>` (a private `record MappedMarkerSet(String
  mapId, MarkerSet markerSet)`), not a bare `List<MarkerSet>`, so each cached `MarkerSet` keeps its
  originating real `BlueMapMap.getId()` through to apply-time.
- `RenderMaskEvaluator` gained a public `RenderMask` type and `load(mapId, mapsConfigDir)` factory
  (`isInsideRenderBounds` now delegates to it) so `BlueMapAPIConnector` can parse a map's
  render-mask once and reuse it across many point tests, cached in a new `renderMaskCache` field
  keyed by real map id, cleared in both `clearMarkerSetsCache()` and `resetQueue()`.
- `applySingleAction` splits into `applyGatedToMarkerSets` (Add/Update/SetLine/SetShape — tests the
  action's point(s) per map, actively removing the marker id on a gate failure instead of
  skipping) and `applyToAllMarkerSets` (Remove/RemoveLine/RemoveShape — unconditional, unchanged
  behavior).
- No changes to `MarkerSetIdentifier`, `ActionFactory`, `SignTransitionResolver`, `SignManager`, or
  persisted sign data.
- `./gradlew build` (unit tests + full build) passes.
