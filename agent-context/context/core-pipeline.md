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
  `markerSetsCache` (keyed by `MarkerSetIdentifier` equality/identity in `BlueMapAPIConnector`).

## 6. `BlueMapAPIConnector` — the only class touching the BlueMap API

- Holds a `ReactiveQueue<MarkerAction>` (see below) and a `markerSetsCache: Map<MarkerSetIdentifier, List<MarkerSet>>`.
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
  now-corrected assignment point. This mitigates finding #12 in `plans/codebase-review-2026-07-11.md` (no
  `volatile` on `blueMapAPI`, so the JMM still doesn't *guarantee* visibility across threads) but doesn't close it
  outright, and doesn't touch findings #10/#11 (stale-executor-generation stragglers, unsynchronized field writes
  racing `getMarkerSets()`'s lock) — those are a different race shape, still open.
- `getMarkerSets(identifier)` is `synchronized`; on cache miss it resolves `BlueMapAPI.getWorld(mapId)` →
  `.getMaps()`, and for each map either fetches an existing `MarkerSet` by `markerGroup.name()` or builds+registers
  one (`label`, `defaultHidden` from the `MarkerGroup`). One `MarkerSetIdentifier` can map to *multiple*
  `MarkerSet`s if a world has multiple BlueMap maps rendered for it — every dispatched action applies to all of them.
- `processMarkerAction` dispatches on the concrete `MarkerAction` subtype via a `switch` pattern-match; **`MarkerAction`
  is a plain abstract class, not `sealed`**, so adding a new subtype without adding a `case` here (and in
  `logProcessingMessage`'s switch) silently falls through to `default` instead of failing to compile — see
  `AGENTS.md`'s "Adding a new marker/BlueMap action" section.
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
- `process()`: if `shouldRunCallback.shouldRun()` (for `BlueMapAPIConnector`, this is `BlueMapAPI.getInstance().isPresent()`),
  submits `processMessages` to a fixed thread pool (`Executors.newFixedThreadPool(availableProcessors())`).
- `processMessages` loops while the queue is non-empty *and* `shouldRun()` still holds, polling one message at a
  time and submitting **each individual message** as its own task to the same executor (so message processing
  itself is also concurrent, not just the drain loop).
- `shutdown()`/`isShutdown()` wrap the executor; `getExecutor()` lazily recreates the executor if the current one
  is null or shut down — this is what lets `BlueMapAPIConnector.onEnable` transparently get a fresh executor after
  a prior `onDisable` shut the old one down.
- A package-private constructor overload accepts an `ExecutorService` directly (the public 3-arg constructor
  delegates to it with `null`, same as before) — test-only seam so `ReactiveQueueTest` can inject a synchronous or
  failure-simulating fake executor instead of the lazily-created fixed thread pool, with no change to real
  behavior. See `testing.md` for what it covers, including a documented gap: an exception thrown by the processor
  callback itself is swallowed (captured on an unawaited `Future`, never reaching `messageProcessorErrorCallback`)
  — only a submission-time failure reaches that callback.

---
*Last updated: 2026-07-21 | Verified against: 26.2-0.17.0 (72d4280)*

