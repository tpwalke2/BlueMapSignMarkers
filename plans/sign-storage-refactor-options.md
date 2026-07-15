# In-memory sign storage: refactor options

## Context

`SignManager` (`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignManager.java:58`) holds the only
in-memory copy of known signs: a single flat `ConcurrentHashMap<SignEntryKey, SignEntry>` (`signCache`). Each
`SignEntry` (`core/signs/SignEntry.java`) carries `playerId` plus parsed front/back text
(`SignLinesParseResult`: prefix/label/detail, `core/signs/SignLinesParseResult.java`). BlueMap itself only ever
receives label/detail via dispatched `MarkerAction`s — it is not queried back for state.

This doc answers two questions raised during review: is the current layout a space/time problem, and does it make
sense to keep a full original copy of each sign in memory at all given markers already live in BlueMap.

## Performance analysis

**Time.** Hot-path operations (`addOrUpdateSign`, `removeByKey`, both driven by mixin injects on player edit / block
removal, and `BLOCK_ENTITY_LOAD`) are `ConcurrentHashMap` get/put/remove — O(1). `getAllSigns()`
(`SignManager.java:79-81`) copies the whole map into an `ArrayList` — O(n), but its only callers are `saveSigns`
(server stop) and `reloadSigns` (BlueMap reset, `SignManager.java:87-94`), both rare, whole-lifecycle events, not
per-sign operations. No time bottleneck exists at any realistic scale.

**Space.** Per sign: a `SignEntryKey` (3 ints + a `parentMap` String) stored twice — once as the map key, once again
inside `SignEntry.key()` — plus `playerId` and two `SignLinesParseResult` records (3 Strings each). Call it
~150-250 bytes of JVM overhead per entry plus string data. Even 10,000 signs (far beyond what a real server
accumulates — that's more player-placed signs than most maps have blocks-of-interest) is a few MB. Not a real
problem.

## Does the original copy need to exist?

Yes. BlueMap is a downstream projection of this cache, not a queryable store the mod can read back from. The cache
is the only source of truth for three things a marker-only view can't provide:

1. **Diffing.** `isTextDifferent` (`SignManager.java:182-184`) compares label/detail against the cached entry before
   dispatching an update, so a player re-saving identical text doesn't spam BlueMap's marker store or connected
   web clients with a no-op update.
2. **Reset replay.** `reset()` → `reloadSigns()` clears and rebuilds the whole cache when BlueMap itself resets
   (`SignManager.java:226-229`). There is no "ask BlueMap for current markers" path — the mod must be able to
   resend everything from its own state.
3. **Persistence.** `signs.json` (region-sharded per
   `plans/region-sharded-sign-persistence-plan.md`) is written from `SignManager.getAll()`. Nothing about BlueMap
   survives a server restart for this mod's purposes; this cache is what does.

Removing the original copy isn't on the table. The only real question is whether its *layout* is worth changing.

## Options

**1. Do nothing — keep the flat `ConcurrentHashMap<SignEntryKey, SignEntry>`**
- Pros: simplest, already O(1) on the hot path, proven correct, zero migration risk.
- Cons: `SignEntryKey` duplicated (map key + `SignEntry.key()` field); `SignEntryHelper.getPrefix`/`getLabel`/
  `getDetail` recompute from raw parse results on every call site instead of being cached once.

**2. Cache derived fields (prefix/label/detail) on the entry instead of recomputing via `SignEntryHelper` each call**
- Pros: cuts redundant string work in `addOrUpdateSign`, which currently calls `getPrefix`/`getLabel`/`getDetail`
  up to ~3 times per invocation across both the new and existing entry.
- Cons: real code churn for a CPU micro-optimization on a low-frequency hot path (player sign edits, not a
  tight loop); `signs.json`'s `SignEntryV2` schema still needs the raw front/back split for persistence regardless,
  so this only saves cycles, not memory or file format.

**3. Shard the in-memory map by dimension (`Map<parentMap, ConcurrentMap<xyz, SignEntry>>`), mirroring the
   region-sharded on-disk format from #109**
- Pros: conceptually matches the disk layout; would let reset/save scope to a single dimension if that ever became
  a requirement.
- Cons: adds a second map hop to every lookup for a capability nothing currently uses — `reset()` and save/load are
  whole-world operations today, not per-dimension. `plans/region-sharded-sign-persistence-plan.md` explicitly chose
  to leave the in-memory map as-is for this reason, deferring a region-indexed in-memory view to whichever future
  feature (chunk-reset reconciliation) actually consumes it. Speculative generality with no current caller.

**4. Bit-pack `x/y/z` into a primitive `long` key (per dimension) instead of a boxed `SignEntryKey` record**
- Pros: only matters at very large scale (100k+ signs) — shaves allocation/lookup overhead.
- Cons: loses `SignEntryKey`'s readable `equals`/`toString`; needs careful encoding for Minecraft's y-range
  (-64..320); meaningful complexity for a scale this mod will not hit in practice.

## Recommendation

Option 1. At realistic Minecraft server sign counts this isn't a storage problem — it's a working, correct,
O(1)-on-the-hot-path cache doing exactly the job the architecture needs (diff, replay, persist). Option 2 is worth
revisiting only if profiling ever shows `addOrUpdateSign` as hot; options 3 and 4 solve problems this project does
not have.
