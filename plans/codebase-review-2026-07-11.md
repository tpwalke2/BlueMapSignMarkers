# Codebase review — 2026-07-11

Full-codebase review (not scoped to one change), done by two parallel deep-dive passes: one over the
sign/config/persistence stack, one over the BlueMap integration/markers/reactive-queue stack. Top findings from
each were spot-checked directly against the source before inclusion here. Severities: Critical > High > Medium >
Low > Nitpick.

Findings were review-only at the time of writing (no fixes applied). Ordered by severity within each area; a
**Resolved** note is added under a finding once it's fixed.

## Critical

### 1. `getMarkerFilePath` resolves to the working-dir name, not the world/level-save name — breaks per-world isolation
**`src/main/java/com/tpwalke2/bluemapsignmarkers/BlueMapSignMarkersMod.java:35`**
```java
var worldSaveName = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().getParent().getFileName();
```
`getWorldPath(LevelResource.ROOT)` already returns the level-save directory itself (e.g. `<run-dir>/world`). The
extra `.getParent()` steps up to `<run-dir>`, so `.getFileName()` returns the run/working directory's name, not the
level-name folder. Any server that changes `level-name` in `server.properties` to host a different map from the
same working directory resolves to the exact same `config/bluemapsignmarkers/<run-dir-name>/signs.json` path —
directly contradicting the per-world persistence design in AGENTS.md. Confirmed by direct read; looks like an
accidental extra `.getParent()` hop (the variable name `worldSaveName` implies the level-name folder was intended).
Verify with `runServer` under two different `level-name` values before fixing.

**Resolved 2026-07-15 (`75b6270`, `#109 Region-sharded sign storage`).** `getMarkerStorageRoot` now calls
`.toAbsolutePath().normalize()` on the level dir before taking `getParent()`/`getFileName()`, collapsing the `.`
segment `LevelResource.ROOT` resolves to before stepping up — the missing normalize was the root cause. The old
buggy formula is deliberately kept, unchanged, as `getLegacyMarkerFilePath`, used only to locate the pre-existing
single-file `signs.json` for one-time migration. See `agent-context/context/config-and-persistence.md`.

### 2. `ReactiveQueue.getExecutor()` self-heals a shut-down executor, defeating `shutdown()` and leaking threads
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/reactive/ReactiveQueue.java:56-62`**
```java
private synchronized ExecutorService getExecutor() {
    if (isShutdown()) {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    return executor;
}
```
Any caller that finds the executor shut down silently allocates a new one — there's no flag for "this queue is
intentionally retired," only `executor.isShutdown()`. Trigger: `onDisable()` calls `markerActionQueue.shutdown()`
while `processMessages()` (line 35-46) is still mid-loop on a worker thread from that same executor. The loop's next
iteration calls `getExecutor()` again (line 40) to submit the next message, observes `isShutdown()==true`, and
creates a **fresh** `ExecutorService` — non-daemon threads that nothing ever explicitly shuts down again (the next
`shutdown()` call only fires on the next `onDisable`, which may be long from now or never). Confirmed by direct
read. Secondary effect: because a live executor now exists, `onEnable()`'s `if (markerActionQueue.isShutdown())`
guard (`BlueMapAPIConnector.java:161`) sees `false` on the next enable, so `resetQueue()`/`fireReset()` — the
intended "replay all signs" recovery path — gets skipped even though the connector went through a disable/enable
cycle.

Github issue #134

**Resolved 2026-07-22.** `shutdown()` now sets a `volatile shutdownRequested` flag under the same monitor
as `getExecutor()`. Once set, `getExecutor()` never creates a replacement executor, `process()`/
`processMessages()` bail out instead of resubmitting, and `isShutdown()` short-circuits on the volatile
read so it reports correctly regardless of `executor`'s own visibility — closing both the thread-leak and
the `onEnable()`-skips-`resetQueue()`/`fireReset()` side effect. `processMessages()`'s inner submit also
catches `RejectedExecutionException` separately from other exceptions, treating a submission rejected by a
concurrent shutdown as expected (stop draining) rather than routing it to `messageProcessorErrorCallback`
as noise. Regression tests: `ReactiveQueueTest.shutdownRacingMidDrainStopsTheLoopWithoutSpawningAReplacementExecutor`
(reproduces the mid-drain shutdown race via the existing `SynchronousExecutorService` test double) and
`shutdownPermanentlyStopsTheQueueFromProcessingLaterEnqueues` (rewritten to assert deterministically via
that same double instead of a timing-based wait).

## High

### 3. `SignEntryHelper.isMarkerType` NPEs when a persisted sign's prefix isn't in the current config
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignEntryHelper.java:21`**
```java
return prefix != null && prefixGroupMap.get(prefix).type() == markerGroupType;
```
No null-check on the map lookup. `prefixGroupMap` is built once from the *current* `BMSM-Core.json`. Every
`SignEntry` loaded from a persisted `signs.json` carries whatever prefix string was current the last time the file
was saved. Renaming or removing a marker-group prefix between server restarts — a normal config edit — makes
`prefixGroupMap.get(prefix)` return `null` for every already-persisted sign using the old prefix, and `.type()`
throws NPE. Confirmed by direct read. This is the first line evaluated in `SignManager.addOrUpdateSign`, so it
fires both during `SignProvider.loadSigns`'s startup loop and again live whenever that chunk loads or the sign is
next edited.

Github issue #135

**Resolved (`320fb47`, "#121 allow hot reloads on config").** `isMarkerType` now null-checks the
`prefixGroupMap.get(prefix)` lookup before calling `.type()`, returning `false` instead of throwing when a
persisted sign's prefix isn't in the current config.

### 4. `SignProvider.loadSigns`'s load loop has no per-entry exception handling — one bad entry drops the whole file
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/persistence/SignProvider.java:59-61`**
```java
for (SignEntry signEntry : signEntries) {
    SignManager.addOrUpdate(signEntry);
}
```
Combined with finding #3: a single entry with a stale prefix throws an uncaught NPE that aborts the *entire* loop,
silently dropping every remaining sign in the file for the rest of the session. Only the outermost catch around all
of `loadSigns` catches it, logging a generic "Failed to load markers from file" with no indication of partial loss.
Directly conflicts with AGENTS.md's stated guarantee that old `signs.json` files "must keep loading." Same
all-or-nothing pattern also exists in `Version1SignEntryLoader.java:28-31`.

**Resolved 2026-07-15 (`7014f82`, "Apply suggestions from code review").** `SignProvider.loadSigns`'s load loop now
wraps each `SignManager.addOrUpdate(signEntry)` call in its own try/catch, logging the failing entry's key and
continuing with the rest of the file instead of aborting. Finding #3 itself (the underlying NPE source in
`SignEntryHelper.isMarkerType`) is unaddressed — this fix contains the blast radius to one entry rather than
removing the root cause. `Version1SignEntryLoader.java:28-31`'s same all-or-nothing pattern is also still
unaddressed.

### 5. Concurrent, unsynchronized mutation of a shared BlueMap marker map
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/bluemap/BlueMapAPIConnector.java`** — `addMarker` (132-154),
`updateMarker` (114-125), `removeMarker` (127-130) vs. `getMarkerSets` (175-207, `synchronized`)
`getMarkerSets(...)` is `synchronized`, but `addMarker`/`updateMarker`/`removeMarker` — which mutate the
`Map<String, Marker>` returned by `MarkerSet.getMarkers()` — are `static` and not synchronized on anything.
`ReactiveQueue`'s executor is sized to `availableProcessors()`, so on any multi-core host more than one
`MarkerAction` can be dispatched to `processMarkerAction` concurrently. Two actions targeting different sign
positions on the same map + marker group resolve to the same cached `MarkerSet` and thus the same underlying marker
`Map` instance (whose concrete thread-safety is controlled by BlueMap's API, not this mod). Concurrent
`put`/`remove`/iteration on that map risks lost updates or corruption during a resize. `getMarkerSets`'s
`synchronized` only protects lookup/creation of the list reference, not what happens to its contents afterward.

Github issue #137

**Resolved 2026-07-22.** `processMarkerAction` is now `synchronized`, so `addMarker`/`updateMarker`/
`removeMarker`'s mutation of a `MarkerSet`'s marker `Map` can never run concurrently with another
dispatched action, whichever `MarkerSet`(s) it targets. This also resolves a related, previously
untracked concurrency-pass item (not in this doc, surfaced in a prior session): `SignProvider.loadSigns`
dispatches one `MarkerAction` per persisted sign at server startup, fanning out across
`ReactiveQueue`'s `availableProcessors()` worker threads — a startup-time amplification of this same race,
reliably triggered by any boot with a nontrivial sign count. Both are closed by the same fix since they
share the same unsynchronized-map root cause. `BlueMapAPIConnector` has no automated test coverage (it
references live BlueMap API types — see AGENTS.md's testable-vs-game-coupled split); this change is a
single `synchronized` keyword with no behavior change beyond mutual exclusion, verified by a clean compile
and full test-suite pass, not by `runServer`.

### 6. `Version3Converter` fabricates a prefix for every migrated V2 entry, even non-matching sides
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/persistence/loaders/Version3Converter.java:26`**
```java
var markerGroup = Arrays.stream(markerGroups).filter(m -> m.type() == MarkerGroupType.POI).findFirst().orElseThrow();
```
Verified by reading `SignLinesParseResultV2` (`markerType` is `@Nullable`, no `prefix` field at all) and
`VersionedFileSignEntryLoader.java:31-33`, which calls `Version3Converter.convertToV3` unconditionally for every
entry's front **and** back text, regardless of whether `markerType` was null (meaning that side never matched any
group in the original V1/V2 data). Every migrated sign — including sides that originally matched nothing — gets
assigned the prefix of whichever `POI`-type `MarkerGroup` happens to be first in the *current* config array. Two
distinct bugs stack here: (a) blank/non-matching sides spuriously become "matching" after migration, fabricating
markers that never should have existed, and (b) if more than one POI-type group is configured, every migrated sign
silently gets reassigned to the first one regardless of which prefix it actually had. `.orElseThrow()` also throws
an uncaught `NoSuchElementException` if the config has zero POI-type groups (e.g. `"markerGroups": []`), which
propagates uncaught through the fallback path all the way to `SignProvider`'s outer catch — total loss of all
persisted signs for the session. (b) alone is already flagged as a known limitation in `agent-context/context/
config-and-persistence.md`; (a) is a separate, unacknowledged defect found during this review.

Github issue #138

### 7. `BlueMapAPIConnector.shutdown()`'s `unregisterListener` calls likely no-op
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/bluemap/BlueMapAPIConnector.java:41-44`**
```java
public void shutdown() {
    BlueMapAPI.unregisterListener(this::onEnable);
    BlueMapAPI.unregisterListener(this::onDisable);
}
```
`this::onEnable` evaluated here creates a **new** method-reference object, distinct by identity/default `equals`
from the `this::onEnable` passed to `BlueMapAPI.onEnable(...)` in the constructor (line 37). Unless
`BlueMapAPI.unregisterListener` does something other than identity/`equals`-based list removal, this fails to
detach the connector from BlueMap's callbacks — a "shut down" connector keeps reacting to future enable/disable
events. Caveat: confirming this needs the BlueMap API's own listener-registration source, which wasn't in the
review's file set — flagged High rather than Critical for that reason.

Github issue #140

### 8. Malformed `REGEX` marker-group prefix throws uncaught, blocking sign processing broadly
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignLinesParser.java:80-90`**
`line.matches(markerGroup.prefix())` / `line.replaceAll(markerGroup.prefix(), "")` use the raw config string as a
regex with zero validation anywhere in the chain — `ConfigProvider` never test-compiles it. A single typo'd regex
throws `PatternSyntaxException` the first time matching reaches that group, and because groups are tried in
configured order until one matches, a bad regex placed early can throw for essentially every sign the server
processes, not just ones meant for that group. Propagates uncaught out of `SignBlockEntityInject`'s `@Inject`
callback and `BlueMapSignMarkersMod.onBlockEntityLoad`.

Github issue #139

## Medium

### 9. `ConfigProvider` misclassifies V2 configs as V1 via raw substring search
**`src/main/java/com/tpwalke2/bluemapsignmarkers/config/ConfigProvider.java:84`**
```java
if (configContent.contains("poiPrefix")) { ... }
```
Confirmed by direct read. This is a substring search over the whole file, not a structural check. Any V2 config
whose JSON text happens to contain `poiPrefix` anywhere (e.g. in a marker-group name/icon) is misclassified as V1;
`GSON.fromJson(configContent, BMSMConfigV1.class)` binds nothing real, falls back to the default `"[poi]"` prefix,
and `loadV1Config` overwrites the entire config with a single default POI group via `saveConfig` — discarding all
real marker groups. A `.v1.bak` backup is made first, so it's recoverable, but the corrupted config no longer
contains `poiPrefix`, so it won't retrigger — the damage silently sticks unless someone notices.

### 10. No `awaitTermination`; old-generation tasks can straddle a `resetQueue()`/`fireReset()` replay
**`ReactiveQueue.java:52-54` (`shutdown()`) and `BlueMapAPIConnector.java` `resetQueue()` (58-66) / `onEnable()`
(160-169)**
`shutdown()` requests graceful shutdown but never waits for in-flight tasks. `onEnable()` can immediately call
`resetQueue()` (swapping in new `markerActionQueue`/`markerSetsCache` objects) while a straggling task from the
previous executor generation is still running `processMarkerAction` against the connector's other shared state. This
can interleave with `fireReset()`'s full sign-cache replay, causing duplicate adds or a stale remove/add applied
after the replay already established "correct" state.

### 11. `resetQueue()`/`onEnable()` mutate fields without the lock `getMarkerSets()` synchronizes on
**`BlueMapAPIConnector.java`** — `resetQueue()` (58-66), `onEnable()` (160-169), vs. `getMarkerSets()` (175,
`synchronized`)
Neither `resetQueue()` nor `onEnable()` synchronizes on `this`, so a worker thread inside the synchronized
`getMarkerSets()` can race with a plain-field reassignment of `markerSetsCache` on another thread — a data race on
the field reference itself, independent of the map's own thread-safety. Same root cause class as #10.

### 12. `blueMapAPI` / `executor` field visibility — no `volatile`, no happens-before edge
**`BlueMapAPIConnector.java:167,210`** and **`ReactiveQueue.java:48-50,56-62`**
`blueMapAPI` is written on the BlueMap callback thread (`onEnable`) and read on `ReactiveQueue` worker threads
(`getMaps()`) with no synchronization. Same pattern in `ReactiveQueue`: `isShutdown()` reads `executor`
unsynchronized while `getExecutor()` writes it under `synchronized`. Per JMM neither read is guaranteed to observe
the latest write — a real (if hard to reproduce) visibility race in both cases.

### 13. `FileUtils.createBackup`/`copyFile` swallow backup failures, then the caller overwrites the original anyway
**`src/main/java/com/tpwalke2/bluemapsignmarkers/common/FileUtils.java:17-32`**
`copyFile` catches `IOException` and only logs a warning — it never signals failure back up. Both the V1→V2 config
migration and the V1/V2→V3 sign-file migration call `createBackup(...)` and then unconditionally overwrite the
original file on the next line. If the backup silently fails (disk full, permissions), the only copy of the user's
pre-migration data is gone with no recoverable backup and no visible signal beyond a log line — undermines the
entire point of backing up before a data-changing migration.

### 14. `SignLinesParser`: REGEX groups can't have a label share the line with the prefix
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignLinesParser.java:88-90`**
`getLabel`'s REGEX branch reapplies the same pattern via `replaceAll` that `matches()` requires to match the whole
line. A pattern strict enough to satisfy whole-line-match without trailing text works (empty label, correct); a
pattern loosened specifically to allow trailing label text on the prefix line is the only way to make `matches()`
accept it at all, but then `replaceAll` greedily strips the entire line, including the label — leaving it blank.
There's no configuration that makes "label on the same line as a REGEX prefix" actually work, and the failure is
silent. Not covered by any existing test.

### 15. `SignLinesParser.trim()` doesn't recognize non-ASCII invisible whitespace
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignLinesParser.java:27`**
`.trim()` misses NBSP (U+00A0), ideographic space (U+3000), zero-width space (U+200B) — all reachable via
clipboard-paste into a sign. A line consisting solely of one becomes the sign's "first non-blank line," matches no
marker group, and permanently transitions the parser to `INVALID` — so a genuine `[poi]` line immediately after is
never reached. A leading invisible character on an otherwise-valid prefix line defeats `startsWith`/most regexes the
same way, with only a DEBUG log and no other feedback.

### 16. `MarkerSetIdentifierCollection`'s internal collections aren't thread-safe
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/markers/MarkerSetIdentifierCollection.java:6-11,34-38`**
Plain `TreeMap`/`HashMap`/`HashSet`, not concurrent collections. Likely only touched from the single Minecraft
server thread based on the documented architecture, but that isn't verifiable from this class alone — flagged as an
open question pending a look at all `ActionFactory` call sites.

### 17. `SignManager.reloadSigns()` races with concurrent live sign edits
**`src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignManager.java:87-94`**
`IResetHandler.reset()` fires on `BlueMapAPI.onEnable`, not necessarily the server thread. `reloadSigns` does
`signCache.clear()` then replays a snapshot of the old values one at a time via `addOrUpdateSign` — not atomic
against concurrent server-thread mutations from the mixins. A real-time edit/removal mid-reload can be clobbered by
a stale replayed value, or a sign removed during the race can be silently re-added by the replay.

## Low

- **`SignManager` singleton's double-checked locking is broken — `instance` isn't `volatile`**
  (`SignManager.java:22`). A thread on the fast path can observe a non-null but not-fully-constructed
  `SignManager` per allowed JMM reordering. Concrete trigger: `SERVER_STARTING`'s `loadSigns` racing a
  `BLOCK_ENTITY_LOAD` firing during world load, both hitting `SignManager` for the first time.
- **`BlueMapSignMarkersMod`: hardcoded `"unknown"` playerId sentinel duplicated, uncoupled from `WorldMap.UNKNOWN`**
  (`BlueMapSignMarkersMod.java:42`, `SignManager.java:136`). Two independently-hardcoded literals that only happen
  to match today; editing one without the other silently breaks playerId-preservation logic on chunk load vs.
  player edit.
- ~~**`ServerPathProvider.java` is dead code**~~ — **Resolved.** `BlueMapSignMarkersMod` now `implements ...
  ServerPathProvider`; no longer unwired.
- **Charset mismatch between save and load** in `ConfigProvider.java` (`saveConfig` uses platform-default
  `FileWriter`, `loadConfig` reads explicit UTF-8) — still open. Non-ASCII marker-group names are at risk of
  encoding mismatch across a restart if the JVM default charset isn't UTF-8. The sign-file half of this finding
  (originally also flagged in `SignProvider.java`) is **resolved**: that class was replaced by the region-sharded
  storage classes (`RegionShardedSignEntryWriter`/`Loader`, `LegacySignFileMigrator`, `#109`), which consistently
  use `Files.writeString`/`readString` with explicit UTF-8 on both sides.
- **No fail-fast config validation** — nothing checks marker-group prefixes are non-empty, that REGEX prefixes
  compile, or that prefixes are unique across groups at load time; bad data is only caught later, silently or by
  crashing (see finding #8).
- **`Version1SignEntryLoader.getNormalizedMapId`** only recognizes exact lowercase `"nether"`/`"end"`/`"overworld"`
  literals; any other legacy dimension string falls through unchanged, permanently mismatching the live dimension
  key post-migration and duplicating markers as "new" signs. Unverifiable against the true historical V1 format from
  files in scope.
- **`VersionedFileSignEntryLoader`**: a structurally-valid-but-incomplete JSON (missing `version`/`data`) is handled
  by Gson returning nulls rather than throwing, which happens to funnel into the same V1-fallback path as a genuine
  parse exception — works today but by coincidence, not design.
- **`getMarkerSets`'s `putIfAbsent`-inside-per-map-forEach pattern is fragile** (`BlueMapAPIConnector.java:187-204`)
  — correctness today depends on an implicit, undocumented invariant (shared mutable list + no-op `putIfAbsent` on
  every iteration after the first) that a future refactor could silently break.
- **`logProcessingMessage` logs full raw sign text at INFO unconditionally**, sanitizing only `\n`
  (`BlueMapAPIConnector.java:97-102`) — other control characters (`\r`, ANSI escapes) aren't sanitized, a minor
  log-injection/log-noise vector.
- **Cache-key growth risk, now confirmed rather than speculative**: `MarkerSetIdentifier`/`markerSetsCache` keys
  are value-equality on the *entire* `MarkerGroup` record, so any config field change (icon, offsets, distances)
  between reloads produces a new, never-evicted cache entry. Originally speculative, pending a config-reload path
  not covered in this review — that path now exists (`SignManager.reloadConfig()`, added by `#121` hot-reload
  work) and confirms the risk: it rebuilds the prefix map but never calls `blueMapAPIConnector.resetQueue()`, so
  `markerSetsCache` is never cleared on a live config reload. Worth reconsidering as Medium severity rather than
  Low.
- **`SignManager`: dual-sided signs with different prefixes mix marker-group semantics** — the marker's group
  (icon/type/visibility) comes from whichever side `getPrefix` picks (front preferred), but `getDetail` merges
  *both* sides' text regardless of whether they matched different groups.
- **`SignEntry`'s hand-written `equals`/`hashCode`** have no null-guards on `key`/`playerId`/`frontText`/`backText`;
  latent only, since no current call site actually calls `SignEntry.equals()`/`hashCode()` (the sign cache is keyed
  by `SignEntryKey`).
- **`Version3Converter`/`Version1SignEntryLoader`** have no per-entry exception handling, same all-or-nothing loss
  pattern as finding #4.

## Nitpick

- ~~`ConfigManager.loadCoreConfig()` is `synchronized` despite being called exactly once from a `static final`
  field initializer~~ — **stale**: the premise no longer holds. Config hot-reload work (`#121`) restructured
  `ConfigManager`: `coreConfig` is now `volatile` and lazily loaded, and `reload()`/`reload(Path)` are
  `synchronized` to guard concurrent hot-reloads for real, not a redundant guard on a one-time static initializer.
- The default single-`[poi]`-group literal is duplicated verbatim across `BMSMConfigV2` and `LoadingBMSMConfigV2`
  with no shared constant.
- `SignManager.isMarkerType` re-derives `getPrefix(signEntry)` that's already computed a few lines later —
  redundant, harmless.
- `MarkerAction` subclasses' `getX/Y/Z` widen `int` to `double` — fine, presumably to match the BlueMap API's
  positional types.
- `SignLinesParserTest.java` has no test for a permissive REGEX pattern with trailing label text on the prefix line
  — worth a test to document/lock in the blank-label behavior from finding #14.

## Verified correct (no action needed)

- **`HtmlUtils.escape`/`toHtmlDetail`** (from the recent #131 fix): escaping order is correct (`&` first, avoiding
  double-escaping), `escape()` runs before the `\n`→`<br>` substitution as the plan requires, and test coverage in
  `HtmlUtilsTest.java` matches every case in `plans/html-detail-escaping-plan.md`. Traced the full text path
  (`ParsingContext` → `SignEntryHelper` → `AddMarkerAction`/`UpdateMarkerAction` → `BlueMapAPIConnector.addMarker`/
  `updateMarker`) and confirmed `toHtmlDetail` is applied at both and only both call sites that reach a `detail`
  field — no double-escaping, no gap.
- **`MarkerGroup`/`MarkerSetIdentifier`/`MarkerIdentifier`** — records, so `equals`/`hashCode` are generated
  structurally-correct and safe as map keys.
- **Mixin injection points** (`SignBlockEntityInject`, `AbstractBlockInject`) match their documented intent; no
  correctness issues found in the injection logic itself.
- **Resource/stream handling** — all I/O reviewed uses NIO `Files.readString`/`Files.copy` or try-with-resources
  `FileWriter`; no unclosed-handle leaks found.
- **`BlueMapAPIConnector`'s two action-dispatch switches** (`processMarkerAction`, `logProcessingMessage`) currently
  list all three `MarkerAction` subclasses — the AGENTS.md-documented "missing case falls to `default`" risk is real
  but not currently triggered.

## Open questions requiring follow-up outside this review's scope

- Does `ReactiveQueue.processMessages` submit each message as an independent task to the same executor (implying no
  ordering guarantee between sequential dispatches, e.g. a prefix-change's remove-then-add)? If so, `SignManager`'s
  prefix-change branch (dispatching remove then add as two separate calls) may not apply in order under load.
- Confirm whether `BlueMapAPI.unregisterListener` matches by identity or by some other means, to settle finding #7
  definitively.
