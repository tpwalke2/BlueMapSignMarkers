# 10 — `reloadSigns()`'s clear-and-replay leaves orphaned markers when a marker's id scheme changes between reloads

**What to build:** `SignManager.reloadSigns()` (`core/signs/SignManager.java:109-117`, called from `reloadConfig()`)
clears `signCache`/`chunkIndex` then replays every cached sign through `addOrUpdateSign`, so every entry always
takes the "add" branch (`existing` is always `null` post-clear) and a replayed add does a plain `Map.put` over
whatever marker was already there **by the same id**. This is only safe today because a POI marker's id
(`MarkerIdentifier.getId()` → `x_y_z`) is purely position-based and never changes between reloads — `put` always
overwrites the correct entry.

That assumption breaks the moment any marker's id can be **content**-keyed instead of position-keyed for the same
underlying sign(s). Concretely: if a config change causes a sign's marker to be rendered under a different id on
reload — e.g. a group's `type` flipping between two marker kinds that key their markers differently — replay only
ever adds under the *new* id; nothing ever explicitly removes the *old* id's entry. The old marker is left behind
in BlueMap's `MarkerSet` map (which BlueMap itself owns and persists across reloads) until someone removes it
manually via BlueMap's web UI. `BlueMapAPIConnector.clearMarkerSetsCache()` (already called from `reloadConfig()`)
doesn't help — it only evicts this mod's own `MarkerSetIdentifier → MarkerSet` *lookup* cache, not the marker
entries inside a `MarkerSet` BlueMap already returned and populated.

Not yet user-visible: today every marker this mod creates is a POI marker with a position-based id, so no code path
can actually trigger this. It becomes live the moment a second marker id scheme is introduced —
`plans/line-markers-plan.md` (§7 "Config reload") hits this directly, since a line marker's id is
`"line:" + label` (content-keyed, not position-keyed), and a marker group's `type` flipping between `POI` and
`LINE` in config (existing signs, unchanged sign text) is exactly the scenario that orphans a marker under the plan
as originally scoped.

**Fix**, per the line-markers plan's §7: stop clearing `signCache`/`chunkIndex` for reload. Capture the prefix→group
map as it was *before* swapping in the new config, then for each currently-cached `SignEntry`, compute its
representation under the **old** map and under the **new** map, and run that pair through the same
add/update/remove/leave-join decision logic used for live sign edits — so a reload can dispatch an explicit removal
of whatever the *old* representation's marker was, not just an add of the new one. This also drops the now-
unnecessary `chunkIndex.clear()`/rebuild, since no sign keys actually change during a reload.

**Blocked by:** None. The fix itself doesn't require `LINE` markers to exist — it can be built today using only
`POI` representations (`null` or `POI(group, label, detail)`): `reloadConfig()` dispatches an explicit remove-old +
add-new only when a sign's representation actually changes between reloads, instead of blindly clearing and
replaying. What *does* require a second id scheme (i.e. `plans/line-markers-plan.md` landing) is **verifying** the
fix — there's no way to trigger an id-scheme change today, since every marker this mod creates is POI,
position-keyed, always, so there's nothing to repro against yet. Landing this standalone now would be defensively
correct but untestable until then; landing it alongside the line-markers work makes it verifiable immediately, since
that work needs the same `SignManager` representation refactor for its own transition table anyway. Either order
works — this ticket doesn't gate the plan, and the plan doesn't gate this ticket.

**Status:** open

- [ ] `reloadConfig()` no longer calls `signCache.clear()`/`chunkIndex.clear()`; it diffs old-vs-new representation per cached sign instead
- [ ] Existing reload behavior (icon/offset/distance/visibility changes, same id scheme) has no regression — still covered by the scenarios in `plans/marker-group-config-reload-plan.md`'s verification checklist
- [ ] Once a second id scheme exists (line markers, or any future addition), a config change that alters a sign's marker id scheme between reloads leaves no orphaned marker in BlueMap's web UI
- [ ] A regression test or manual verification step confirms the fix — likely manual only for the reload path itself (`SignManager`/`BlueMapAPIConnector` are game/API-coupled with no automated coverage per `AGENTS.md`), though the old-vs-new representation diffing logic itself may be extractable into something unit-testable

## Comments

Found while writing `plans/line-markers-plan.md` — tracing the reload path in detail to work out how a marker
group's `type` flip (`POI`↔`LINE`) should behave on `/bluemap reload` surfaced this as a pre-existing structural
fragility in `reloadSigns()`, not something new to the line-markers feature itself. Filing separately from the plan
so it's visible/trackable and fixable on its own schedule, rather than implying it can only be addressed as part of
that larger feature.
