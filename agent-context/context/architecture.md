# Architecture

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 (`sourceCompatibility`/`targetCompatibility` = `VERSION_25` in `build.gradle`) |
| Mod platform | Fabric, `DedicatedServerModInitializer` (server-only, no client logic) |
| Build | Gradle wrapper + Fabric Loom `1.17-SNAPSHOT` |
| Target Minecraft | version pinned in `gradle.properties` (`minecraft_version`), currently `26.2` |
| Fabric loader/API | `loader_version` / `fabric_api_version` in `gradle.properties` |
| External integration | BlueMap API `blue_map_api_version` (`compileOnly` — provided by BlueMap itself at runtime), targeting BlueMap `blue_map_version` |
| JSON | Gson, `Strictness.LENIENT` |
| Logging | SLF4J, all loggers named via `Constants.MOD_ID` |
| Testing | JUnit 5 (Jupiter), via `junit-bom:5.11.4` |
| Publish target | Modrinth, via `com.modrinth.minotaur` Gradle plugin |
| CI | GitHub Actions |

All version numbers live in `gradle.properties` — never hardcode a version in `build.gradle` or source.
`mod_version` follows `<minecraft_version>-<mod_semver>` (e.g. `26.2-0.17.0`).

## Top-level layout

```
src/main/java/com/tpwalke2/bluemapsignmarkers/   mod source
src/main/resources/                              fabric.mod.json, mixins config, icon asset
src/test/java/com/tpwalke2/bluemapsignmarkers/   unit tests (plain-Java logic only)
plans/                                           design/implementation plans, written before larger changes
.github/workflows/                               build.yml (CI), publish.yml (manual Modrinth publish)
build.gradle, gradle.properties                  build config and all version numbers
README.md                                        end-user install + config-format docs
AGENTS.md / CLAUDE.md                            AI agent guidance (source of truth CLAUDE.md defers to AGENTS.md)
```

## Package taxonomy

| Package | Contents |
|---------|----------|
| `com.tpwalke2.bluemapsignmarkers` (root) | `BlueMapSignMarkersMod` (entrypoint, lifecycle hooks; implements `ServerPathProvider`), `Constants` (`MOD_ID`), `ServerPathProvider` (interface: `getMarkerStorageRoot(MinecraftServer): Path`, resolves `{server_root}/bluemapsignmarkers/{level}`) |
| `common` | `FileUtils` — `createBackup`/`moveToBackup(originalPath, suffix, description)` (copy- vs. rename-based backup, used by config/sign migration code before overwriting or retiring an old-format file); `HtmlUtils` — `escape`/`toHtmlDetail`, escapes player-supplied sign text before it crosses into BlueMap's HTML-rendered marker `detail` field |
| `config` | `ConfigManager` (lazy `volatile` singleton over the loaded config, `reload()` on first null `get()`), `ConfigProvider` (load/save/migrate `BMSM-Core.json`) |
| `config.models` | `BMSMConfigV1` (legacy, single `poiPrefix` field), `BMSMConfigV2` (current runtime shape — `MarkerGroup[]`, ctor also seeds Gson defaults) |
| `config.persistence` | `LoadingBMSMConfigV2` / `LoadingMarkerGroupV2` — a *nullable-boxed-field* mirror of `BMSMConfigV2`/`MarkerGroup` used only for Gson deserialization, so partially-specified user JSON can be detected and defaulted field-by-field in `ConfigProvider.convertToLoadedMarkerGroup` rather than Gson silently zeroing missing primitives |
| `core` | `WorldMap` — just the `UNKNOWN` dimension-key sentinel used when a sign's `Level` is null |
| `core.bluemap` | `BlueMapAPIConnector` (the only class that touches the BlueMap API), `IResetHandler` (callback interface `SignManager` implements to replay its cache on BlueMap reset) |
| `core.bluemap.actions` | `MarkerAction` (abstract base, holds a `MarkerIdentifier`), `AddMarkerAction`/`UpdateMarkerAction`/`RemoveMarkerAction`, `ActionFactory` (builds actions, resolves `MarkerSetIdentifier`s) |
| `core.markers` | `MarkerGroup` (record: the config unit), `MarkerGroupMatchType` (`STARTS_WITH`/`REGEX`), `MarkerGroupType` (currently only `POI`), `MarkerIdentifier`/`MarkerSetIdentifier`/`MarkerSetIdentifierCollection` (marker identity + dedup) |
| `core.reactive` | `ReactiveQueue<T>` (generic buffer-while-unavailable queue) + its three functional-interface callbacks (`ShouldRunCallback`, `MessageProcessorCallback<T>`, `MessageProcessorErrorCallback`) — see `core-pipeline.md` |
| `core.signs` | `SignEntry`/`SignEntryKey` (immutable sign snapshot + position/dimension key), `SignHelper` (game-coupled: builds `SignEntry` from a `SignBlockEntity`), `SignEntryHelper` (plain-Java: derives label/detail/prefix from a `SignEntry`), `SignLinesParser`/`ParsingContext`/`SignLinesParseResult` (plain-Java parsing state machine), `SignManager` (singleton decision point — cache + dispatch), `SignChunkKey`/`SignChunkIndex` (plain-Java chunk-position lookup backing chunk-load sign reconciliation, see `core-pipeline.md` §4) |
| `core.signs.persistence` | `SignProvider` (load/save, dispatches to the region-sharded loader/writer or migration), `SignFileVersions` (`V1`/`V2`/`V3` — per-region-file schema version, unchanged by sharding), `VersionedSignFile` (`{version, data}` envelope), `SignRegionKey` (record: dimension + region coords; `forPosition` does the `floorDiv` region math, `relativeFilePath` builds the `{namespace}/{path}/r.{x}.{z}.json` path), `SignRegionPartitioner` (groups `SignEntry`s by `SignRegionKey`), `RegionShardedSignEntryWriter` (writes each region's file; region files that dropped to zero signs are quarantined with a `.stale` suffix, not deleted, unless any region write failed — in which case quarantine is skipped entirely, to avoid mistaking a load failure for a genuine "signs removed" case), `LegacySignFileMigrator` (one-shot: reads the pre-sharding single file through the existing V1/V2/V3 chain, writes it out sharded, backs up the legacy file only once every expected region file is confirmed written to disk) |
| `core.signs.persistence.loaders` | `VersionedFileSignEntryLoader` (tries versioned envelope; V2 → converts + backs up), `Version1SignEntryLoader` (pre-versioning fallback; also normalizes legacy `nether`/`end`/`overworld` map-id strings to real dimension identifiers — the identifiers are spelled out as literal strings rather than read from `net.minecraft.world.level.Level` statics, so the class has no Minecraft-type dependency and is fully unit-testable), `Version3Converter` (V2 model → current `SignEntry`), `RegionShardedSignEntryLoader` (walks a storage root's directory tree, loads each region file via `VersionedFileSignEntryLoader`, flattens to one list; `hasSignData(Path)` is the migration-trigger check) |
| `core.signs.persistence.models` | `SignEntryV2`, `SignLinesParseResultV2`, `MarkerTypeV2` — frozen shapes representing the on-disk V2 format, kept solely so `Version3Converter` can still read old files; do not evolve these, they must stay byte-compatible with historical `signs.json` |
| `mixin` | `SignBlockEntityInject` (injects `SignBlockEntity.updateSignText` TAIL), `AbstractBlockInject` (injects `BlockBehaviour.affectNeighborsAfterRemoval` HEAD) |

## Import / module resolution

Standard Java package imports. Fabric Loom remaps Minecraft/Fabric API artifacts at build time; `build.gradle`'s
`loom { mods { "bluemapsignmarkers" { sourceSet sourceSets.main } } }` registers the main source set as the mod's
dev source for `runServer`/`runClient`.

## Build / CI / publish tooling

- `./gradlew build` — compile, run tests, produce jar in `build/libs/`; fails on any test failure
- `./gradlew test` — JUnit 5 suite only (`src/test/java`)
- `./gradlew runServer` / `runClient` — dev Minecraft instance with the mod + BlueMap loaded
- `.github/workflows/build.yml` — runs on push/PR to `main` and `releases/**`: `./gradlew test` → summarize JUnit
  XML results to `$GITHUB_STEP_SUMMARY` (plain shell `sed`, no third-party reporter action — see `testing.md` for
  why) → `./gradlew build` → upload `build/libs/` as an artifact
- `.github/workflows/publish.yml` — manual `workflow_dispatch` only: runs tests first (failure blocks publish), then
  `./gradlew modrinth -PbuildNumber=<run_number>` (alpha, from a branch dispatch) or the same with `-PisRelease`
  (release, from a `v*` tag dispatch)

---
*Last updated: 2026-07-21 | Verified against: 26.2-0.17.0 (72d4280)*

