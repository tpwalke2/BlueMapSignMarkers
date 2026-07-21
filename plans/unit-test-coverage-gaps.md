# Unit test coverage gaps

## Context

Prior to a planned concurrency-hardening pass on `ReactiveQueue` / `MarkerSetIdentifierCollection` /
`BlueMapAPIConnector` (tracked separately — see `plans/codebase-review-2026-07-11.md` findings #5, #10, #11, #12,
#16), we want confidence that current behavior across the testable core is already locked in by tests. This is an
inventory of every class that qualifies as unit-testable per AGENTS.md's "Testable vs. game-coupled code" section
(plain Java, no Minecraft/Fabric/BlueMap API types in its signature) and currently has no test coverage, or only
partial coverage, plus a few items that look like test gaps but aren't.

## Priority 1 — in the concurrency-hardening blast radius (test before touching)

- **`ReactiveQueue`** (`core/reactive/ReactiveQueue.java`) — DONE. `ReactiveQueueTest` (12 cases) covers: enqueue →
  processor callback delivery (single and multiple messages); `shouldRun` gating (queued but not processed while
  false, resumes once true, and a mid-drain false leaves the rest of the backlog queued until resumed);
  `shutdown()`/`isShutdown()` semantics, including that `shutdown()` doesn't durably stick (`getExecutor()` silently
  creates a fresh executor the next time work is scheduled); and the concurrency characterization test (a burst of
  20 concurrent enqueues delivers every message exactly once but submits more executor tasks than messages,
  documenting today's redundant one-loop-per-enqueue fan-out as a "before" baseline).

  One finding changed the "needed" list while writing the tests: the error callback does **not** fire on a
  processor-callback exception as the plan originally assumed. `messageProcessorCallback.processMessage(message)`
  runs inside a task handed to `ExecutorService.submit()`; nothing ever calls `.get()` on the returned `Future`, so
  an exception it throws is captured on that `Future` and silently dropped — it never reaches the `try/catch` in
  `processMessages()`. That `try/catch` is only reachable via a submission-time failure (e.g. the executor itself
  rejecting the task), not a processing exception. Both behaviors are now locked in as tests:
  `exceptionThrownByProcessorCallbackIsNotSurfacedToTheErrorCallback` documents the swallowed-exception gap, and
  `submissionFailureForOneMessageInvokesErrorCallbackAndLeavesLaterMessagesUnaffected` exercises the real
  submission-failure path the `try/catch` actually guards, using a fake executor to simulate the failure.

  Testability required one small, additive seam: a package-private constructor overload on `ReactiveQueue` that
  accepts an `ExecutorService`, so tests can inject a synchronous (same-thread) executor or a counting/failing fake
  instead of the lazily-created fixed thread pool. No other production behavior changed.
- **`MarkerSetIdentifierCollection`** (`core/markers/MarkerSetIdentifierCollection.java`) — DONE.
  `MarkerSetIdentifierCollectionTest` (5 cases, no production changes needed — the class was already plain Java with
  a public no-arg constructor) covers: `getIdentifier` returns the same instance for a repeated `(mapId, markerGroup)`
  pair; distinct `mapId`s and distinct `markerGroup`s each get distinct identifiers; and `mapId` matching is
  case-insensitive (a lookup with different casing returns the same cached instance).

  Also includes the concurrent-access stress test the plan called for, in the style of
  `SignChunkIndexTest.concurrentAddDuringRemovalOfTheLastKeyIsNeverLost`:
  `concurrentFirstTimeCallersForTheSameComboConvergeOnOneIdentifierInstance` gates many threads on a latch and has
  them all call `getIdentifier` for the same brand-new `(mapId, markerGroup)` combo at once, then asserts (via
  `IdentityHashMap`-backed identity comparison, not `.equals()` — `MarkerSetIdentifier` is a record, so two
  independently-`new`'d instances with the same field values are already `.equals()` regardless of any race) that
  every caller got back the exact same instance. Confirmed by temporarily removing `@Disabled` and running it: it
  reliably fails against the current implementation (500 iterations x 8 threads reproduces >1 distinct instance for
  the same combo well within the first few iterations) — `getIdentifier`'s "is this combo cached?" check and its
  cache write are two separate, unsynchronized steps over plain `TreeMap`/`HashMap`/`HashSet` fields (review finding
  #16), so concurrent first-time callers can each construct and return their own instance instead of converging on
  one. Left `@Disabled` with a reason pointing at finding #16 so `./gradlew test` stays green; re-enable once the
  concurrency-hardening pass makes that check-then-act sequence atomic.
- **`ActionFactory`** (`core/bluemap/actions/ActionFactory.java`) — DONE. `ActionFactoryTest` (5 cases, no production
  changes needed — already plain Java) covers: `createAddPOIAction`/`createRemovePOIAction`/`createUpdatePOIAction`
  each build the right `MarkerIdentifier` (x/y/z, `parentSet` carrying the given mapId/markerGroup) and action-specific
  fields (label/detail, newLabel/newDetail); and repeated calls for the same map/group — both from the same factory
  method and across different action types — reuse the exact same `MarkerSetIdentifier` instance, confirming the
  delegation contract with `MarkerSetIdentifierCollection`.

## Priority 2 — testable per AGENTS.md, currently zero coverage

- **`ConfigProvider`** (`config/ConfigProvider.java`) — DONE. `ConfigProviderTest` (5 cases) covers: `loadConfig`
  creates and persists defaults when the file is absent; loading a well-formed V2 file null-coalesces missing
  optional fields in `convertToLoadedMarkerGroup` (matchType→`STARTS_WITH`, type→`POI`, offsetX/offsetY→0,
  defaultHidden→`false`, minDistance→0.0, maxDistance→10000000.0); malformed JSON returns `null` rather than
  throwing; and V1→V2 migration (`loadV1Config`) produces one POI group plus a `.v1.bak` backup file. Also covers
  review finding #9 as a documented "current behavior" test: a well-formed V2 config whose JSON happens to contain
  the substring `poiPrefix` (here, inside a marker group's `name`) is misdetected as a V1 file and silently collapsed
  to the single default POI group, discarding the actual configured prefix/name.

  Testability required a seam: `getConfigPath()` resolves a path relative to the process's working directory
  (`config/<mod-id>/BMSM-Core.json`), which tests can't safely point at a temp directory without it. Added
  package-private `loadConfig(Path)`/`saveConfig(BMSMConfigV2, Path)` overloads containing the existing method
  bodies parameterized on the path instead of always calling `getConfigPath()`; the public no-arg methods now just
  delegate to these with `getConfigPath()`, and the V1-migration branch's internal `saveConfig(...)` call was updated
  to pass the same path through instead of implicitly re-resolving `getConfigPath()`. No other behavior changed.
- **`ConfigManager`** (`config/ConfigManager.java`) — DONE. `ConfigManagerTest` (3 cases) covers: `get()` returns the
  config loaded by the most recent `reload`; `get()` falls back to `new BMSMConfigV2()` defaults when the configured
  path fails to load (malformed JSON); and `reload()` swaps in a freshly loaded config (reloading from a second path
  replaces what an earlier `reload` had cached, rather than merging or ignoring it).

  Testability required two changes. First, the same path-parameter seam as `ConfigProvider`: added a package-private
  `reload(Path)` overload (real `reload()` and `get()`'s first-touch load both go through it, passing
  `ConfigProvider.loadConfig()`'s real hardcoded path) so tests can point at a temp directory. Second, and less
  trivial: `coreConfig` was previously populated by an *eager* static field initializer (`= loadCoreConfig()`), which
  runs the instant the class is first referenced by anything — including, in a test run, merely loading
  `ConfigManagerTest` itself, before any test method executes. Since the Gradle `test` task's working directory is
  the project root (confirmed empirically), that eager load would have called the real `ConfigProvider.loadConfig()`
  and written a real `config/bluemapsignmarkers/BMSM-Core.json` into the project directory as a side effect of
  running the test suite. Changed `coreConfig` to lazy-initialize to `null`, with `get()` now calling `reload()` on
  first access if still unset. Observable behavior is unchanged for real usage (config is still loaded exactly once
  and cached until an explicit `reload()`), and it removes the untested-but-latent "referencing this class touches
  disk" side effect entirely.
- **`FileUtils`** (`common/FileUtils.java`) — DONE. `FileUtilsTest` (6 cases, no production changes needed — already
  plain Java operating on `String` paths, so `@TempDir` works directly) covers: `createBackup` copies the original
  when no backup exists yet, and leaves an existing backup untouched rather than overwriting it; `moveToBackup` moves
  the original into the backup path when the source exists and no backup exists yet, no-ops when the source is
  missing, and no-ops (leaving the original in place) when a backup already exists.

  Also covers review finding #13 as a documented "current behavior" test: `createBackupSwallowsACopyFailure...`
  routes the backup destination through the original file itself as a fake parent directory (a regular file can't be
  traversed as a directory component on any OS, so `Files.copy` reliably throws there without needing OS-specific
  permission hacks) and asserts `createBackup` returns normally, with no backup created — `copyFile` catches the
  `IOException` and only logs a warning, never signaling the failure back to the caller.
- **`SignEntry`** (`core/signs/SignEntry.java`) — DONE. `SignEntryTest` (12 cases, no production changes needed —
  already a plain record) covers the standard equals/hashCode contract: reflexive; two independently-constructed
  entries with the same field values are equal and share a hash code; symmetric; not equal to `null` or to a
  different type; distinct inequality for each of `key`/`playerId`/`frontText`/`backText` individually; and
  `withKey` returns a new instance with only the key changed, leaving the original untouched.

  Also locks in the latent risk the review flagged: `equalsAndHashCodeThrowNpeWhenThisEntrysKeyIsNull` documents that
  the hand-written `equals`/`hashCode` call straight into `key.equals(...)`/`key.hashCode()` with no null guard, so
  an entry whose own `key` (or, by the same unguarded pattern, `playerId`/`frontText`/`backText`) is null throws NPE
  rather than behaving like a normal implementation — currently latent only, since no call site actually calls
  `SignEntry.equals()`/`hashCode()` (the sign cache is keyed by `SignEntryKey`, not `SignEntry`).
- **`ParsingContext`** (`core/signs/ParsingContext.java`) — DONE. `ParsingContextTest` (5 cases, no production
  changes needed — already plain Java) covers: `buildResult()` returns the `(null, "", "")` sentinel when no marker
  group is set; with a group set, `buildResult()` uses its `prefix()` and the current label; detail is `""` when
  `appendDetail` was never called; multiple `appendDetail` calls join with `\n`; and the overall `trim()` on the
  built detail only strips the outermost whitespace of the joined text, not per-line padding — demonstrated with
  `" line1 "`/`" line2 "` producing `"line1 \n line2"` (inner padding around the newline survives, only the very
  first and very last characters are stripped).

## Priority 3 — loader edge cases with existing partial coverage

- **`Version3Converter`** (`core/signs/persistence/loaders/Version3Converter.java`) — DONE. `Version3ConverterTest`
  (4 cases, no production changes needed — already plain Java) covers: the basic conversion (key/playerId pass
  through unchanged, front/back each get the configured POI group's prefix, label/detail preserved) plus the three
  gaps from review finding #6 — a non-matching side (`markerType` `null`, i.e. never matched any group under V2)
  still gets fabricated the POI group's prefix, since `convertToV3` never actually looks at `markerType`; with more
  than one POI-type group configured, the first one in array order always wins (`Arrays.stream(...).findFirst()`),
  silently reassigning every entry to that group regardless of which group the sign originally matched; and with
  zero POI-type groups configured, `.orElseThrow()` on the empty stream throws `NoSuchElementException`, crashing
  the whole migration rather than failing gracefully — documented as current (undesirable) behavior, not fixed.
- **`VersionedFileSignEntryLoader`** (`core/signs/persistence/loaders/VersionedFileSignEntryLoader.java`) — only the
  V3-passthrough branch is exercised (via `LegacySignFileMigratorTest`). Missing: the V2 branch (`SignFileVersions.V2`,
  converts through `Version3Converter`, writes a `.v2.bak` backup) and the catch-all fallback (malformed/incomplete
  JSON returns `null` rather than throwing).
- **`Version1SignEntryLoader`** (`core/signs/persistence/loaders/Version1SignEntryLoader.java`) — only the
  already-namespaced dimension string path is exercised today. The actual legacy-shorthand normalization branch
  (`"nether"`/`"end"`/`"overworld"` → canonical identifier — the Low-severity finding about incomplete literal
  matching) is explicitly *not* tested, per the comment in `LegacySignFileMigratorTest`, because it reaches into
  `net.minecraft.world.level.Level`'s static constants, which need a Minecraft bootstrap. Recommend a small
  testability seam first — replace `Level.NETHER.identifier().toString()` etc. with the equivalent literal strings
  (`"minecraft:the_nether"`, `"minecraft:the_end"`, `"minecraft:overworld"`) so this branch no longer needs a
  bootstrap to test — then add cases for the three recognized literals, an unrecognized legacy string falling through
  unchanged (the actual bug), and case-insensitivity.

## Not test gaps — flagged for a different disposition

- **`ServerPathProvider`** — confirmed dead code (no implementers, no callers anywhere in `src/`). Consider removing
  rather than testing, per the existing review finding.
- **`SignManager`, `BlueMapAPIConnector`, `SignHelper`, `BlueMapSignMarkersMod`, the two Mixins** — game-coupled per
  AGENTS.md; no automated coverage possible without a bootstrapped Minecraft/Fabric/BlueMap environment. Remain
  manual-only via `runServer`.
- **Trivial types with no behavior** — `Constants`, `WorldMap`, `MarkerGroupMatchType`, `MarkerGroupType`,
  `IResetHandler`, the `core/reactive` package's three functional interfaces, `MarkerSetIdentifier`,
  `SignLinesParseResult`/`SignEntryKey`/`MarkerTypeV2`/`SignLinesParseResultV2` (plain data records with no derived
  logic) — skip.
- **`SignProvider`** (`core/signs/persistence/SignProvider.java`) — plain Java in its own signature, but
  `loadSigns`/`saveSigns` call straight into `SignManager` (a game-coupled singleton that constructs a
  `BlueMapAPIConnector` on first use). Can't be unit-tested in isolation as written — the format-selection and
  per-entry exception containment logic is entangled with the singleton. Flagging as a design question (would need
  the `SignManager` dependency extracted behind an interface/callback to test in isolation) rather than a
  straightforward add-a-test-class item.

## Verification

- `./gradlew test` after each new test class, to confirm the existing suite plus new tests pass.
- For the concurrency stress tests (`MarkerSetIdentifierCollection`), follow `SignChunkIndexTest`'s precedent of a
  few thousand iterations with a `CountDownLatch`-synchronized start to reliably surface races; expect the
  `MarkerSetIdentifierCollection` one to fail against the current implementation, which is the point of writing it
  now rather than after the hardening pass.
