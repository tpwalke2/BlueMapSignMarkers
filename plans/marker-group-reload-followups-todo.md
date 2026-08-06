# Marker group reload — follow-up todo list

Deferred edge cases from `plans/marker-group-config-reload-plan.md`, not tracked anywhere else (checked
`plans/codebase-review-2026-07-11.md` and the rest of `plans/` — no match). Neither blocks that plan's core
goal (icon/offset/visibility/distance edits reflected on `/bluemap reload`); both need `SignManager` to track,
per cached sign, the last-dispatched `MarkerGroup` value (not just its prefix string) to fix properly.

## 1. Group rename leaves a duplicate marker in the old-named `MarkerSet`

Renaming a group's `name` field (BlueMap's `MarkerSet`/layer label) while its `prefix` stays the same: reload
resolves the new `MarkerGroup` value (different `name`), so `BlueMapAPIConnector.getMarkerSets` looks it up by the
*new* name, misses, and creates/reuses a `MarkerSet` under that new name — but nothing ever removes the marker from
the *old*-named set, because `SignManager` only remembers a cached sign's prefix string, not the full `MarkerGroup`
(specifically its `name`) it was last dispatched under. Result: the marker shows up under both set names.

No operator-facing remediation exists today: BlueMap's web UI has no marker/marker-set edit or delete capability,
and it's unconfirmed whether a plain server restart would even clear it (BlueMap persists marker-set state itself,
independent of this mod's in-memory cache) — removing the stale entry likely requires a mod code fix, not a
workaround.

**Fix sketch for a future plan:** give `SignManager` a way to know the previous `MarkerGroup` (not just prefix) each
cached sign was last dispatched under — e.g. a side map `Map<SignEntryKey, MarkerGroup>` updated on every dispatch,
or store the resolved `MarkerGroup` alongside `SignEntry` in `signCache`. In `reloadConfig()`/`reloadSigns()`,
compare old vs. newly-resolved group per entry; if `name()` differs, dispatch an explicit `RemoveMarkerAction`
against the *old* `MarkerSetIdentifier` (mirroring `addOrUpdateSign`'s existing prefix-changed remove-then-add
branch) before the replay's `AddMarkerAction` under the new one. Note this only empties the old `MarkerSet`'s marker
list — it doesn't remove the (now-empty) `MarkerSet` itself from `blueMapMap.getMarkerSets()`, which would still
show up as an empty, togglable layer in BlueMap's UI unless explicitly deleted once no sign references it anymore.

## 2. Prefix rename without an in-game sign edit doesn't reclassify the sign

`SignEntry` stores only the *parsed* result (`SignLinesParseResult`: prefix/label/detail) — never the raw sign
text — so re-parsing against an updated config isn't possible from the cache alone. The existing remove-then-add
path for a prefix change (`SignManager.addOrUpdateSign`'s prefix-changed branch) only ever runs when a player
re-edits that specific sign in-game (which re-parses live text via `SignHelper.createSignEntry`) or a chunk reloads
it (`BLOCK_ENTITY_LOAD`, same re-parse). Reload's clear-and-replay never re-parses — it just re-dispatches each
cached entry's already-resolved prefix — so renaming a group's `prefix` in `BMSM-Core.json`, with no matching
in-game re-edit or chunk reload of that sign, leaves the marker classified under the old prefix's group semantics
indefinitely (or, if the old prefix was removed rather than renamed, silently orphaned per
`marker-group-config-reload-plan.md`'s `isMarkerType` null-guard behavior).

**Fix sketch for a future plan:** either (a) store the raw front/back sign text alongside the parsed result in
`SignEntry` (and in persisted `signs.json` — a `SignFileVersions` bump) so `reloadSigns()` can re-run
`SignLinesParser` against current config for every cached entry before re-dispatching, or (b) on reload, re-read
each cached sign's live `SignBlockEntity` text directly from its `ServerLevel` (heavier: requires the chunk to be
loaded, and a lookup from `SignEntryKey` back to a world/`BlockPos`, not something `SignManager` has today).
