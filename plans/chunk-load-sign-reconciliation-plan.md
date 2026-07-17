# Chunk-load sign reconciliation

## Context

Addresses GitHub issue #110, the follow-up `plans/region-sharded-sign-persistence-plan.md` and
`plans/sign-storage-refactor-options.md` both flagged but deferred: nothing today detects a sign that vanished while
its chunk was unloaded (external region-file deletion/regen, backup restore, manual NBT surgery). The removal path
that exists (`AbstractBlockInject` mixin on `BlockBehaviour.affectNeighborsAfterRemoval`) only fires for an in-game
block change on a loaded chunk — it can't see a sign that disappeared by any other means. `SignManager`'s cache
keeps that entry and its BlueMap marker forever.

`region-sharded-sign-persistence-plan.md` set up on-disk storage sharded by 512-block region specifically so this
feature would have something cheap to query, but explicitly left `SignManager`'s in-memory `ConcurrentMap<SignEntryKey,
SignEntry>` untouched, deferring "a region-indexed in-memory view" to whichever feature actually consumes it.
`sign-storage-refactor-options.md` rejected sharding that map speculatively for the same reason — no caller yet. This
plan is that caller.

Fabric API already exposes the event needed, no new dependency: `ServerChunkEvents.CHUNK_LOAD`
(`fabric-lifecycle-events-v1`, part of `fabric_api_version` already in `gradle.properties`) —
`Load.onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated)`, fired once a chunk is already loaded into
a `ServerLevel`.

**Performance constraint**: chunk load fires constantly — spawn-chunk keep-alive, players walking, world
pre-generation. The design below must not scan the full sign cache per event.

## Goal

On every chunk load, cheaply find which cached signs belong to that chunk, check each still has a live
`SignBlockEntity` at its position, and remove (via the existing `SignManager.remove`, unchanged) any that don't.

## Design

### `SignChunkKey` (new, plain Java, unit-testable)

`record SignChunkKey(String parentMap, int chunkX, int chunkZ)`, in `core.signs` (a runtime lookup concern, not
persistence — kept separate from `core.signs.persistence.SignRegionKey`, which is 512-block/32-chunk file-layout
math and stays untouched). `forEntryKey(SignEntryKey key)` computes `Math.floorDiv(x, 16)`/`Math.floorDiv(z, 16)` —
plain per-chunk coordinates, `floorDiv` for the same negative-coordinate reason `SignRegionKey` already documents.

### `SignChunkIndex` (new, plain Java, unit-testable)

Wraps `ConcurrentHashMap<SignChunkKey, Set<SignEntryKey>>` (values are `ConcurrentHashMap.newKeySet()`), in
`core.signs`:
- `add(SignEntryKey key)` — `computeIfAbsent(SignChunkKey.forEntryKey(key), ...).add(key)`.
- `remove(SignEntryKey key)` — drops `key` from its chunk's set; if the set is now empty, removes the chunk entry
  too, so emptied-out areas don't leak map entries over a long-running server.
- `keysInChunk(String parentMap, int chunkX, int chunkZ)` — returns a snapshot `List<SignEntryKey>` (empty list if
  none tracked), the query the chunk-load handler uses.
- `clear()`.

Fully independent of `SignManager`/BlueMap types — testable the same way as `SignRegionKeyTest`/
`SignRegionPartitionerTest`.

### `SignManager` changes

- New field: `private final SignChunkIndex chunkIndex = new SignChunkIndex();`.
- Every place `signCache.put(key, entry)` establishes a *new* key (the add branch in `addOrUpdateSign`) also calls
  `chunkIndex.add(key)`. `removeByKey` also calls `chunkIndex.remove(key)`. The update branch (existing entry, same
  key, text/prefix change) doesn't touch `chunkIndex` — position is immutable once a `SignEntry` exists.
- `reloadSigns()` calls `chunkIndex.clear()` alongside `signCache.clear()` (the replay loop repopulates both via the
  same add path).
- New method: `static List<SignEntryKey> getKeysInChunk(String parentMap, int chunkX, int chunkZ)` → delegates to
  `chunkIndex.keysInChunk(...)`. Pure data query, no BlueMap/game types — but `SignManager` itself stays outside unit
  test coverage regardless (its constructor still builds a `BlueMapAPIConnector`), consistent with its current status
  in `testing.md`.

### Mod-level chunk-load handler (game-coupled, `BlueMapSignMarkersMod`)

Register alongside the existing three hooks: `ServerChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);`.

```java
private void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
    var parentMap = SignHelper.getSignParentMap(level);
    var chunkPos = chunk.getPos();

    for (var key : SignManager.getKeysInChunk(parentMap, chunkPos.x(), chunkPos.z())) {
        if (!(chunk.getBlockEntity(new BlockPos(key.x(), key.y(), key.z())) instanceof SignBlockEntity)) {
            LOGGER.info("Removing stale sign marker at {} - no sign block found on chunk load "
                    + "(external deletion/regen?)", key);
            SignManager.remove(key);
        }
    }
}
```

`chunk.getBlockEntity(pos)` is used directly (not `level.getBlockEntity(pos)`) since the event already hands us the
loaded chunk — a direct lookup into its already-deserialized block-entity map, no re-resolution through the level's
chunk manager.

**Deliberately no special case for `generated == true`.** That flag also fires when a chunk's saved data is missing
and Minecraft regenerates it fresh — exactly the "region file deleted externally" scenario this feature targets.
Skipping reconciliation there would silently defeat the main use case. There's no performance reason to skip it
either: the `chunkIndex` lookup is a single hashmap `get` returning an empty list for the overwhelming majority of
chunk loads (no signs ever tracked there), so cost is the same whether or not the chunk is newly generated.

## Performance

- Cost per chunk load: one `ConcurrentHashMap.get` (via `keysInChunk`) — O(1), returns empty for nearly every chunk.
- Only chunks that do have tracked signs (a tiny fraction in practice) pay any further cost, bounded by the number of
  signs in that one 16x16 chunk (typically 0-2), each a single `getBlockEntity` call already backed by a hashmap the
  chunk itself maintains — no scanning, no I/O, no BlueMap API calls unless a removal is actually dispatched.
- No new threading: runs synchronously on the same thread the existing `BLOCK_ENTITY_LOAD` handler already runs on;
  `SignManager.remove`'s dispatch to BlueMap still goes through the existing async `ReactiveQueue`.

## Changes (files)

1. **New** `core/signs/SignChunkKey.java` — record + `forEntryKey`.
2. **New** `core/signs/SignChunkIndex.java` — the add/remove/keysInChunk/clear wrapper described above.
3. **`core/signs/SignManager.java`** — add `chunkIndex` field, wire it into the add/remove paths and
   `reloadSigns()`, add `getKeysInChunk` static method.
4. **`BlueMapSignMarkersMod.java`** — register `ServerChunkEvents.CHUNK_LOAD`, add `onChunkLoad` handler.
5. **New tests**:
   - `src/test/java/.../core/signs/SignChunkKeyTest.java` — `floorDiv` chunk assignment, negative coordinates,
     boundary blocks (15/16), mirroring `SignRegionKeyTest`'s pattern.
   - `src/test/java/.../core/signs/SignChunkIndexTest.java` — add/query round-trip, multiple signs in one chunk,
     signs in different chunks/dimensions stay isolated, `remove` drops the chunk entry once its set empties,
     `clear` resets everything.

No `SignFileVersions` bump, no persisted-schema change — this is runtime-cache-only. No `mod_version` bump either:
the last release hasn't shipped yet, so this folds into the same in-progress `26.2-0.17.0`.

## Out of scope

- A config toggle to disable reconciliation — not requested, and there's no existing feature-flag mechanism in this
  mod to extend.
- Reconciling on chunk *unload* — not requested.
- Any change to `SignRegionKey`/region-sharded on-disk storage — persistence layout is unrelated to this in-memory
  lookup index.

## Verification

- `./gradlew test` — new `SignChunkKeyTest`/`SignChunkIndexTest` pass alongside the existing suite.
- `./gradlew build` — full build still succeeds.
- Manual, via `./gradlew runServer` (this handler touches live Minecraft/Fabric types, no automated coverage
  possible per `testing.md`):
  1. Place a sign, confirm its marker appears in BlueMap. Restart the server, confirm the marker is unchanged and no
     spurious remove/re-add happens (watch the log for the new INFO line — it should **not** appear).
  2. Place a sign, stop the server, then remove the physical block without going through the mod (e.g. delete that
     chunk's region `.mca` file to force a regen, or edit the block out with an NBT editor). Restart, walk into that
     chunk, confirm: the INFO log line appears, and the stale marker disappears from BlueMap.
  3. Repeat with signs in two different dimensions (overworld + nether) at the same x/z to confirm dimension
     isolation — loading one dimension's chunk must not touch the other's sign at the same coordinates.
