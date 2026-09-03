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
  2. Parses the raw content once as a `JsonObject` and detects V1 **structurally**: `root.has("poiPrefix") &&
     !root.has("markerGroups")` — a bare V1 config never has a `markerGroups` field. This replaced a substring
     search for the literal text `"poiPrefix"` anywhere in the file (review finding #9, resolved as part of
     ticket 01), which used to misdetect a well-formed V2 config as V1 — and silently collapse its real marker
     groups to a single default — whenever a group's `name`/`icon` happened to contain that substring. On a V1
     match: parse as `BMSMConfigV1`, migrate via `loadV1Config` (builds a single `MarkerGroup` from `poiPrefix`,
     defaults for everything else), back up the old file (`FileUtils.createBackup(path, ".v1.bak", "config file")`
     — **aborts the migration** by throwing `IllegalStateException` instead of overwriting the original if the
     backup fails, ticket 02), save the migrated V2 config, return it.
  3. Otherwise parse as `LoadingBMSMConfigV2` (nullable-boxed fields — `config.persistence.LoadingMarkerGroupV2`,
     whose default single-`[poi]`-group literal is derived from `MarkerGroup.DEFAULT_POI_GROUP` — see
     `core-pipeline.md` §3 — rather than duplicated separately) and convert each `LoadingMarkerGroupV2` to a
     runtime `MarkerGroup` via `convertToLoadedMarkerGroup`, which applies defaults per-field (`matchType` →
     `STARTS_WITH`, `type` → `POI`, `offsetX`/`offsetY` → `0`, `defaultHidden` → `false`, `minDistance` → `0.0`,
     `maxDistance` → `10000000.0`, `lineWidth` → `2`, `lineColor` → `"#FF0000FF"`, `fillColor` → `"#FF000033"`,
     `sorting` → `0`, `toggleable` → `true`, `depthTest` → `true`, `cssClasses` → `List.of()` — the `lineWidth`/
     `lineColor`/`fillColor` trio are the `LINE`/`SHAPE`/`EXTRUDE`-marker additions, mirroring BlueMap's own
     `LineMarker`/`ShapeMarker`/`ExtrudeMarker` defaults; `fillColor`'s default is translucent, unlike `lineColor`'s
     opaque one; `EXTRUDE` reuses `SHAPE`'s exact defaults/validation for every one of these fields, see below).
     `lineWidth`/`lineColor`/`fillColor`/`sorting`/`cssClasses` each go through their own validating resolver
     (`resolveLineWidth`/`resolveLineColor`/`resolveFillColor`/`resolveSorting`/`resolveCssClasses`) rather than a
     plain null-check default: a non-positive `lineWidth` (`<= 0`) or a `lineColor`/`fillColor` that fails
     `ColorUtils.isValidHex` (accepts `#RRGGBB`/`#RRGGBBAA`, leading `#` optional — the same shape
     `ColorUtils.parseHex` accepts) logs a warning naming the group and falls back to the default, instead of the
     malformed value reaching `ActionFactory`/BlueMap's `LineMarker`/`ShapeMarker`/`ExtrudeMarker` unnoticed
     (silently caught later only at dispatch time by `ColorUtils.parseHex`'s own fallback). `resolveLineWidth`/
     `resolveLineColor` validate for `LINE`, `SHAPE`, and `EXTRUDE` groups; for any other type (`POI`) they return
     the raw configured value unvalidated (pass-through, since the field is ignored for that type anyway).
     `resolveFillColor` only validates when the group's effective `type` is `SHAPE` or `EXTRUDE`; for any other type
     it returns the raw configured value unvalidated the same way (`fillColor` isn't applied for non-`SHAPE`/
     `EXTRUDE` groups anyway). `sorting` is read off
     `LoadingMarkerGroupV2` as a raw `JsonElement` rather than `Integer` specifically so a non-integer JSON value
     (a string, an array, an out-of-`int`-range number) falls back to the default with a warning in
     `resolveSorting` instead of failing Gson's parse for the *entire* config; `resolveCssClasses` drops any `null`
     entries in the list (warning once) rather than propagating them to BlueMap's marker builder. `resolveToggleable` is plain null-coalescing (`toggleable == null || toggleable`), a simple boolean with no
     malformed-value case to guard against. `resolveDepthTest` is not: an unset value defaults to `true`, but a
     configured value is only honored for `LINE`/`SHAPE`/`EXTRUDE` groups — for any other type (`POI`) it forces
     `true` regardless of what was configured, since depth-testing has no effect on a POI marker. This two-model
     split (`LoadingMarkerGroupV2`
     boxed/nullable vs. `MarkerGroup` primitive) exists so a partially-specified group in user JSON gets these
     explicit defaults rather than Gson silently zeroing missing primitive fields. `validateMarkerGroups` then fails
     fast (`IllegalArgumentException`, caught by the catch-all below) on an empty prefix, a `REGEX` prefix that
     doesn't compile, or a prefix duplicated across groups — ticket 01 — rather than surfacing as a skip/NPE later,
     downstream in `SignLinesParser`/`SignManager`. A separate, non-fatal pass, `warnOnTypeFieldMismatches`, logs a
     warning (never throws) against the raw `LoadingMarkerGroupV2` (which still distinguishes "field omitted" via
     `null`) for a field set on a type it doesn't apply to: `POI` warns on an explicit `lineWidth`/`lineColor`/
     `fillColor`/`depthTest`; `LINE` warns on an explicit `icon`/`offsetX`/`offsetY`/`fillColor`/`cssClasses`;
     `SHAPE` and `EXTRUDE` each warn on an explicit `icon`/`offsetX`/`offsetY`/`cssClasses` (but *not*
     `lineWidth`/`lineColor`, which both also use for their border) — those fields are silently ignored for the
     group's actual type, so this just flags a likely config mistake rather than rejecting it. `sorting`/`toggleable`
     apply to every group type (thin `MarkerSet` passthroughs — see `core-pipeline.md` §6) so neither has a
     type-mismatch warning.
  4. Any exception during load (`Gson.fromJson` failure, I/O error, or a `validateMarkerGroups` failure) logs and
     returns `null`, and `ConfigManager.loadCoreConfig` falls back to `new BMSMConfigV2()` defaults — a broken
     config file never prevents server startup, it just silently reverts to a single default `[poi]` group.
- Config file reads/writes both go through `StandardCharsets.UTF_8` explicitly (`Files.readString`/
  `OutputStreamWriter`, ticket 01) rather than the JVM's platform-default charset, so a non-ASCII marker-group
  name survives a restart regardless of the host's default encoding.
- `saveConfig(config)` creates parent dirs if needed and writes pretty-printed Gson JSON.

## Sign persistence (`core/signs/persistence/`)

Storage root is **per-world, region-sharded**: `{server_root}/bluemapsignmarkers/{level}/{dimension_namespace}/
{dimension_path}/r.{regionX}.{regionZ}.json` — one JSON file per (dimension, 32x32-chunk region), e.g.
`{server_root}/bluemapsignmarkers/world/minecraft/overworld/r.0.0.json`. Design rationale, rejected alternatives
(SQLite, H2, LMDB/MapDB, store-by-player, in-memory-only chunk index), and migration considerations are in
`../plans/region-sharded-sign-persistence-plan.md`. This replaced the prior single-file-per-world layout
(`config/bluemapsignmarkers/<name>/signs.json`) so a future reconciliation feature (detecting signs whose chunk was
externally deleted/regenerated — GitHub issue #109) can query "signs known in this region" cheaply instead of
scanning every cached sign; that reconciliation logic itself is not yet implemented.

`ServerPathProvider.getMarkerStorageRoot(server)` (implemented on `BlueMapSignMarkersMod`) resolves the root:
`levelDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()`, server root =
`levelDir.getParent()`, level name = `levelDir.getFileName()`. The `.normalize()` is required because
`LevelResource.ROOT`'s relative path is literally `"."`, which `Path.resolve()` doesn't collapse on its own —
skipping it shifts `getParent()`/`getFileName()` by one level and lands the storage root inside the world save
folder instead of beside it. This also fixed a pre-existing bug where the old path formula's extra `.getParent()`
resolved to the *run directory's* name, not the level name (`../plans/codebase-review-2026-07-11.md` finding #1).
`BlueMapSignMarkersMod.getLegacyMarkerFilePath` intentionally keeps the old (buggy) formula unchanged — migration
must locate files at the path they were actually written to, not the corrected one.

Loaded on `SERVER_STARTING`, saved on `SERVER_STOPPING` (then `SignManager.stop()`).

Format envelope per region file is unchanged: `VersionedSignFile(SignFileVersions version, String data)` where
`data` is itself a JSON-encoded string of that region's entry array (double-encoded — the envelope is parsed first,
then `data` is parsed again as the entry array). `SignFileVersions` = `V1, V2, V3, V4, V5` (current = `V5`, written
by `RegionShardedSignEntryWriter` unconditionally) — sharding changed how many files exist and where, not the
schema of an individual file. `V4` is the line-markers addition: `SignEntry` gained `long createdAtMillis`, set
once when a sign is first observed by `SignManager` and never recomputed afterward, needed to order a line/shape
marker's points in placement order (`LineGroupResolver`/`ShapeGroupResolver`, `core-pipeline.md` §3).

`V5` is the stale-prefix self-heal fix (GitHub issue #190): `SignEntry` gained `String[] frontRawLines`/
`backRawLines` — the raw, unparsed sign message lines for each side, captured by `SignHelper.createSignEntry`
alongside the already-parsed `frontText`/`backText`. These fields are `null` (not empty arrays) rather than
populated for any entry that came through migration from pre-`V5` data — `Version5Converter.convertToV5`
(`SignEntryV4` → current `SignEntry`) sets both explicitly to `null`, since there's no raw text on disk to backfill
from. `SignManager.reloadConfig()` (`core-pipeline.md` §3) uses these fields to re-run `SignLinesParser` against
every cached sign's *original* text on every `/bluemap reload`, rather than only re-resolving each sign's
already-parsed prefix against the new config — this is what lets an edited `REGEX` prefix correctly reclassify a
sign (or drop it, or move it to a different group) without a manual in-game re-edit or a server restart. An entry
with `null` raw lines (still-unmigrated pre-`V5` data) keeps the old, more limited reload behavior, since there's
nothing to re-parse. See `README.md`'s "Troubleshooting" section for how this is described to end users.

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
   - Otherwise reads it and runs the **exact same V1-V5 chain as before sharding**, unchanged:
     `VersionedFileSignEntryLoader.loadSignEntries(...)`, falling back to `Version1SignEntryLoader.loadSignEntries(...)`
     if that returns `null`. Region-sharding is layered *after* this existing version normalization, not a
     replacement for it. `VersionedFileSignEntryLoader` treats a structurally-valid document missing
     `version`/`data` (e.g. `"{}"`) as an explicit, intentional signal to fall back to `Version1SignEntryLoader`
     (ticket 05), rather than relying on Gson's nulls to coincidentally route there. A `V2` file is converted
     `SignEntryV2` → `SignEntryV3` (`Version3Converter.convertToV3`, per entry, `convertV2EntrySafely` isolating a
     bad entry) → `SignEntryV3` → `SignEntryV4` (`Version4Converter.convertToV4`, see below) → `SignEntryV4` →
     current `SignEntry` (`Version5Converter.convertToV5`) in the same pass — a V2 file is always exactly three
     migrations behind current, so all three converters run back-to-back rather than requiring a server restart
     between each version bump. A `V3` file skips straight to `Version4Converter` then `Version5Converter`; a `V4`
     file goes straight to `Version5Converter`. All loaders isolate per-entry conversion failures
     (`convertV2EntrySafely`/`loadEntry`, ticket 05) so one malformed V1/V2 entry logs and is skipped instead of
     losing the whole file — the same pattern `SignProvider.loadSigns` already applies per entry at step 3 below.
     `Version1SignEntryLoader`'s dimension
     normalization (`getNormalizedMapId`) recognizes both the short legacy names (`"nether"`/`"end"`/`"overworld"`)
     and the canonical-but-unnamespaced resource paths (`"the_nether"`/`"the_end"`), with or without a `minecraft:`
     namespace already attached (ticket 05) — previously only the three exact lowercase shorthand strings
     normalized, so anything else fell through unchanged and permanently mismatched the live dimension key
     post-migration, duplicating markers as "new" signs. `Version1SignEntryLoader` and the `V2`/`V3`/`V4` branches
     of `VersionedFileSignEntryLoader` each back up the file before migrating (`.v1.bak`/`.v2.bak`/`.v3.bak`/
     `.v4.bak` respectively) so the original isn't overwritten with no recoverable copy (ticket 02) — but they don't
     react the same way to a failed backup: `Version1SignEntryLoader` throws `IllegalStateException`, aborting that
     migration (uncaught here, isolated instead by `LegacySignFileMigrator`'s own try/catch around the whole
     chain), while `VersionedFileSignEntryLoader`'s `V2`/`V3`/`V4` branches log an error and **continue anyway**,
     still returning the converted current-version entries in-memory without a pre-migration backup on disk.
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
carried a `MarkerTypeV2` enum, not a raw prefix string) to `SignEntryV3`/`SignLinesParseResult` — `SignEntryV3` is
the frozen pre-`createdAtMillis` shape, not the live `SignEntry` class (that final V3→current hop is
`Version4Converter`'s job, above) — per side (front/back):
- If that side's `SignLinesParseResultV2.markerType()` is `null` — it never matched any group under V1/V2 — the
  converted side stays non-matching (`prefix = null`) rather than fabricating a prefix for it. Fixed for GitHub
  issue #138 / review finding #6 (resolved 2026-07-23); previously every side got the first POI group's prefix
  regardless of whether it had actually matched anything.
- Otherwise it looks up
  `Arrays.stream(markerGroups).filter(g -> g.type() == MarkerGroupType.POI).findFirst()` and uses *that* group's
  `prefix()`. **The first configured POI-type group's prefix is still assumed for every matched side** — this part
  of finding #6 remains open: with multiple POI-type groups configured, migration can't recover which one a V2
  entry actually matched, since `MarkerTypeV2` only distinguished POI-vs-not, not which POI group. Safe historically
  because V2-era configs only ever had one POI group.
- If no POI-type group is configured at all, this now logs a warning and treats the side as non-matching
  (`prefix = null`) instead of `.orElseThrow()`ing `NoSuchElementException` — also fixed for #138/#6, since the
  prior behavior lost every persisted sign for the session via `LegacySignFileMigrator`'s catch-and-discard.

Unaffected by region-sharding — this conversion still runs once, during `LegacySignFileMigrator`'s reuse of the
V1/V2/V3/V4 chain, before entries are ever partitioned by region. See `Version3ConverterTest` (`testing.md`) for the
per-case coverage.

`Version4Converter.convertToV4(SignEntryV3 entry, int indexInFile, long fileLastModifiedMillis)`: converts
`SignEntryV3` (the frozen pre-`createdAtMillis` shape) to current `SignEntry` by copying every field unchanged and
setting `createdAtMillis = fileLastModifiedMillis + indexInFile`. No real placement history exists for signs
created before line markers existed, so this value is **arbitrary but stable** — derived from the region file's
own last-modified time (read once per file via `Files.getLastModifiedTime`, falling back to `0L` on any
`IOException` rather than throwing) plus the entry's array index within that file — not a reconstruction of true
history. Two old signs that end up in the same eventual `LINE` group but were migrated from *different* region
files can land on a duplicate `createdAtMillis`; `LineGroupResolver.members` (`core-pipeline.md` §3) breaks such
ties deterministically by sorting on position (`x`, then `y`, then `z`) after `createdAtMillis`, rather than
depending on input/iteration order. See `Version4ConverterTest` (`testing.md`) for per-case coverage.

`SignProvider.saveSigns(storageRoot)`: gets all cached entries from `SignManager.getAll()`, delegates to
`RegionShardedSignEntryWriter.write(storageRoot, entries, gson)`, which partitions via `SignRegionPartitioner` and
writes each region's `VersionedSignFile(V4, ...)` (creating parent dirs as needed). Any region file already on disk
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
*Last updated: 2026-09-02 | Verified against: feature/tpwalke2/196-extrude-markers (5b38852)*

