# Testing

## Framework and commands

JUnit 5 (Jupiter), via `testImplementation platform("org.junit:junit-bom:5.11.4")` +
`org.junit.jupiter:junit-jupiter`, `testRuntimeOnly org.junit.platform:junit-platform-launcher`. `test { useJUnitPlatform() }`
in `build.gradle`.

- `./gradlew test` — run the whole suite (`src/test/java`)
- `./gradlew test --tests "*.SignLinesParserTest"` — single class
- `./gradlew build` runs tests as part of the build and **fails the build on any test failure**

## What's testable vs. what isn't

Only plain-Java classes with **no Minecraft/Fabric/BlueMap API types in their method signatures** are unit tested.
Qualifying today: `SignLinesParser`/`ParsingContext`/`SignLinesParseResult`, `SignEntry`, `SignEntryHelper`,
`SignChunkKey`/`SignChunkIndex`, `MarkerGroup`/`MarkerGroupMatchType`/`MarkerGroupType`, `ConfigManager`/`ConfigProvider`,
`ReactiveQueue`, `HtmlUtils`, `FileUtils`, the sign-persistence loaders/converters/writer (`VersionedFileSignEntryLoader`,
`Version1SignEntryLoader`, `Version3Converter`, `RegionShardedSignEntryLoader`, `RegionShardedSignEntryWriter`,
`SignRegionKey`, `SignRegionPartitioner`, `LegacySignFileMigrator`), `ActionFactory`/`MarkerSetIdentifierCollection`.

`Version1SignEntryLoader` used to be a partial exception — its legacy-shorthand (`"nether"`/`"end"`/`"overworld"`)
dimension normalization branch read `net.minecraft.world.level.Level`'s static constants, requiring a running
Minecraft bootstrap. That dependency was removed (the three identifiers are now spelled out as literal strings,
e.g. `"minecraft:the_nether"`) specifically so `Version1SignEntryLoaderTest` could exercise the shorthand-normalization
branches directly instead of only via an already-namespaced dimension string.

Excluded — anything that must reference live game types (`SignHelper`, the two mixins, `BlueMapSignMarkersMod`
including its `ServerChunkEvents.CHUNK_LOAD` reconciliation handler, `BlueMapAPIConnector`, `SignProvider` itself,
since loading/saving calls the game-coupled `SignManager` singleton) — these are thin glue and can only be
verified manually: `./gradlew runServer` + placing/editing/breaking signs in-game (and, for chunk-load
reconciliation specifically, removing a sign block without going through the mod — e.g. deleting its chunk's
region file to force a regen — then reloading that chunk), watching the BlueMap web UI update.

## Current coverage

As of `26.2-0.17.0`, `src/test/java/com/tpwalke2/bluemapsignmarkers/`:
- `core/signs/SignLinesParserTest.java` — 10 `@Test` methods covering `SignLinesParser`: label-on-prefix-line vs.
  label-on-following-line, multi-line detail joining/trimming, leading/interstitial blank-line handling, no-match
  and all-blank sign results, `REGEX` match type's whole-line-match requirement (contrasted with `STARTS_WITH`),
  first-matching-group-wins ordering, and whitespace tolerance. Test helper pattern: private static factory methods
  (`startsWithGroup(prefix, name)`, `regexGroup(pattern, name)`) building a `MarkerGroup` with the remaining fields
  fixed at reasonable defaults — follow this pattern for new test classes over hand-building full `MarkerGroup`
  records inline.
- `common/HtmlUtilsTest.java` — escaping of individual metacharacters and a full script payload, plain text left
  untouched, escape-before-`<br>`-substitution ordering (a sign literally containing `<br>` renders as escaped
  entities, not a live tag), newline-to-`<br>` conversion including consecutive newlines and no-newline input.
- `core/signs/SignChunkKeyTest.java` — chunk assignment via `floorDiv` (origin, negative coordinates, the
  15/16 chunk-boundary blocks), mirroring `SignRegionKeyTest`'s pattern but at 16-block chunk granularity instead
  of 512-block region granularity.
- `core/signs/SignChunkIndexTest.java` — add/query round-trip, multiple signs in one chunk, signs in different
  chunks/dimensions staying isolated, `remove` dropping a chunk's map entry once its key set empties, `remove` of
  an untracked key being a no-op, `clear` resetting everything.
- `core/signs/persistence/SignRegionKeyTest.java` — region assignment via `floorDiv` (including negative
  coordinates and the exact region-boundary blocks 511/512), and `relativeFilePath` namespace/path splitting
  (including the no-colon `unknown` dimension and a nested-path dimension).
- `core/signs/persistence/SignRegionPartitionerTest.java` — grouping entries by region and dimension, multiple
  entries landing in the same region, empty input.
- `core/signs/persistence/RegionShardedSignEntryWriterTest.java` — one file per region; stale region files (signs
  removed, or moved to a different region) quarantined with a `.stale` suffix on re-save, not deleted.
- `core/signs/persistence/RegionShardedSignEntryLoaderTest.java` — `hasSignData` true/false cases, round-trip
  load of entries written across multiple regions and dimensions.
- `core/signs/persistence/LegacySignFileMigratorTest.java` — no-legacy-file case, migrating a V3-shaped legacy file
  (entries land in the right region files, legacy file renamed to `.migrated` rather than deleted), migrating a
  V1-shaped legacy file (prefix fabricated from the configured POI group, per `Version3Converter`'s existing
  behavior).
- `core/signs/SignEntryHelperTest.java` — `getPrefix` (front-text preferred, back-text fallback, `null` when
  neither side matches), `isMarkerType` (`true` on a matching POI prefix, `false` on no prefix, and `false` rather
  than throwing when the prefix isn't in `prefixGroupMap` at all — the config-reload case from
  `plans/marker-group-config-reload-plan.md`), `getLabel`/`getDetail` front/back precedence and combining.
- `core/signs/SignEntryTest.java` — standard `equals`/`hashCode` contract on the hand-written implementation
  (reflexive, symmetric, per-field inequality, not equal to `null`/another type), `withKey` returning a new instance
  with only the key changed; also documents a latent risk (`equalsAndHashCodeThrowNpeWhenThisEntrysKeyIsNull`) that
  `equals`/`hashCode` NPE if the entry's own `key` (or `playerId`/`frontText`/`backText`) is `null` — harmless today
  since no call site actually calls `SignEntry.equals()`/`hashCode()` (the sign cache keys on `SignEntryKey`).
- `core/signs/ParsingContextTest.java` — the `(null, "", "")` sentinel when no marker group is ever set,
  `buildResult()` using the set group's `prefix()` plus the current label, multiple `appendDetail` calls joining
  with `\n`, and that the final `trim()` only strips the outermost whitespace of the joined detail, not per-line
  padding.
- `common/FileUtilsTest.java` — `createBackup` copies the original when no backup exists yet and leaves an existing
  backup untouched; `moveToBackup` moves the original into place, no-ops when the source is missing, and no-ops when
  a backup already exists; documents (`createBackupSwallowsACopyFailure...`) that a copy failure is caught and only
  logged, never surfaced to the caller (review finding #13).
- `config/ConfigProviderTest.java` — `loadConfig` creating and persisting defaults when the file is absent; missing
  optional V2 fields defaulted per-field in `convertToLoadedMarkerGroup`; malformed JSON returning `null`; V1→V2
  migration producing one POI group plus a `.v1.bak` backup; and a documented "current behavior" case (review
  finding #9) where a well-formed V2 file that happens to contain the substring `poiPrefix` anywhere (e.g. inside a
  group's `name`) is misdetected as V1 and collapsed to the single default POI group.
- `config/ConfigManagerTest.java` — `get()` returns the config from the most recent `reload`; falls back to
  `new BMSMConfigV2()` defaults when the configured path fails to load; a second `reload()` replaces (not merges
  with) what an earlier `reload` cached.
- `core/bluemap/actions/ActionFactoryTest.java` — each of `createAddPOIAction`/`createRemovePOIAction`/
  `createUpdatePOIAction` builds the right `MarkerIdentifier` and action-specific fields; repeated calls for the
  same map/group (same or different action type) reuse the same `MarkerSetIdentifier` instance via
  `MarkerSetIdentifierCollection`.
- `core/markers/MarkerSetIdentifierCollectionTest.java` — `getIdentifier` returns the same instance for a repeated
  `(mapId, markerGroup)` pair (case-insensitive on `mapId`), distinct pairs get distinct identifiers. Also includes
  `@Disabled` concurrent-first-callers stress test documenting review finding #16 (unsynchronized check-then-act
  over the backing `TreeMap`/`HashMap`/`HashSet` lets concurrent first-time callers each construct their own
  instance instead of converging on one) — confirmed failing when temporarily un-`@Disabled`d; re-enable once the
  planned concurrency-hardening pass makes that sequence atomic.
- `core/reactive/ReactiveQueueTest.java` — enqueue → processor callback delivery; `shouldRun` gating (queued while
  false, resumes once true, a mid-drain false leaves the rest queued); `shutdown()`/`isShutdown()`, including that
  `getExecutor()` silently creates a fresh executor next time work is scheduled after a shutdown; a documented gap
  where an exception thrown by the processor callback itself never reaches `messageProcessorErrorCallback` (it's
  captured on an unawaited `Future` and dropped) versus a submission-time failure, which does reach it via a fake
  executor.
- `core/signs/persistence/loaders/Version3ConverterTest.java` — basic V2→V3 conversion, plus three documented
  "current behavior" gaps (review finding #6): a non-matching side (`markerType null`) still gets the POI group's
  prefix fabricated onto it since `convertToV3` never reads `markerType`; with multiple POI-type groups configured,
  the first one in array order always wins; with zero POI-type groups configured, `.orElseThrow()` throws
  `NoSuchElementException` rather than failing gracefully.
- `core/signs/persistence/loaders/VersionedFileSignEntryLoaderTest.java` — V3-passthrough (no backup written);
  V2 branch (converts via `Version3Converter`, backs up to `.v2.bak`); the catch-all fallback returning `null`
  rather than throwing, for both malformed JSON and empty content (which parses to `null` and NPEs on
  `.version()`, caught by the same generic `catch`).
- `core/signs/persistence/loaders/Version1SignEntryLoaderTest.java` — the three recognized legacy shorthand strings
  normalizing to their canonical identifiers, case-insensitively; an already-namespaced dimension string passing
  through unchanged; backup creation to `.v1.bak`; and a documented Low-severity finding that an unrecognized
  dimension string is still silently lowercased on the `default` branch rather than preserved as-is.

## CI integration

`.github/workflows/build.yml` (push/PR to `main`, `releases/**`) and `.github/workflows/publish.yml` (manual
dispatch) both run `./gradlew test` before anything else — a test failure in `publish.yml` blocks the Modrinth
publish step entirely.

Both workflows have a `summarize test results` step (`if: always()`, so it runs even when tests fail)
immediately after the test step: it sums the `tests`/`failures`/`errors`/`skipped` XML attributes out of
`build/test-results/test/*.xml` using plain shell (`sed`, looping the glob, `shopt -s nullglob`) and writes a
markdown pass/fail table to `$GITHUB_STEP_SUMMARY`. This was a **deliberate choice over a `checks: write`-based
JUnit reporter action** — those actions don't get `checks: write` permission on PRs from forks by default in a
public repo, so the summary step was written to need no extra permissions.

---
*Last updated: 2026-07-21 | Verified against: 26.2-0.17.0 (72d4280)*

