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
`ReactiveQueue`, `HtmlUtils`, `FileUtils`, `ColorUtils`, `LineGroupResolver`, the sign-persistence loaders/converters/writer
(`VersionedFileSignEntryLoader`, `Version1SignEntryLoader`, `Version3Converter`, `Version4Converter`,
`RegionShardedSignEntryLoader`, `RegionShardedSignEntryWriter`, `SignRegionKey`, `SignRegionPartitioner`,
`LegacySignFileMigrator`), `ActionFactory`/`MarkerSetIdentifierCollection`.

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

As of `main` (`374d6db`), `src/test/java/com/tpwalke2/bluemapsignmarkers/`:
- `core/signs/SignLinesParserTest.java` — 12 `@Test` methods covering `SignLinesParser`: label-on-prefix-line vs.
  label-on-following-line, multi-line detail joining/trimming, leading/interstitial blank-line handling, no-match
  and all-blank sign results, `REGEX` match type's whole-line-match requirement (contrasted with `STARTS_WITH`),
  first-matching-group-wins ordering, whitespace tolerance, and the constructor's up-front prefix validation
  (`malformedRegexPrefixIsSkippedInsteadOfThrowing`, `nullPrefixIsSkippedInsteadOfThrowing` — a broken group is
  dropped/logged rather than throwing later out of `parse()`, GitHub issue #139/review finding #8). Test helper
  pattern: private static factory methods (`startsWithGroup(prefix, name)`, `regexGroup(pattern, name)`) building a
  `MarkerGroup` with the remaining fields fixed at reasonable defaults — follow this pattern for new test classes
  over hand-building full `MarkerGroup` records inline.
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
- `core/signs/persistence/LegacySignFileMigratorTest.java` — no-legacy-file case, migrating a V4-shaped legacy file
  (`migratesAV4LegacyFileAndBacksItUpWithoutDeletingIt`, renamed from the pre-line-markers V3 version — entries
  land in the right region files, legacy file renamed to `.migrated` rather than deleted), migrating a V1-shaped
  legacy file (prefix fabricated from the configured POI group, per `Version3Converter`'s existing behavior).
- `core/signs/SignEntryHelperTest.java` — `getPrefix` (front-text preferred, back-text fallback, `null` when
  neither side matches); `isMarkerType` (`true` on a matching POI prefix, `false` on a `null` prefix, and `false`
  rather than throwing when the prefix isn't in `prefixGroupMap` at all — the config-reload case from
  `plans/marker-group-config-reload-plan.md`) — takes the already-resolved prefix `String` directly rather than a
  `SignEntry` (ticket 08, so callers that already have the prefix don't make `isMarkerType` re-derive it);
  `getLabel`/`getDetail` front/back precedence and combining, plus `getDetail`'s ticket-07 fix
  (`getDetailUsesOnlyFrontWhenSidesMatchDifferentGroups`): when front and back match *different* marker groups,
  only the front's detail is used rather than merging both — see `core-pipeline.md`'s `SignEntryHelper` paragraph.
- `core/signs/SignEntryTest.java` — standard `equals`/`hashCode` contract on the hand-written implementation
  (reflexive, symmetric, per-field inequality including the added `createdAtMillis` field, not equal to
  `null`/another type), `withKey` returning a new instance with only the key changed (`createdAtMillis` carried
  through unchanged); `equalsAndHashCodeToleratesNullFields` (ticket 08) confirms `equals`/`hashCode` no
  longer `NPE` if the entry's own `key` (or `playerId`/`frontText`/`backText`) is `null` — now backed by
  `Objects.equals`/`Objects.hash` instead of unguarded field-level `.equals()`/`.hashCode()` calls.
- `core/signs/ParsingContextTest.java` — the `(null, "", "")` sentinel when no marker group is ever set,
  `buildResult()` using the set group's `prefix()` plus the current label, multiple `appendDetail` calls joining
  with `\n`, and that the final `trim()` only strips the outermost whitespace of the joined detail, not per-line
  padding.
- `common/FileUtilsTest.java` — `createBackup` copies the original when no backup exists yet and leaves an existing
  backup untouched; `moveToBackup` moves the original into place, no-ops when the source is missing, and no-ops when
  a backup already exists; `createBackupReturnsFalseWhenTheCopyFails` and
  `createBackupReturnsFalseWhenTheBackupDestinationIsADirectory` (ticket 02, superseding review finding #13) — a
  copy failure (or a non-file already sitting at the backup path) is now reported back to the caller via
  `createBackup`'s `boolean` return rather than caught, logged, and swallowed; `copyFile` copies via a temp file in
  the same directory plus an atomic move, so a failure partway through can't leave a truncated file a later
  `createBackup` call would mistake for a valid backup.
- `common/LogUtilsTest.java` — `sanitizeForLog` (ticket 06) strips ANSI CSI escape sequences (color/cursor codes,
  private-mode sequences) and normalizes `\r\n`/`\n`/`\r` to literal `\n`/`\r` text, closing a log-injection/log-noise
  vector in player-controlled sign text logged at INFO by `BlueMapAPIConnector.logProcessingMessage` — previously
  only `\n` was escaped.
- `common/ColorUtilsTest.java` — `parseHex` on the 8-digit (`#RRGGBBAA`) and 6-digit (`#RRGGBB`, alpha defaults to
  opaque) forms, mixed-case hex digits, an optional leading `#`, and falling back to opaque red (never throwing)
  for `null`, wrong-length, or non-hex-character input.
- `core/signs/LineGroupResolverTest.java` — `members` filters to signs sharing `(parentMap, prefix, label)` exactly
  (a different map, prefix, or label is excluded), orders results by `createdAtMillis` ascending, breaks ties on a
  duplicate `createdAtMillis` deterministically by position (`x`, then `y`, then `z` — the cross-region-file
  migration-tie scenario the persistence section's `Version4Converter` paragraph describes), and returns an empty
  list for empty input.
- `config/ConfigProviderTest.java` — `loadConfig` creating and persisting defaults when the file is absent; missing
  optional V2 fields defaulted per-field in `convertToLoadedMarkerGroup`; malformed JSON returning `null`; V1→V2
  migration producing one POI group plus a `.v1.bak` backup (aborting via `IllegalStateException`, caught by the
  outer catch-all, if that backup fails — ticket 02); structural (not substring) V1-vs-V2 detection (ticket 01,
  resolves former review finding #9) — a V2 config whose group `name`/`icon` happens to contain the literal text
  `poiPrefix` is no longer misdetected as V1; and `validateMarkerGroups` failing fast (ticket 01) on an empty
  prefix, a non-compiling `REGEX` prefix, or a prefix duplicated across groups, each surfacing as `loadConfig`
  returning `null` (caught by the same catch-all) rather than corrupting silently or deferring to a later NPE/skip.
  Note: `lineWidth`/`lineColor` defaulting and `warnOnTypeFieldMismatches`'s POI/LINE field-mismatch warning have no
  dedicated assertions in this class yet — an uncovered gap from the line-markers work, not something this update
  invented coverage for.
- `config/ConfigManagerTest.java` — `get()` returns the config from the most recent `reload`; falls back to
  `new BMSMConfigV2()` defaults when the configured path fails to load; a second `reload()` replaces (not merges
  with) what an earlier `reload` cached.
- `core/bluemap/actions/ActionFactoryTest.java` — each of `createAddPOIAction`/`createRemovePOIAction`/
  `createUpdatePOIAction` builds the right `MarkerIdentifier` and action-specific fields;
  `createChangeGroupPOIActionBuildsARemoveAndAddEffectPair` (ticket 09, updated for the line-markers rewrite) now
  asserts `createChangeGroupPOIAction` returns a `GroupTransitionMarkerAction` with exactly two `effects` — a
  `RemoveMarkerAction` for the old group then an `AddMarkerAction` for the new one — rather than the older single
  action type carrying two identifiers directly; repeated calls for the same map/group (same or different action
  type) reuse the same `MarkerSetIdentifier` instance via `MarkerSetIdentifierCollection`. Note: `createSetLineAction`/
  `createRemoveLineAction` have no dedicated test in this class yet — an uncovered gap, not something this update
  invented coverage for.
- `core/markers/MarkerSetIdentifierCollectionTest.java` — `getIdentifier` returns the same instance for a repeated
  `(mapId, markerGroup)` pair (case-insensitive on `mapId`), distinct pairs get distinct identifiers. Also includes
  `concurrentFirstTimeCallersForTheSameComboConvergeOnOneIdentifierInstance`, an active (not `@Disabled`) regression
  test for review finding #16 (resolved 2026-07-22): 8 threads x 500 iterations racing `getIdentifier` for the same
  brand-new pair, asserting they always converge on one identity-equal instance — this used to fail before
  `getIdentifier` became `synchronized` (see `core-pipeline.md` §5).
- `core/reactive/ReactiveQueueTest.java` — enqueue → processor callback delivery (single and multiple messages,
  each exactly once); `shouldRun` gating (queued while false, resumes once true, a mid-drain false leaves the rest
  queued); a submission failure for one message reaching the error callback without affecting later messages.
  Concurrency-hardening regression coverage (`plans/codebase-review-2026-07-11.md`, resolved 2026-07-22):
  `shutdownBlocksUntilAnInFlightTaskFinishesBeforeReturning` (finding #10 — `shutdown()` now blocks on
  `awaitTermination` rather than returning while a task is still mid-flight), `shutdownPermanentlyStopsTheQueueFromProcessingLaterEnqueues`
  and `shutdownRacingMidDrainStopsTheLoopWithoutSpawningAReplacementExecutor` (finding #2 — a shut-down queue never
  self-heals a replacement executor, including when `shutdown()` races a still-draining `processMessages()` loop),
  and `concurrentEnqueueBurstDeliversEveryMessageExactlyOnceDespiteRedundantDrainLoopFanOut` (documents current
  "before" behavior: a burst of concurrent enqueues spawns more drain-loop submissions than messages, but every
  message still lands exactly once — not itself a bug, just characterizing the fan-out). A documented remaining
  gap: an exception thrown by the processor callback itself never reaches `messageProcessorErrorCallback` (it's
  captured on an unawaited `Future` and dropped) versus a submission-time failure, which does reach it via a fake
  executor. `reactiveQueueGivesNoOrderingGuaranteeBetweenIndependentlySubmittedMessages` (ticket 09) confirms and
  reproduces that two independently-`enqueue()`d messages have no relative execution-order guarantee once the
  executor has more than one worker thread (blocks the first message's processing on a real 2-thread pool and
  shows the second can finish first) — see `core-pipeline.md` §7 for why this is left as-is rather than fixed in
  `ReactiveQueue` itself; its doc comment now points at `GroupTransitionMarkerAction`/`applySingleAction` (renamed
  from `ChangeGroupMarkerAction`/`processChangeGroupAction`) as the caller relying on bundled-message ordering.
- `core/signs/persistence/loaders/Version3ConverterTest.java` — basic V2→V3 conversion (both sides matched, POI
  group's prefix assumed for both, output is the frozen `SignEntryV3` shape); `aNonMatchingSideStaysNonMatching` and
  `treatsAMatchedSideAsNonMatchingWhenNoPoiGroupIsConfigured` (GitHub issue #138/review finding #6, parts a and c,
  resolved 2026-07-23 — a `markerType null` side stays non-matching, and a matched side is treated as non-matching
  rather than throwing when zero POI-type groups are configured); `whenMultiplePoiGroupsAreConfiguredTheFirstInArrayOrderWins`
  documents the still-open part of finding #6: with multiple POI-type groups configured, `convertToV3` can't
  recover which one a V2 entry actually matched, so the first one in array order always wins.
- `core/signs/persistence/loaders/Version4ConverterTest.java` — `convertToV4` copies every `SignEntryV3` field
  unchanged into the current `SignEntry` shape; `createdAtMillisIsFileLastModifiedPlusIndexInFile` and
  `differentIndicesInTheSameFileProduceDifferentButStableTimestamps` confirm the arbitrary-but-stable
  `fileLastModifiedMillis + indexInFile` formula (no real placement history exists for pre-existing signs, see the
  persistence-doc `Version4Converter` paragraph).
- `core/signs/persistence/loaders/VersionedFileSignEntryLoaderTest.java` — `v4ContentIsParsedDirectlyWithoutCreatingABackup`
  (renamed from the pre-line-markers V3-passthrough test — no backup written for a file already at the current
  version); `v3ContentIsConvertedThroughVersion4ConverterAndBackedUp` (new — a `V3` file converts via
  `Version4Converter`, backs up to `.v3.bak`); the `V2` branch converts via `Version3Converter` **then**
  `Version4Converter` in the same pass, backs up to `.v2.bak`, and is per-entry-isolated: one malformed V2 entry is
  skipped rather than losing the whole file (ticket 05); the catch-all fallback returning `null` rather than
  throwing, for both malformed JSON and empty content (which parses to `null` and NPEs on `.version()`, caught by
  the same generic `catch`); a structurally-valid document missing `version`/`data` (e.g. `"{}"`) explicitly falling
  back to V1 rather than relying on Gson's nulls to coincidentally route there (ticket 05); and the `V2`/`V3`
  branches returning `null` (falls through to the V1 loader) rather than proceeding, if backing up to `.v2.bak`/
  `.v3.bak` fails (ticket 02).
- `core/signs/persistence/loaders/Version1SignEntryLoaderTest.java` — the three recognized legacy shorthand
  strings (`"nether"`/`"end"`/`"overworld"`) *and* the canonical-but-unnamespaced resource paths
  (`"the_nether"`/`"the_end"`, ticket 05) normalizing to their canonical namespaced identifiers, case-insensitively,
  with or without a `minecraft:` namespace already attached; an already-namespaced dimension string passing
  through unchanged; backup creation to `.v1.bak`, throwing `IllegalStateException` (aborting the migration,
  ticket 02) if that backup fails; one malformed entry being skipped rather than losing the whole file (ticket 05,
  `loadEntry`'s per-entry try/catch); and a documented Low-severity finding that an unrecognized dimension string
  is still silently lowercased on the `default` branch rather than preserved as-is.

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
*Last updated: 2026-08-13 | Verified against: main (374d6db)*

