# Config loading and sign persistence

Two independent versioned-JSON subsystems share the same pattern (detect version → migrate → back up old file →
save current format), but are separate code paths: marker-group **config** (`config/`) and sign **state**
(`core/signs/persistence/`). End-user config format is documented in repo-root `README.md`; this doc covers the
loading/migration mechanics, not the format itself.

## Marker-group config (`config/`)

File: `config/bluemapsignmarkers/BMSM-Core.json`. Path is fixed (not per-world) —
`ConfigProvider.getConfigPath()` = `Path.of("config", Constants.MOD_ID, "BMSM-Core.json")`.

- `ConfigManager.get()` returns a `volatile` singleton reference, lazily populated by calling `reload()` on first
  access if still `null` (not an eager static-field initializer — that would run the instant anything references
  the class, including a test merely loading `ConfigManagerTest`, and write a real config file as a side effect).
  **Config is hot-reloadable**: `ConfigManager.reload()` (`synchronized`, public) loads via `ConfigProvider.loadConfig()`
  and swaps the reference; `volatile` alone is enough for safe publication to other threads since the new
  `BMSMConfigV2` is fully built before the swap. Reload is wired to BlueMap's `/bluemap reload` via
  `SignManager.reloadConfig()` — see `core-pipeline.md` §3. A package-private `reload(Path)` overload (and matching
  package-private `ConfigProvider.loadConfig(Path)`/`saveConfig(BMSMConfigV2, Path)` overloads, which the public
  no-arg methods now delegate to with `getConfigPath()`) exist solely so tests can point loading/saving at a temp
  directory instead of the hardcoded `config/<mod-id>/BMSM-Core.json` path — no behavior change for real usage.
- `ConfigProvider.loadConfig()`:
  1. If the file doesn't exist: create `new BMSMConfigV2()` (which self-populates the default single `[poi]`
     group via its field initializer), save it, return it.
  2. If the raw file content contains the literal substring `"poiPrefix"` (a V1-only field), parse as `BMSMConfigV1`,
     migrate via `loadV1Config` (builds a single `MarkerGroup` from `poiPrefix`, defaults for everything else),
     back up the old file (`FileUtils.createBackup(path, ".v1.bak", "config file")`), save the migrated V2 config,
     return it.
  3. Otherwise parse as `LoadingBMSMConfigV2` (nullable-boxed fields — `config.persistence.LoadingMarkerGroupV2`)
     and convert each `LoadingMarkerGroupV2` to a runtime `MarkerGroup` via `convertToLoadedMarkerGroup`, which
     applies defaults per-field (`matchType` → `STARTS_WITH`, `type` → `POI`, `offsetX`/`offsetY` → `0`,
     `defaultHidden` → `false`, `minDistance` → `0.0`, `maxDistance` → `10000000.0`). This two-model split
     (`LoadingMarkerGroupV2` boxed/nullable vs. `MarkerGroup` primitive) exists so a partially-specified group in
     user JSON gets these explicit defaults rather than Gson silently zeroing missing primitive fields.
  4. Any exception during load (`Gson.fromJson` failure, I/O error) logs and returns `null`, and
     `ConfigManager.loadCoreConfig` falls back to `new BMSMConfigV2()` defaults — a broken config file never
     prevents server startup, it just silently reverts to a single default `[poi]` group.
- `saveConfig(config)` creates parent dirs if needed and writes pretty-printed Gson JSON.

## Sign persistence (`core/signs/persistence/`)

Storage root is **per-world, region-sharded**: `{server_root}/bluemapsignmarkers/{level}/{dimension_namespace}/
{dimension_path}/r.{regionX}.{regionZ}.json` — one JSON file per (dimension, 32x32-chunk region), e.g.
`{server_root}/bluemapsignmarkers/world/minecraft/overworld/r.0.0.json`. Design rationale, rejected alternatives
(SQLite, H2, LMDB/MapDB, store-by-player, in-memory-only chunk index), and migration considerations are in
`plans/region-sharded-sign-persistence-plan.md`. This replaced the prior single-file-per-world layout
(`config/bluemapsignmarkers/<name>/signs.json`) so a future reconciliation feature (detecting signs whose chunk was
externally deleted/regenerated — GitHub issue #109) can query "signs known in this region" cheaply instead of
scanning every cached sign; that reconciliation logic itself is not yet implemented.

`ServerPathProvider.getMarkerStorageRoot(server)` (implemented on `BlueMapSignMarkersMod`) resolves the root:
`levelDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()`, server root =
`levelDir.getParent()`, level name = `levelDir.getFileName()`. The `.normalize()` is required because
`LevelResource.ROOT`'s relative path is literally `"."`, which `Path.resolve()` doesn't collapse on its own —
skipping it shifts `getParent()`/`getFileName()` by one level and lands the storage root inside the world save
folder instead of beside it. This also fixed a pre-existing bug where the old path formula's extra `.getParent()`
resolved to the *run directory's* name, not the level name (`plans/codebase-review-2026-07-11.md` finding #1).
`BlueMapSignMarkersMod.getLegacyMarkerFilePath` intentionally keeps the old (buggy) formula unchanged — migration
must locate files at the path they were actually written to, not the corrected one.

Loaded on `SERVER_STARTING`, saved on `SERVER_STOPPING` (then `SignManager.stop()`).

Format envelope per region file is unchanged: `VersionedSignFile(SignFileVersions version, String data)` where
`data` is itself a JSON-encoded string of that region's entry array (double-encoded — the envelope is parsed first,
then `data` is parsed again as the entry array). `SignFileVersions` = `V1, V2, V3` (current = `V3`, written by
`RegionShardedSignEntryWriter` unconditionally) — sharding changed how many files exist and where, not the schema
of an individual file.

`SignRegionKey(dimension, regionX, regionZ)`: `forPosition(dimension, x, z)` computes region coordinates via
`Math.floorDiv(x, 512)`/`Math.floorDiv(z, 512)` (512 = 32 chunks x 16 blocks, matching Minecraft's own Anvil
`.mca` region size — `floorDiv`, not truncating division, so negative coordinates split correctly).
`relativeFilePath()` splits `dimension` (a string like `minecraft:overworld`, or the `WorldMap.UNKNOWN` sentinel
`"unknown"` with no colon) on the first `:` into namespace/path segments, appending `r.{regionX}.{regionZ}.json`.
It rejects (throws `IllegalArgumentException`) a blank/`.`/`..` namespace, or a resolved relative path that's
absolute, starts with `..`, or otherwise escapes the namespace directory after `.normalize()` — a defense against a
maliciously/accidentally crafted dimension id writing outside the storage root.
`SignRegionPartitioner.partition(List<SignEntry>)` groups entries into `Map<SignRegionKey, List<SignEntry>>` using
each entry's `key().parentMap()`/`x()`/`z()` — the shared grouping logic behind both save and migration.

`SignProvider.loadSigns(storageRoot, legacyPath)`:
1. `RegionShardedSignEntryLoader.hasSignData(storageRoot)` — true if the storage root exists and contains at
   least one region file. If true: `RegionShardedSignEntryLoader.loadSignEntries(...)` walks the directory tree
   and loads every region file through `VersionedFileSignEntryLoader` (same per-file deserialization as before
   sharding), flattening into one list.
2. If false (first boot after upgrading, or a fresh world): `LegacySignFileMigrator.migrate(legacyPath, storageRoot,
   groups, gson)` — one-shot:
   - If no file exists at `legacyPath`, returns an empty list (fresh install, nothing to migrate).
   - Otherwise reads it and runs the **exact same V1/V2/V3 chain as before sharding**, unchanged:
     `VersionedFileSignEntryLoader.loadSignEntries(...)`, falling back to `Version1SignEntryLoader.loadSignEntries(...)`
     if that returns `null`. Region-sharding is layered *after* this existing version normalization, not a
     replacement for it.
   - Writes the resulting entries via `RegionShardedSignEntryWriter.write(...)` (see below). Backs up the legacy
     file via `FileUtils.moveToBackup(legacyPath, ".migrated", ...)` — **renamed, not deleted** — only after
     confirming every region file expected from `SignRegionPartitioner.partition(entryList)` actually exists on
     disk (or the entry list was empty to begin with); if any expected region file is missing, the legacy file is
     left in place and an error is logged, so a partial/failed migration doesn't lose the only remaining copy of
     the data. A successful migration isn't re-attempted on future boots since step 1 will find the new storage
     root non-empty from then on.
3. Every resulting `SignEntry` (from either path) is fed through `SignManager.addOrUpdate(...)` — same as before
   sharding, loading signs at startup goes through the exact same decision logic as any other sign event (see
   `core-pipeline.md` §3). Each entry is wrapped in its own try/catch, so one malformed entry logs an error and is
   skipped rather than aborting the load of every other sign.

`RegionShardedSignEntryLoader.hasSignData(storageRoot)` treats an `IOException` while walking the directory as
"assume it has data" (fails safe toward *not* triggering legacy migration) rather than "assume empty" — a directory
listing failure shouldn't be misread as a fresh install and cause the legacy file to be migrated again.

`Version3Converter.convertToV3(SignEntryV2, MarkerGroup[])`: converts `SignEntryV2`/`SignLinesParseResultV2` (which
carried a `MarkerTypeV2` enum, not a raw prefix string) to current `SignEntry`/`SignLinesParseResult` by looking up
`Arrays.stream(markerGroups).filter(g -> g.type() == MarkerGroupType.POI).findFirst().orElseThrow()` and using
*that* group's `prefix()` — i.e. **the first configured POI-type group's prefix is assumed for every migrated V2
entry**, regardless of what `MarkerTypeV2` value was actually stored. This is safe historically because V2-era
configs only ever had one POI group, but means migration would misattribute prefix if a server has multiple POI
groups configured differently than at V2 time. Unaffected by region-sharding — this conversion still runs once,
during `LegacySignFileMigrator`'s reuse of the V1/V2/V3 chain, before entries are ever partitioned by region.

`SignProvider.saveSigns(storageRoot)`: gets all cached entries from `SignManager.getAll()`, delegates to
`RegionShardedSignEntryWriter.write(storageRoot, entries, gson)`, which partitions via `SignRegionPartitioner` and
writes each region's `VersionedSignFile(V3, ...)` (creating parent dirs as needed). Any region file already on disk
that isn't in this save's partition set is **quarantined, not deleted**: renamed in place with a `.stale` suffix,
since an empty region on this save could mean the signs were genuinely removed, or that the region failed to load
at startup (in which case deleting it would be data loss) — there's no way to tell which from here. Quarantining is
skipped entirely (leaving all pre-existing files, including genuinely stale ones, untouched) if *any* region file
failed to write during this save, so a partial-write failure can't be compounded by also discarding good data.

### Adding a new persisted field

Per `AGENTS.md`: bump `SignFileVersions`, add a new loader/converter — **never** change an existing version's shape
in place. Old region files (or a not-yet-migrated legacy `signs.json`) on live servers must keep loading through
the version they were written with.

---
*Last updated: 2026-07-21 | Verified against: 26.2-0.17.0 (72d4280)*

