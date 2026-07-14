# Region-sharded sign persistence

## Context

Addresses GitHub issue #109.

Signs load fully into memory from one file per world:
`config/bluemapsignmarkers/<world-save-name>/signs.json`, built by
`BlueMapSignMarkersMod.getMarkerFilePath` (`src/main/java/com/tpwalke2/bluemapsignmarkers/BlueMapSignMarkersMod.java:34-37`):

```java
private String getMarkerFilePath(MinecraftServer server) {
    var worldSaveName = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().getParent().getFileName();
    return String.format("config/%s/%s/signs.json", Constants.MOD_ID, worldSaveName);
}
```

Chunk deletion/regen (external tools, pregen resets, manual region-file deletion) fires no "sign removed"
event — `BLOCK_ENTITY_LOAD` only adds/updates signs that are actually present when a chunk loads, so a chunk wiped
between server runs leaves its old sign entries in the cache and their markers on the map forever. Fixing this
correctly means, on chunk load, asking "which signs do we know about in this chunk?" — cheap only if signs are
indexed by location. Against today's single flat file (and the flat `ConcurrentMap<SignEntryKey, SignEntry>` it's
loaded into) that question costs a full scan per chunk load, which doesn't scale on busy servers. This plan is
groundwork: reorganize on-disk storage by location so the actual reconciliation logic (separate follow-up issue) has
something cheap to query. It does not implement that reconciliation logic itself.

This also folds in an adjacent fix already on record: `plans/codebase-review-2026-07-11.md` (finding #1) flags that
`getWorldPath(LevelResource.ROOT)` already resolves to the level-save directory, so the extra `.getParent()` walks
up to the server root and `.getFileName()` grabs the *run directory's* name, not the level name. Rewriting path
construction to relocate storage out of `config/` is the natural place to fix this too.

## Goal

Move signs off one global-scan file onto one JSON file per (dimension, 32×32-chunk region), stored at:

```
{server_root}/bluemapsignmarkers/{level}/{dimension_namespace}/{dimension_path}/r.{regionX}.{regionZ}.json
```

e.g. `{server_root}/bluemapsignmarkers/world/minecraft/overworld/r.0.0.json`. Existing single-file installs migrate
automatically on first boot after the update, with the legacy file backed up rather than deleted.

Out of scope: the chunk-load reconciliation logic itself (follow-up issue), and any change to `SignManager`'s
in-memory `ConcurrentMap<SignEntryKey, SignEntry>` — it stays as-is for point lookups by exact position; a
region-indexed in-memory view is added alongside the reconciliation feature that will actually consume it, not
speculatively here. Incremental/eager per-region writes for crash resilience are also out of scope — signs still
save as a full snapshot at `SERVER_STOPPING`, same as today; a crash between clean shutdowns loses unsaved edits
regardless of file layout, a pre-existing limitation this plan doesn't change.

## Options considered, not chosen

- **SQLite** — real indexed `WHERE dimension = ? AND regionX = ? AND regionZ = ?` queries, ACID. Rejected:
  `sqlite-jdbc` bundles native binaries per OS/arch, a real risk for a mod meant to run on arbitrary player-hosted
  servers (unusual JVMs, ARM hosts); also swaps the existing JSON-version-converter migration model for a SQL
  schema-migration model, a different maintenance pattern from the rest of the persistence code; loses the
  "open signs.json in a text editor" debuggability the project currently has.
- **H2 (pure-Java embedded DB)** — same query benefits as SQLite without the native-library risk. Rejected: still a
  binary file format, still a new migration model, and a heavier dependency than the problem needs given
  region-sharded JSON solves the same access pattern with no new dependency at all.
- **LMDB / MapDB (embedded KV store, spatially-sortable keys)** — memory-mapped, fast range scans. Rejected: LMDB
  still needs native bindings (same risk as SQLite); MapDB is a more novel/less battle-tested dependency than this
  problem justifies.
- **Store by player** — doesn't fit the actual problem. A chunk reset can contain signs owned by many players, so a
  player-keyed index doesn't shrink the reconciliation query at all; it's a different access axis, useful for a
  future "list my signs" command, not for this issue.
- **Chunk-based in-memory index only, no on-disk change** — cheapest possible fix (add a
  `Map<ChunkPos, Set<SignEntryKey>>` alongside the existing cache, built at load and maintained incrementally).
  Solves the query-cost half of the problem but not the "everything loads into memory at startup" half, and doesn't
  address the storage-location/config-folder ask in this issue. Worth keeping in mind as the shape of the future
  in-memory index, but insufficient alone as the on-disk redesign this issue calls for.

## Chosen approach: region-sharded JSON

Why: region boundaries (32×32 chunks, matching Minecraft's own `.mca` Anvil region-file granularity) are exactly
the granularity external tools reset or delete at (Chunky, WorldBorder, MCA Selector, manual region-file deletion),
so "this region's file is gone/changed" maps directly onto "these signs may be stale" — the natural unit for the
eventual reconciliation feature. It keeps the existing Gson + `VersionedSignFile`/`SignFileVersions` machinery
completely unchanged in shape: an individual region file is exactly today's versioned envelope
(`{version: V3, data: "[...]"}`) around the `SignEntry` array for that region — only the *aggregation into many
files*, not the JSON schema, changes. No new dependency, no native-library risk, still human-readable/greppable.

**Region coordinate math**: `regionX = Math.floorDiv(x, 512)`, `regionZ = Math.floorDiv(z, 512)` (512 = 32 chunks ×
16 blocks/chunk). Must use `floorDiv`, not integer division — world coordinates go negative and truncating division
would put e.g. `x = -1` in the same region as `x = 0`.

**Dimension folder**: `SignEntryKey.parentMap` is already a string of the form `namespace:path` (or the
`WorldMap.UNKNOWN` sentinel `"unknown"`, no colon) — split on the first `:` to get two path segments
(`namespace`, `path`); no further sanitizing needed since Minecraft identifier characters are already
filesystem-safe on Windows/Linux/macOS. `unknown` (no colon) becomes a single folder segment with no sub-path.

## Changes

1. **`src/main/java/com/tpwalke2/bluemapsignmarkers/ServerPathProvider.java`** — currently a dead, unimplemented
   interface (`Path getConfigFolder()`, no implementers, no callers). Repurpose it to own the new base-directory
   resolution instead of adding a parallel abstraction:
   ```java
   Path getMarkerStorageRoot(MinecraftServer server); // {server_root}/bluemapsignmarkers/{level}
   ```
   Implementation: `levelDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()`; server root =
   `levelDir.getParent()`; level name = `levelDir.getFileName()`; result =
   `serverRoot.resolve(Constants.MOD_ID).resolve(levelName)`. The `.normalize()` is load-bearing, not cosmetic:
   `LevelResource.ROOT`'s relative path is literally `"."`, and `Path.resolve()` never collapses `.` segments on its
   own — without normalizing first, `levelDir` keeps an unresolved trailing `.`, which shifts `getParent()`/
   `getFileName()` by one level (`serverRoot` resolves to the level directory itself instead of the true server
   root, and `levelName` comes back as `"."` instead of the level name), landing the whole storage root one level
   too deep, inside the world save folder. Confirm the `LevelResource.ROOT` assumption once via a log line in
   `runServer` before relying on it, since the whole path change hinges on it.
2. **New pure-Java class, `core/signs/persistence/SignRegionKey.java`** (record: `String dimension, int regionX,
   int regionZ`) plus a small partitioning helper (e.g. a static method on `SignProvider` or a new
   `SignRegionPartitioner`) implementing the region-coordinate math and dimension split above, grouping a
   `List<SignEntry>` into `Map<SignRegionKey, List<SignEntry>>`. Pure logic, no Minecraft types — unit-testable
   per the existing `SignLinesParser`/`SignEntryHelper` convention in `AGENTS.md`.
3. **New loader, `core/signs/persistence/loaders/RegionShardedSignEntryLoader.java`** — given the storage root,
   walks the `{namespace}/{path}/r.{x}.{z}.json` tree, reads each region file with the same Gson/
   `VersionedFileSignEntryLoader` deserialization `SignProvider` already uses per-file today, flattens into one
   `SignEntry` list.
4. **`core/signs/persistence/SignProvider.java`** — `loadSigns`/`saveSigns` take the storage root (a `Path`, from
   change #1) instead of a single file path string:
   - `loadSigns`: if the storage root exists and is non-empty, load via #3. Otherwise run migration (#5).
   - `saveSigns`: partition `SignManager.getAll()` via #2, write each partition as a `VersionedSignFile{V3, ...}`
     to its region path (creating parent dirs as needed) — same per-file write logic as today's single file, just
     once per region. Then delete any region file already on disk under the tree that isn't in this save's
     partition set (a region that's now empty), so stale empty-shard files don't accumulate.
5. **Migration** (see dedicated section below) — one-time, triggered when the new storage root doesn't exist yet.
6. **New tests**, `src/test/java/com/tpwalke2/bluemapsignmarkers/core/signs/persistence/`:
   - Region-key math: `floorDiv`-based region assignment is correct for negative coordinates and region boundaries
     (e.g. `x = -1` vs `x = 0` vs `x = 511` vs `x = 512` land in the expected/different regions).
   - Dimension-string splitting, including the no-colon `WorldMap.UNKNOWN` case.
   - Round-trip: write a set of `SignEntry`s spanning multiple regions and dimensions to a `@TempDir`, reload via
     #3, assert the same entries come back.
   - Migration: a legacy single-file fixture (V1, V2, and V3 shapes) in the old buggy-path layout migrates to the
     correct new-layout files, and the legacy file is renamed/backed up rather than deleted.
7. **`gradle.properties`** — minor `mod_version` bump (`26.2-0.16.1` → `26.2-0.17.0`): default storage location and
   on-disk layout change for every install (auto-migrated, but a real behavior/location change, not just a bug fix
   — e.g. any external backup tooling pointed at `config/bluemapsignmarkers/` needs to be repointed).

## Migration considerations

- **Must locate the legacy file using the current (buggy) path formula**, not the fixed one — `getMarkerFilePath`'s
  existing `.getParent().getFileName()` logic is what's actually on disk on live servers today, so migration has to
  replicate that exact (wrong) formula to find the old file, not the newly-fixed level-name resolution.
- **Reuse the existing V1→V2→V3 converter chain unchanged** as migration's first stage — load the legacy file
  through `VersionedFileSignEntryLoader`/`Version1SignEntryLoader` exactly as `SignProvider.loadSigns` does today,
  *then* region-partition the resulting flat `SignEntry` list. Region-sharding is a step layered after existing
  version normalization, not a replacement for it.
- **Back up, don't delete**, the legacy file — rename/copy it aside (reuse `FileUtils.createBackup`'s pattern,
  e.g. a `.migrated` suffix) once the new region files are written successfully, consistent with the existing
  `.v1.bak`/`.v2.bak` convention.
- **One-shot**: presence of the new storage root (change #1) short-circuits migration on every subsequent boot —
  no re-migration, no double-processing.
- **Log the new location** clearly on migration, since storage moved out of `config/` entirely — anyone with
  backup scripts or manual habits pointed at the old path needs to notice.
- **Edge cases**: `WorldMap.UNKNOWN` dimension entries (no colon in `parentMap`) need graceful single-segment
  folder handling; a region that had signs in the legacy file but ends up empty after migration should simply not
  get a file written, not an empty stub.

## Verification

- `./gradlew test` — new persistence/migration/region-math tests pass alongside existing `SignLinesParserTest`/
  `HtmlUtilsTest`.
- `./gradlew build` — full build, including jar packaging, still succeeds.
- Manual, via `./gradlew runServer`: place signs across multiple regions and at least two dimensions
  (overworld + nether), restart the server, confirm markers still appear correctly in BlueMap after reload.
- Manual migration check: start from a dev `run/` directory containing a pre-existing
  `config/bluemapsignmarkers/<name>/signs.json` fixture (V1, V2, and V3 shaped, in separate test runs), boot the
  server, confirm the new `bluemapsignmarkers/{level}/...` region files appear with correct contents and the
  legacy file is backed up in place rather than removed.
