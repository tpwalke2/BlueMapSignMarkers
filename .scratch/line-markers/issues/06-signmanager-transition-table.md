# 06 — `SignManager`: representation transition table (add/update/remove/join/leave)

**Spec:** `.scratch/line-markers/spec.md` §6 ("Sign-role transitions: a lookup table, not a class hierarchy")

**Blocked by:** 03 (`LineGroupResolver`), 05 (`BlueMapAPIConnector` line rendering + `GroupTransitionMarkerAction`)

**What to build:**
Every sign change reduces to `(oldRepresentation, newRepresentation)`, where a representation is `null` (no
group), `POI(group, label, detail)`, or `LINE(group, label)`. Deliberately a plain lookup table in `SignManager`,
not first-class `State`/`Transition` classes — that class-hierarchy shape is exactly what a prior abandoned attempt
(`feature/tpwalke2/7-lines`) did and is the branch that ended up non-compiling (see spec's Context section).

| Old ＼ New | NONE | POI | LINE |
|---|---|---|---|
| **NONE** | no-op | dispatch Add POI | recompute line group **including** this sign; dispatch Set if ≥2 members, else no-op |
| **POI** | dispatch Remove POI | same group+label & text unchanged: no-op; same group+label & text changed: dispatch Update; different group/label: leave-effect + join-effect | leave-effect (Remove POI) + join-effect (recompute including this sign; Set if ≥2, else no-op) |
| **LINE** | recompute line group **excluding** this sign; dispatch Set if ≥2 remain, Remove if it drops below 2 | leave-effect + join-effect (Add POI) | same group+label: recompute (Set); different group/label: leave-effect + join-effect |

"Leave-effect"/"join-effect" are each a `MarkerAction` or nothing — 0, 1, or 2 concrete dispatches per transition,
computed synchronously before dispatch, bundled into one `GroupTransitionMarkerAction` (ticket 04/05) so
`ReactiveQueue`'s lack of ordering guarantees can't transiently show a sign in two places.

`addOrUpdateSign` keeps its existing responsibilities (cache/`chunkIndex` maintenance, `WorldMap.UNKNOWN`
playerId-preservation on update) and calls this table as its dispatch step, replacing the current
`shouldAdd/Remove/UpdatePOIMarker` + prefix-change branch (`SignManager.java:142-253`).

Concurrency: synchronous recompute-then-dispatch, no new locking. Every dispatch carries the complete current
point list, so `ReactiveQueue`'s serial draining plus BlueMap's put-by-id upsert makes a stale in-flight dispatch
harmless even if overtaken by a fresher one.

**Out of scope here:** the `reloadConfig()`/`/bluemap reload` rewrite that reuses this table — that's ticket 07.

**Status:** resolved

- [x] Transition table implemented, replacing `shouldAdd/Remove/UpdatePOIMarker` + prefix-change branch
- [x] `addOrUpdateSign` dispatches via the table; existing cache/`chunkIndex`/`WorldMap.UNKNOWN` behavior unchanged
- [x] `./gradlew build` passes (this class stays untestable by unit test — no Minecraft/BlueMap types allowed in
      signature per `AGENTS.md`, but `SignManager` itself has them; verified manually)
- [ ] Manual, via `runServer`: place 3 signs with the same line-group prefix+label at different times — no marker
      until the 2nd, line grows to include the 3rd; break the middle sign — line reconnects the remaining two;
      break down to 1 remaining sign — marker disappears; edit one sign's prefix from a `LINE` group to `POI` and
      back — no duplicate/orphaned markers in either direction

## Comments

Replaced `shouldAdd/Remove/UpdatePOIMarker` + the prefix-change branch with a representation-based transition
table: a private `Representation(MarkerGroup, label, detail)` record (null = NONE) computed from a sign's parsed
text against the current prefix→group map, and `computeTransitionAction(key, oldRep, newRep, actionFactory)`
covering all nine `(NONE/POI/LINE) x (NONE/POI/LINE)` cells. `lineJoinAction`/`lineLeaveAction` share the
"recompute via `LineGroupResolver.members` against the post-mutation `signCache`, dispatch Set if ≥2 members,
else Remove/no-op" logic for both directions. `addOrUpdateSign` mutates `signCache`/`chunkIndex` first (so
membership recompute sees the post-change state), then dispatches the single resulting action (a plain
`MarkerAction`, or a `GroupTransitionMarkerAction` when both a leave- and join-effect apply). `removeByKey` reuses
the same table as the `(oldRep, null)` cell. `./gradlew build` passes; manual smoke test still pending (needs
`runServer`).
