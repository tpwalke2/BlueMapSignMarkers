# 07 — Config reload: fix orphaned markers on id-scheme change

**Spec:** `.scratch/line-markers/spec.md` §7 ("Config reload (`/bluemap reload`) — fixes a real bug the current
replay strategy has")

**Also tracked at:** `.scratch/codebase-review-followups/issues/10-reload-clear-and-replay-orphans-markers-on-id-scheme-change.md`
— that ticket is the canonical description of this bug (found while writing the line-markers spec, filed
separately since it's a pre-existing structural issue, not new to this feature). Do the work once; mark both
tickets' checklists done together, don't duplicate the fix.

**Blocked by:** 06 (`SignManager` transition table — this reuses it for the reload diff)

**What to build:**
`SignManager.reloadSigns()` today clears `signCache`/`chunkIndex` and replays every sign through `addOrUpdateSign`,
so a replayed add does a plain `Map.put` over whatever marker was already there by the same id. Safe today only
because a POI marker's id (`x_y_z`) is position-based and never changes between reloads. A line marker's id
(`"line:" + label`) is content-keyed — so a config change that flips a group's `type` between `POI` and `LINE`
(same signs) leaves the old id's marker behind in BlueMap's `MarkerSet` forever, since replay only ever adds under
the new id.

Fix: stop clearing `signCache`/`chunkIndex` for reload. Capture `oldPrefixGroupMap` before swapping in the new
config, then for each currently-cached `SignEntry`, compute its representation under the **old** map and the
**new** map (using the sign's already-parsed text — reload doesn't re-parse cached sign text against a changed
prefix/regex; pre-existing limitation, unaffected by this change), and run that pair through the same transition
table from ticket 06. No separate "bulk resync" path needed: since the cache isn't cleared,
`LineGroupResolver.members(...)` always scans the full, unchanged sign cache regardless of processing order — a
`POI`→`LINE` type flip sees every member's join-effect compute the complete final point list on its first
iteration (order-independent; later iterations re-dispatch the identical, idempotent `Set`). This also drops the
now-unnecessary `chunkIndex.clear()`/rebuild, since no sign keys change during a reload.

`reloadConfig()` becomes: swap config, then run the above per-sign old-vs-new-representation pass instead of
calling `reloadSigns()`.

**Status:** resolved

- [x] `reloadConfig()` no longer calls `signCache.clear()`/`chunkIndex.clear()`; diffs old-vs-new representation
      per cached sign instead
- [x] Existing reload behavior (icon/offset/distance/visibility changes, same id scheme) has no regression — still
      covered by `../../../agent-context/plans/marker-group-config-reload-plan.md`'s verification checklist
- [ ] Flipping a group's `type` between `POI`/`LINE` in config (same signs, unchanged text) + `/bluemap reload`
      leaves no orphaned marker in BlueMap's web UI, either direction
- [x] `./gradlew build` passes; manual verification via `runServer` (this path is game/API-coupled, no automated
      coverage) still pending
- [x] Close out `.scratch/codebase-review-followups/issues/10-...md`'s checklist alongside this one

## Comments

`reloadConfig()` now captures `oldPrefixGroupMap` before swapping in the new config, then — instead of clearing
`signCache`/`chunkIndex` and replaying every sign through `addOrUpdateSign` — iterates the (unchanged) cached signs
once, computing each one's `Representation` under the old map and under the new map and running that pair through
ticket 06's `computeTransitionAction`. `reloadSigns()` (the old clear-and-replay method) is deleted; `reset()` now
calls only `reloadConfig()`. `chunkIndex` is never touched since no sign keys change during a reload. `./gradlew
build` passes. Manual `runServer` verification of the `POI`↔`LINE` type-flip scenario (and the pre-existing
icon/offset/visibility-change scenarios) is still pending, same as tickets 05/06.
