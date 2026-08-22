# Grilling: Reconcile pre-existing out-of-bounds markers on upgrade

Type: grilling
Status: resolved

Blocked by: 03

## Question

Once this feature ships, servers upgrading from a prior version may already have markers created
on maps whose `render-mask` would now exclude them (the exact bug in
[GitHub issue #67](https://github.com/tpwalke2/BlueMapSignMarkers/issues/67)). Decide: do these
get swept/reconciled automatically on the first `/bluemap reload` after upgrade (folding into the
existing `SignManager.reset()` full-cache-walk, now that reset also re-evaluates per-map bounds
per the outcome of `03-dispatch-restructuring-decomposition.md`),
or only newly-(re)created/updated signs get bounds-checked, leaving stale out-of-bounds markers
until their sign is next touched? If the former, confirm `reset()`'s existing full-cache-walk
pattern is sufficient with no extra migration step; if the latter, decide whether that's an
acceptable gap given issue #67's motivation, or whether it needs its own follow-up.

## Answer

Automatic sweep on the next `/bluemap reload` — no separate migration step — with one documentation
caveat.

1. **Gate-fail-on-Add becomes an active Remove, not a skip.** `BlueMapAPIConnector.addMarker` is a
   keyed upsert into `MarkerSet.getMarkers()` with no expiry/TTL. If the render-mask gate (Ticket B)
   just declines to call `addMarker` when a point is out of bounds, a marker created by a
   pre-this-feature version of the mod stays in the map's marker set forever — nothing ever tells
   BlueMap to remove it. So when the gate finds a point out of bounds, it must issue the equivalent
   `removeMarker`/remove-by-id call for that marker, not merely withhold the add. This covers both
   "never let an out-of-bounds marker appear" and "sweep a stale one left over from before this
   feature shipped" as the same code path.
2. **This is a decision within Ticket B's existing scope, not new plumbing.** Ticket B already
   restructures `markerSetsCache`/`getMarkerSets`/`applyToMarkerSets` to retain each `MarkerSet`'s
   real `BlueMapMap` id through to apply-time specifically so the mask can be checked per real map
   inside that loop. Converting "skip add" into "call remove" reuses that same loop and the same
   `DispatchedMarkerIdentifier`/marker id already computed for the add — no new ticket needed.
3. **LINE/SHAPE need no special-casing.** Per ticket 03 decision 4 (all-or-nothing per map: "is any
   member point inside this map's bounds?"), when that predicate is false for a map, the dispatched
   Set action for that map becomes a Remove-by-group-label instead, using the existing
   `RemoveLineMarkerAction`/`RemoveShapeMarkerAction` id scheme — same substitution as POI, just
   keyed by `(group, label)` instead of a single marker id.
4. **Why this sweeps automatically on reload:** `SignManager.reset()` (fired by `IResetHandler` on
   `/bluemap reload`) already walks the full sign cache with `isReload=true`, and
   `SignTransitionResolver.computeTransitionAction` already force-re-dispatches an Add/Set for every
   *unchanged* representation under `isReload=true` (POI: line 91-93; LINE/SHAPE same-group
   recompute: line 105) — this existing behavior exists for other config-reload cases (icon/offset
   edits) but incidentally means every sign already re-runs through the mask gate on every reload,
   config-changed or not. Decision 1 is what makes that re-run actually clean up a stale marker
   instead of silently no-op'ing.
5. **Caveat scoped to documentation, not engineering:** `BlueMapAPIConnector.onEnable`'s
   `fireReset()` only runs on a genuine BlueMap disable/enable cycle (`disabledSinceLastEnable`),
   *not* on the very first `onEnable()` a server ever sees after boot — a deliberate guard (see its
   comment) that avoids discarding startup-queued actions. So restarting the server after upgrading
   the mod jar does **not**, by itself, trigger the sweep; an admin must run `/bluemap reload` once
   (or wait for a natural BlueMap disable/enable). Decided to document this rather than change
   `onEnable`'s startup-vs-reload guard: for the specific case this ticket cares about (existing
   signs, mod upgraded), an explicit `/bluemap reload` after upgrading is an acceptable one-time ask,
   and touching the startup/reload distinction is out of scope for this feature. The eventual plan
   doc / changelog / README must state: "run `/bluemap reload` once after upgrading to sweep any
   markers that existed outside their map's render bounds."
