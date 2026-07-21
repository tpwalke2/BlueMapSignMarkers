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
- **`ActionFactory`** (`core/bluemap/actions/ActionFactory.java`) — zero tests. Needed: each
  `create{Add,Remove,Update}POIAction` builds the right `MarkerIdentifier`/action fields, and repeated calls for the
  same map/group reuse the same `MarkerSetIdentifier` (its delegation contract with `MarkerSetIdentifierCollection`).

## Priority 2 — testable per AGENTS.md, currently zero coverage

- **`ConfigProvider`** (`config/ConfigProvider.java`) — explicitly named in AGENTS.md as testable; no test file
  exists. Needed: `loadConfig` creates+persists defaults when the file is absent; loads a well-formed V2 file and
  null-coalesces missing fields in `convertToLoadedMarkerGroup` (matchType/type/offsets/minDistance/maxDistance/
  defaultHidden); malformed JSON returns `null` and logs rather than throwing; V1→V2 migration (`loadV1Config`)
  produces one POI group plus a `.v1.bak` backup. Also worth a test that documents review finding #9 as-is: a V2
  config whose JSON happens to contain the substring `poiPrefix` (e.g. in a group name/icon) gets misdetected as V1
  and collapsed to defaults.
- **`ConfigManager`** (`config/ConfigManager.java`) — zero tests. Needed: `get()` falls back to `new BMSMConfigV2()`
  defaults when `ConfigProvider.loadConfig()` returns null; `reload()` swaps in a freshly loaded config.
- **`FileUtils`** (`common/FileUtils.java`) — zero tests. Needed: `createBackup` copies when no backup exists yet and
  no-ops when one already does; `moveToBackup` moves when the source exists and no backup exists, no-ops otherwise;
  and a test documenting review finding #13 — copying into a destination that can't be written logs a warning but
  doesn't throw or signal failure to the caller.
- **`SignEntry`** (`core/signs/SignEntry.java`) — zero tests. Hand-rolled `equals`/`hashCode`/`toString` with no null
  guards on `key`/`playerId`/`frontText`/`backText` (flagged as latent risk in the review). Needed: standard
  equals/hashCode contract tests (reflexive, distinct-field inequality) to lock in current behavior before anyone
  touches this class.
- **`ParsingContext`** (`core/signs/ParsingContext.java`) — zero dedicated tests (only indirectly exercised through
  `SignLinesParserTest`). Needed: `buildResult()` with no marker group set returns the `(null, "", "")` sentinel;
  `appendDetail`'s newline-joining/trim behavior tested directly, independent of the parser.

## Priority 3 — loader edge cases with existing partial coverage

- **`Version3Converter`** (`core/signs/persistence/loaders/Version3Converter.java`) — only indirectly exercised via
  `LegacySignFileMigratorTest`, and only for "exactly one POI group configured, front side matches." Missing, all
  tied to review finding #6: zero POI-type groups configured (currently throws `NoSuchElementException` via
  `.orElseThrow()` — document this crash as current behavior); more than one POI-type group configured (currently
  always picks the first in array order, silently reassigning prefix); a non-matching side (e.g. `backText` that
  never matched any group originally) still getting fabricated a prefix it never had.
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
