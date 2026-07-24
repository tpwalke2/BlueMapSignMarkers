# Core pipeline: sign text → marker action

Cross-ref: `architecture.md` for package locations.

## 1. Entry points into the pipeline

Three ways a `SignEntry` gets built and handed to `SignManager`, plus a fourth path that only ever removes (never
builds/adds) an entry:

1. **Server startup** — `BlueMapSignMarkersMod.onServerStarting` → `SignProvider.loadSigns(storageRoot, legacyPath)`
   reads the per-world, region-sharded storage (migrating a pre-sharding single `signs.json` on first boot if
   found) and calls `SignManager.addOrUpdate(...)` for every stored entry (see `config-and-persistence.md`).
2. **Block entity load** — `BlueMapSignMarkersMod.onBlockEntityLoad` (registered on
   `ServerBlockEntityEvents.BLOCK_ENTITY_LOAD`) fires for every loaded `SignBlockEntity` and calls
   `SignHelper.createSignEntry(entity, "unknown")` → `SignManager.addOrUpdate(...)`. Player id is `"unknown"`
   here because chunk load isn't attributable to a player.
3. **Mixins** (`src/main/resources/bluemapsignmarkers.mixins.json`, server-only, `JAVA_21` compat level):
   - `SignBlockEntityInject` injects `SignBlockEntity.updateSignText` at `TAIL` → a player edited a sign →
     `SignManager.addOrUpdate(SignHelper.createSignEntry(this, player.getStringUUID()))`.
   - `AbstractBlockInject` injects `BlockBehaviour.affectNeighborsAfterRemoval` at `HEAD`, but only proceeds
     `if (state.getBlock() instanceof SignBlock)` → `SignManager.remove(new SignEntryKey(...))`.
4. **Chunk-load reconciliation** — `BlueMapSignMarkersMod.onChunkLoad` (registered on
   `ServerChunkEvents.CHUNK_LOAD`) doesn't build a `SignEntry`. It queries `SignManager.getKeysInChunk(...)` for
   sign keys the cache already knows about in the loading chunk, and calls `SignManager.remove(key)` for any whose
   `SignBlockEntity` is gone — see §4 below.

`SignHelper.createSignEntry` builds a `SignEntry` from both the front and back `SignText`, running each through a
`volatile` module-level `SignLinesParser` instance (`buildParser()`, first populated from
`ConfigManager.get().getMarkerGroups()` at class-init). `SignHelper.reloadParser()` rebuilds it from the current
config — called from `SignManager.reloadConfig()` (see §3) on every BlueMap reset, so a sign parsed *after*
`/bluemap reload` picks up an edited prefix/matchType rather than a stale one.

## 2. Parsing: `SignLinesParser`

`SignLinesParser`'s constructor filters `markerGroups` once, up front, via `hasValidPrefix`: a `null` prefix is
dropped (logged as a warning), and for a `REGEX`-type group the prefix is compiled once with `Pattern.compile` —
a `PatternSyntaxException` drops that group (logged) rather than surfacing later. This closed GitHub issue #139
(review finding #8): a malformed `REGEX` prefix used to throw from `line.matches(...)` deep inside `parse()`,
uncaught, blocking sign processing broadly rather than just disabling that one group. Validation happens once at
construction (i.e. once per config load/reload), not per line parsed. See `SignLinesParserTest`'s
`malformedRegexPrefixIsSkippedInsteadOfThrowing`/`nullPrefixIsSkippedInsteadOfThrowing` (`testing.md`).

A 3-state machine (`START` → `HAS_MARKER_TYPE` → `INVALID`) driven by `ParsingContext`:

- Every line is `.trim()`-ed before processing.
- In `START`: blank lines are skipped (state stays `START`). The first non-blank line is checked against every
  configured `MarkerGroup` in order (`markerGroups.stream().filter(...).findFirst()`) — **first match wins** when
  prefixes overlap (e.g. `[poi]` vs `[poi` — see `SignLinesParserTest.firstMatchingGroupWinsWhenMultipleConfigured`).
  If no group matches, state goes to `INVALID` and the final result is `SignLinesParseResult(null, "", "")`.
- Match semantics differ by `MarkerGroupMatchType`:
  - `STARTS_WITH` (default): `line.startsWith(prefix)`; label = `line.substring(prefix.length()).trim()`.
  - `REGEX`: `line.matches(prefix)` — **whole-line match**, not `find()`. This means a regex prefix can't share its
    line with label text (unlike `STARTS_WITH`, where `[poi] Town Hall` puts the label on the same line). Label
    extraction uses `line.replaceAll(prefix, "").trim()`.
- Once in `HAS_MARKER_TYPE`: every non-blank line is appended to the detail buffer (`ParsingContext.appendDetail`,
  which joins with `\n`); the *first* non-blank line becomes `label` if one hasn't been set yet. Blank lines
  between content lines are skipped without breaking the state.
- `ParsingContext.buildResult()` trims the accumulated detail buffer and returns
  `SignLinesParseResult(markerGroup.prefix(), label, detail)`.

Result: a sign can put its label on the prefix line (`[poi] Town Hall`) or on the following line — both produce
the same label/detail.

## 3. `SignManager` — the decision point

Singleton (double-checked locking), holds:
- `ConcurrentMap<SignEntryKey, SignEntry> signCache` — every known sign, keyed by position+dimension.
- `SignChunkIndex chunkIndex` — secondary lookup from chunk to the sign keys cached in it, kept in sync with
  `signCache` (populated on add, cleared on remove, wiped and rebuilt in `reloadSigns()`) purely so chunk-load
  reconciliation (§4) doesn't have to scan the whole cache. Never touched on the update branch — a `SignEntry`'s
  position is immutable once cached, only ever a different key entirely.
- `volatile RuntimeConfig runtimeConfig` — a private record `RuntimeConfig(Map<String, MarkerGroup> prefixGroupMap,
  ActionFactory actionFactory)` built by static `buildRuntimeConfig()`/`buildPrefixGroupMap()` from
  `ConfigManager.get().getMarkerGroups()` (duplicate prefixes are logged and skipped, first one wins). Bundling both
  fields into one record swapped via a single `volatile` write means a reader always sees a `prefixGroupMap` paired
  with the `actionFactory` (and its `MarkerSetIdentifierCollection`) built in the *same* reload — two separate
  `volatile` fields could let one thread observe a freshly-rebuilt `prefixGroupMap` alongside the *previous* reload's
  `actionFactory`, or vice versa. Every method that dispatches an action takes a local `var config = runtimeConfig`
  snapshot first, then reads `config.prefixGroupMap()`/`config.actionFactory()` off that same snapshot rather than
  re-reading the volatile field twice.
- One `BlueMapAPIConnector`; `SignManager` registers itself as an `IResetHandler` on it.

`addOrUpdateSign`, `removeByKey`, and `reloadSigns` are all `synchronized` on the same monitor (finding #17,
`plans/codebase-review-2026-07-11.md`, resolved 2026-07-23) — `reset()`'s snapshot-clear-replay sequence in
`reloadSigns()` (below) runs on whatever thread `BlueMapAPI.onEnable` fires on, not necessarily the server thread,
so without this a live sign edit/removal arriving from the mixins mid-replay could be clobbered by a stale
replayed value, or a sign removed mid-replay could be silently re-added from the pre-removal snapshot. `dispatch()`
only enqueues onto `ReactiveQueue` under this lock (no blocking BlueMap API work), so it doesn't add hot-path
contention the way locking around `processMarkerAction` would.

`addOrUpdateSign(signEntry)` (called for every add/update event, from entry points 1-3 above — not §1.4's chunk-load
reconciliation, which only ever calls `removeByKey` directly):

`newPrefix` (`SignEntryHelper.getPrefix`, preferring front-text prefix, falling back to back-text) `null` is handled
*before* the decision table below: if `existing != null`, dispatches a remove via `removeEntry` — this is how a sign
edited away from every configured prefix (e.g. `[poi] Town Hall` → `Town Hall`) gets its marker cleaned up, not the
table's `false` row. If `existing == null` too, it's a no-op (an unrecognized sign that was never tracked). Both
branches return immediately, skipping the table entirely.

For every other case (`newPrefix != null`), decision table on `(existing entry in cache, whether the new entry is
currently a POI-type match)`:

| existing | isPOIMarker | Action |
|----------|-------------|--------|
| `null` | `true` | **Add**: cache the entry, dispatch `AddMarkerAction` |
| non-null | `false` | **Remove**: dispatch `RemoveMarkerAction` via `removeEntry` |
| non-null | `true` | **Update** (see below) |
| `null` | `false` | no-op |

`isPOIMarker` comes from `SignEntryHelper.isMarkerType(signEntry, prefixGroupMap, POI)`, which null-guards
`prefixGroupMap.get(prefix)` — returns `false` rather than throwing `NPE` if the entry's prefix isn't in the
current map at all (an operator removed/renamed that group's prefix in a config reload since this sign was last
dispatched). Every subsequent `prefixGroupMap.get(prefix)` call used to build the actual `MarkerGroup` for a
dispatch (in the Add branch, the same-prefix Update branch, and both sides of the prefix-changed
remove-then-add branch, plus `removeByKey`) is null-guarded the same way: if the prefix isn't in the map, that
specific dispatch is skipped and a warning logged, rather than passing a `null` `MarkerGroup` into `ActionFactory`.
In `reset()`'s replay (below), `existing` is always `null` post-clear, so an entry whose prefix maps to nothing
that reload falls into the `null`/`false` no-op row (or the Add branch's own null-guard) — its marker is neither
re-added nor removed, and stays orphaned in BlueMap until the physical sign is edited or removed. See the
config-reload known limitation in `plans/marker-group-config-reload-plan.md`.

**Update branch detail:** the cache is refreshed unconditionally (note: if the incoming entry's `playerId` is
`"unknown"` — i.e. came from a chunk-load event, not a player edit — the *existing* cached `playerId` is preserved
rather than overwritten, so a chunk reload never erases the last player known to have edited a sign). Then:
- If the marker's prefix is unchanged (`existingPrefix.equals(newPrefix)`): only dispatch `UpdateMarkerAction` if
  label or detail actually changed (`isTextDifferent`) — avoids redundant BlueMap API calls on every chunk reload.
- If the prefix changed (sign text edited to match a *different* marker group): dispatch `RemoveMarkerAction` for
  the old group, then `AddMarkerAction` for the new group — a prefix change is a remove-then-add, not an in-place
  update, because the marker lives in a different `MarkerSet`.

`removeByKey(key)` looks up and removes from `signCache`; if nothing was cached for that key, or the removed
entry had no resolvable prefix, it logs and returns without dispatching anything.

`reset()` (from `IResetHandler`, called when BlueMap fires a reset — i.e. `/bluemap reload`) calls
`reloadConfig()` then `reloadSigns()`, in that order:
- `reloadConfig()`: `ConfigManager.reload()` (re-reads `BMSM-Core.json` from disk), `SignHelper.reloadParser()`,
  then replaces `runtimeConfig` wholesale via `buildRuntimeConfig()` — a freshly rebuilt `prefixGroupMap` paired
  with a brand-new `ActionFactory` backed by a new `MarkerSetIdentifierCollection` — starting that cache clean on
  every reload, paired with `BlueMapAPIConnector.resetQueue()` clearing `markerSetsCache` (see §6), means neither
  identifier cache accumulates entries keyed on a `MarkerGroup` value from before the last reload (`MarkerSetIdentifier`
  keys on the whole record by value, so a changed icon/offset/distance would otherwise be a new, never-evicted cache
  entry).
- `reloadSigns()`: snapshots the current cache, clears it, then replays every entry back through `addOrUpdateSign`
  — since `existing` is always `null` post-clear, every replayed entry takes the **Add** row above, re-dispatching
  against the just-rebuilt `prefixGroupMap`/`actionFactory` so an edited icon/offset/visibility/distance/name takes
  effect without a server restart. Full design and known limitations (group-rename leaves a duplicate marker under
  the old `MarkerSet` name; a prefix rename alone, with no in-game re-edit, isn't reclassified) are in
  `plans/marker-group-config-reload-plan.md` and `plans/marker-group-reload-followups-todo.md`.

## 4. Chunk-load reconciliation — `SignChunkKey` / `SignChunkIndex`

Addresses GitHub issue #110 (plan: `plans/chunk-load-sign-reconciliation-plan.md`). Nothing else detects a sign
that vanished while its chunk was unloaded (external region-file deletion/regen, backup restore, manual NBT
surgery) — the removal mixin (§1.3) only fires for an in-game block change on a *loaded* chunk.

- **`SignChunkKey`** (`core.signs`, plain Java, record: `parentMap, chunkX, chunkZ`) — `forEntryKey(SignEntryKey)`
  computes `Math.floorDiv(x, 16)`/`Math.floorDiv(z, 16)`, vanilla chunk granularity. Deliberately separate from
  `core.signs.persistence.SignRegionKey`'s 512-block/32-chunk region math — that's on-disk file-layout, unrelated
  to this in-memory runtime lookup.
- **`SignChunkIndex`** (`core.signs`, plain Java) — wraps `ConcurrentHashMap<SignChunkKey, Set<SignEntryKey>>`.
  `add`/`remove` keep it in sync with a key's presence in `signCache`; `remove` also drops the chunk's map entry
  once its key set empties, so long-emptied areas don't leak entries. `keysInChunk(parentMap, chunkX, chunkZ)`
  returns a snapshot list (empty if nothing tracked there) — the query the reconciliation handler uses. `clear()`
  is called alongside `signCache.clear()` in `reloadSigns()`.
- `SignManager.getKeysInChunk(parentMap, chunkX, chunkZ)` — static, delegates to `chunkIndex.keysInChunk(...)`.
  Pure data query, but `SignManager` itself stays outside unit-test coverage regardless (constructs a
  `BlueMapAPIConnector`); `SignChunkKey`/`SignChunkIndex` are unit-tested on their own
  (`SignChunkKeyTest`/`SignChunkIndexTest`, see `testing.md`).
- **`BlueMapSignMarkersMod.onChunkLoad`** (registered on `ServerChunkEvents.CHUNK_LOAD`, game-coupled, no
  automated coverage) — for each key `SignManager.getKeysInChunk` returns for the loading chunk, checks
  `chunk.getBlockEntity(new BlockPos(key.x(), key.y(), key.z())) instanceof SignBlockEntity`; if not, logs at INFO
  (an unattended removal is unusual enough to warrant visibility above the default log level) and calls
  `SignManager.remove(key)` — the same removal path §1.3's mixin uses, no new dispatch logic.
- **No special case for `generated == true`** (a chunk Minecraft reports as newly generated, no saved data found).
  That flag also covers "region file deleted externally, world regenerated it fresh" — exactly the scenario this
  feature targets — so skipping reconciliation there would defeat the main use case. No performance reason to
  skip it either: `keysInChunk` is one hashmap `get` returning empty for the overwhelming majority of chunk loads.

## 5. Marker identity — `MarkerIdentifier` / `MarkerSetIdentifier` / `MarkerSetIdentifierCollection`

- `MarkerIdentifier(x, y, z, parentSet)` — its `getId()` is `"x%d_y%d_z%d"`, the literal key used inside a BlueMap
  `MarkerSet`'s marker map. **No dimension component** — uniqueness across dimensions is guaranteed only because
  each dimension maps to a separate `MarkerSetIdentifier`/`MarkerSet`, not because the id string itself is unique.
- `MarkerSetIdentifier(mapId, markerGroup)` — one BlueMap marker-set per (map, marker-group) pair.
- `MarkerSetIdentifierCollection` is a per-`SignManager`-instance cache that guarantees the *same*
  `MarkerSetIdentifier` object is returned for a given `(mapId, markerGroup)` pair (indexed both by map and by
  marker group, intersected) — `ActionFactory` always goes through this rather than constructing
  `MarkerSetIdentifier` directly, so repeated calls for the same map+group don't fragment the connector's
  `markerSetsCache` (keyed by `MarkerSetIdentifier` equality/identity in `BlueMapAPIConnector`). `getIdentifier` is
  `synchronized` (finding #16, resolved 2026-07-22) so the "is this combo already cached?" check and the "cache it"
  write are one atomic step — `SignManager` can call it both from the server thread (live sign edits) and from
  whatever thread replays the cache on a config reload, concurrently, against the same instance; without the lock,
  concurrent first-time lookups for the same pair could each miss the cache and construct a distinct instance, and
  the plain `TreeMap`/`HashMap`/`HashSet` backing fields could corrupt under concurrent mutation.
  `MarkerSetIdentifierCollectionTest.concurrentFirstTimeCallersForTheSameComboConvergeOnOneIdentifierInstance`
  (`testing.md`) is an active (not `@Disabled`) regression test for this.

## 6. `BlueMapAPIConnector` — the only class touching the BlueMap API

- Holds a `volatile ReactiveQueue<MarkerAction> markerActionQueue`, a
  `volatile Map<MarkerSetIdentifier, List<MarkerSet>> markerSetsCache`, and a `volatile BlueMapAPI blueMapAPI`.
  All three are `volatile` because `resetQueue()`/`onEnable()` always replace them wholesale with a brand-new
  object rather than mutating the existing one, so correctness only needs a reader to see the latest *reference*
  — that's what `volatile` guarantees (it says nothing about the referenced objects, which are mutated afterward
  through their own thread-safe methods: `ReactiveQueue.enqueue()`/`process()`, `ConcurrentHashMap.get()`/
  `putIfAbsent()`). No reader (`dispatch()`/`onDisable()`/`onEnable()` for the queue, `getMarkerSets()` for the
  cache, `getMaps()` for `blueMapAPI`) ever needs a joint snapshot of more than one of these fields at once, so
  per-field visibility is enough — a shared lock would additionally serialize `dispatch()` (hot path, every sign
  event) behind `processMarkerAction()`'s BlueMap API calls, an unrelated critical section. This resolves finding
  #12 (`plans/codebase-review-2026-07-11.md`, resolved 2026-07-22) and the field-visibility half of #11.
- **Listener detach (finding #7, GitHub issue #140, resolved 2026-07-23):** the constructor registers
  `BlueMapAPI.onEnable(...)`/`onDisable(...)` with two `final Consumer<BlueMapAPI>` fields
  (`onEnableListener`/`onDisableListener` — each built once as `this::onEnable`/`this::onDisable`), and
  `shutdown()` calls `BlueMapAPI.unregisterListener(...)` with those *same* instances.
  `BlueMapAPI.unregisterListener` removes by `equals`/`hashCode`, and a bare method reference has no custom
  `equals` — two separately-evaluated `this::onEnable` expressions are distinct objects under default identity
  equality, so passing a fresh method reference to `shutdown()` would silently no-op. Confirmed against
  `bluemap-api` 2.8.0's source that `onEnable`/`onDisable` do store the `Consumer` and `unregisterListener` does
  remove it correctly once the same instance is passed both ways — this class is excluded from unit-test coverage
  (game-coupled, see `testing.md`), so this was verified by reading the dependency's source, not by a test.
- `BlueMapAPI.onEnable`/`onDisable` are registered in the constructor. `onDisable` shuts the queue down (actions
  keep enqueuing but stop draining). `onEnable(api)`: assigns `this.blueMapAPI = api` **first**, then, if the queue
  was shut down, calls `resetQueue()` (fresh queue + fresh `markerSetsCache`) and `fireReset()` (→ every registered
  `IResetHandler`, i.e. `SignManager.reset()`) before resuming draining — this is why a BlueMap reload replays the
  entire sign cache rather than assuming stale `MarkerSet` state is still valid. The `blueMapAPI` assignment must
  come before `fireReset()`, not after: `fireReset()`'s replay dispatches `MarkerAction`s that `ReactiveQueue`
  starts draining on background threads immediately (`enqueue()` calls `process()` synchronously, which submits
  to the executor right away — it doesn't wait for the enqueuing loop, let alone `onEnable`, to finish), and those
  threads read `this.blueMapAPI` in `getMaps()`. Assigning it after `fireReset()` (the pre-fix ordering) let replay
  actions race ahead and read the *previous* cycle's `blueMapAPI` reference — root cause of a bug where editing a
  marker group's config and running `/bluemap reload` made that group's markers (and its `MarkerSet` layer) vanish
  instead of updating in place, recoverable only by reloading a second time. `SignManager.reloadConfig()`'s disk
  read (see §3) made the race reliably reproducible by widening the window between replay-dispatch and the
  now-corrected assignment point.
- `getMarkerSets(identifier)` is `synchronized`; on cache miss it resolves `BlueMapAPI.getWorld(mapId)` →
  `.getMaps()`, and for each map either fetches an existing `MarkerSet` by `markerGroup.name()` or builds+registers
  one (`label`, `defaultHidden` from the `MarkerGroup`). One `MarkerSetIdentifier` can map to *multiple*
  `MarkerSet`s if a world has multiple BlueMap maps rendered for it — every dispatched action applies to all of them.
- `processMarkerAction` is now `synchronized` (finding #5, resolved 2026-07-22): `addMarker`/`updateMarker`/
  `removeMarker` mutate a `MarkerSet`'s marker `Map` (thread-safety of which is BlueMap's concern, not this mod's),
  and `ReactiveQueue`'s executor is sized to `availableProcessors()`, so without this lock two actions dispatched
  close together (e.g. many signs loading at server startup) could race on the same underlying map. Because
  `ReactiveQueue.shutdown()` only stops *new* submissions (already-submitted tasks still run — see §7), several
  such tasks can end up queued behind this monitor for a while after a shutdown is requested; `processMarkerAction`
  re-checks `BlueMapAPI.getInstance().isEmpty()` itself on entry so one of those queued tasks can't mutate a
  `MarkerSet` after BlueMap has actually disabled in the meantime. It then dispatches on the concrete `MarkerAction`
  subtype via a `switch` pattern-match; **`MarkerAction` is a plain abstract class, not `sealed`**, so adding a new
  subtype without adding a `case` here (and in `logProcessingMessage`'s switch) silently falls through to `default`
  instead of failing to compile — see `AGENTS.md`'s "Adding a new marker/BlueMap action" section.
- `addMarker` only actually builds a marker `if (markerGroup.type() == MarkerGroupType.POI)` — non-POI marker
  groups are a no-op today (only `POI` exists in `MarkerGroupType`, so this is future-proofing, not a live branch).
- **HTML escaping (fixed)**: `addMarker`/`updateMarker` wrap `detail` with `HtmlUtils.toHtmlDetail(...)` (`common`
  package) before it reaches `POIMarker.builder().detail(...)` / `poiMarker.setDetail(...)` — BlueMap renders
  `detail` as raw HTML (unlike `label`, which BlueMap's own `Marker.setLabel()` escapes), and sign text is
  player-controlled, so this closed a live XSS vector (`plans/html-detail-escaping-plan.md`). `toHtmlDetail` escapes
  first, then converts `\n` to `<br>` so multi-line detail renders line breaks correctly — escaping before the `<br>`
  substitution matters, otherwise the inserted tags would themselves get escaped. `SignEntry`/persisted `signs.json`
  data stays raw/unescaped; escaping happens only at this BlueMap-API call site.

## 7. `ReactiveQueue<T>` — generic buffer-while-unavailable primitive

Lives in `core.reactive`, not BlueMap-specific — reusable anywhere something needs to "queue while a dependency is
unavailable, drain once it's back."

- `enqueue(message)`: offers to an internal `ConcurrentLinkedQueue`, then calls `process()`.
- `process()`: bails immediately if `shutdownRequested` or `!shouldRunCallback.shouldRun()` (for
  `BlueMapAPIConnector`, `shouldRun` is `BlueMapAPI.getInstance().isPresent()`); otherwise submits `processMessages`
  to `getExecutor()`'s fixed thread pool (`Executors.newFixedThreadPool(availableProcessors())`), swallowing a
  `RejectedExecutionException` (shut down concurrently between the check and the submission — nothing more to
  schedule on a retired instance).
- `processMessages` loops while `!shutdownRequested && !queue.isEmpty() && shouldRun()` still holds, polling one
  message at a time and submitting **each individual message** as its own task to the same executor (so message
  processing itself is also concurrent, not just the drain loop) — a per-message `RejectedExecutionException`
  during a shutdown race just returns; any other exception from the submission reaches
  `messageProcessorErrorCallback`.
- `shutdown()` (finding #2 and #10, both resolved 2026-07-22) is no longer a same-thread-only best-effort call:
  under a `synchronized` block it sets a `volatile shutdownRequested` flag and calls `executor.shutdown()`
  together (paired with `getExecutor()` sharing the same monitor, so a `shutdown()` racing a lazy executor
  creation can't leave a freshly-created executor un-shut-down), then — lock released, so an in-flight task's own
  `getExecutor()` call can't deadlock against it — blocks up to `SHUTDOWN_AWAIT_SECONDS` (5) on
  `awaitTermination`, falling back to `shutdownNow()` (then one more bounded `awaitTermination`) if the timeout
  elapses. This is what lets a caller that awaits `shutdown()` returning (e.g. `BlueMapAPIConnector.onDisable`)
  rely on there being no straggler task still able to touch shared state afterward, which otherwise could run
  after a subsequent `resetQueue()`/`fireReset()` replay and clobber the state that replay just established.
- Once `shutdownRequested` is set, `getExecutor()` **never creates a replacement executor** — a shut-down queue is
  permanently retired rather than self-healing (finding #2). This is why `BlueMapAPIConnector.onEnable` has to call
  `resetQueue()` (a brand-new `ReactiveQueue` instance) rather than relying on the old one to resurrect itself.
- `executor` is `volatile` (finding #12, resolved 2026-07-22) so `isShutdown()` — callable with no lock held, from
  any thread — sees `getExecutor()`'s synchronized write without needing its own synchronization.
- A package-private constructor overload accepts an `ExecutorService` directly (the public 3-arg constructor
  delegates to it with `null`, same as before) — test-only seam so `ReactiveQueueTest` can inject a synchronous or
  failure-simulating fake executor instead of the lazily-created fixed thread pool, with no change to real
  behavior. See `testing.md` for what it covers, including a documented remaining gap: an exception thrown by the
  processor callback itself is swallowed (captured on an unawaited `Future`, never reaching
  `messageProcessorErrorCallback`) — only a submission-time failure reaches that callback.

---
*Last updated: 2026-07-23 | Verified against: 26.2-0.17.0 (3034be2)*

