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
   `SignHelper.createSignEntry(entity, WorldMap.UNKNOWN)` → `SignManager.addOrUpdate(...)`. Player id is the
   `WorldMap.UNKNOWN` (`"unknown"`) sentinel here because chunk load isn't attributable to a player — this
   constant is the single canonical source for that sentinel (ticket 08 consolidated a second, independent
   `"unknown"` literal duplicated in `SignManager`; both now reference `WorldMap.UNKNOWN`).
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
`ConfigManager.get().getMarkerGroups()` at class-init), and also captures each side's raw, unparsed lines
(`getRawLines(SignText)`) into `SignEntry.frontRawLines`/`backRawLines` — this raw text is what lets a config
reload later re-parse the sign against a changed prefix instead of trusting the parse it produced at creation time
(§3's reload self-heal). `SignHelper.reloadParser()` rebuilds the parser from the current config — called from
`SignManager.reloadConfig()` (see §3) on every BlueMap reset, so a sign parsed *after* `/bluemap reload` picks up
an edited prefix/matchType rather than a stale one.

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

`SignEntryHelper` (plain Java, `core.signs`) derives the values `SignManager` dispatches from a `SignEntry`'s
front/back `SignLinesParseResult`s: `getPrefix` prefers the front side's prefix, falling back to the back side's
(`null` if neither matched); `getLabel` likewise prefers front, falling back to back. `getDetail` merges both
sides' detail text (`"FRONT: ...%nBACK: ..."`) only when front and back **matched the same marker group** (ticket
07, `.scratch/codebase-review-followups/issues/07-fix-dual-sided-sign-semantics.md`); when they matched two
*different* groups, only the front side's detail is used — matching `getPrefix`'s front-preferred rule for which
group the marker actually belongs to. Previously `getDetail` merged both sides' text unconditionally whenever
neither was blank, so a marker could show detail text attributed to a group it didn't belong to (e.g. the back
side's group). If only one side matched anything, that side's detail is used regardless of the other's blankness.

## 3. `SignManager` — the decision point

Singleton (double-checked locking), holds:
- `ConcurrentMap<SignEntryKey, SignEntry> signCache` — every known sign, keyed by position+dimension.
- `SignChunkIndex chunkIndex` — secondary lookup from chunk to the sign keys cached in it, kept in sync with
  `signCache` (populated on add, cleared on remove) purely so chunk-load reconciliation (§4) doesn't have to scan
  the whole cache. Never touched on the update branch — a `SignEntry`'s position is immutable once cached, only
  ever a different key entirely. **Not** cleared/rebuilt on a config reload (see below) — no sign keys actually
  change during a reload, only their representation under the config.
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

`addOrUpdateSign`, `removeByKey`, and `reloadConfig` are all `synchronized` on the same monitor (finding #17,
`../plans/codebase-review-2026-07-11.md`, resolved 2026-07-23) — `reset()`'s config-swap-then-diff sequence in
`reloadConfig()` (below) runs on whatever thread `BlueMapAPI.onEnable` fires on, not necessarily the server thread,
so without this a live sign edit/removal arriving from the mixins mid-diff could be clobbered by a stale
dispatch, or a sign removed mid-diff could be silently re-added. `dispatch()`
only enqueues onto `ReactiveQueue` under this lock (no blocking BlueMap API work), so it doesn't add hot-path
contention the way locking around `processMarkerAction` would.

### Representation and the transition table (`.scratch/line-markers/spec.md` §6)

A private record `Representation(MarkerGroup group, String label, String detail)` captures what a sign currently
*is* to the marker layer: `null` means the sign matches no configured group (NONE); a non-null `Representation`
whose `group.type()` is `POI`, `LINE`, or `SHAPE` says which kind. `computeRepresentation(SignEntry, prefixGroupMap)` derives
it from `SignEntryHelper.getPrefix`/`getLabel`/`getDetail`, returning `null` if the entry has no resolvable prefix
or the prefix isn't in `prefixGroupMap` (an operator removed/renamed that group's prefix since this sign was last
dispatched — logged as a warning, not thrown).

Every sign change — edit, removal, or a config reload (§ below) — reduces to a single lookup: compute the sign's
`Representation` under the *old* state and under the *new* state, then pass the `(oldRep, newRep)` pair to
`SignTransitionResolver.computeTransitionAction(allSigns, key, oldRep, newRep, actionFactory)`. This replaced the
old `(existing entry, isPOIMarker)` 2x2 table entirely — the old table had no way to express a POI↔LINE type flip
or route a change through anything but position-keyed add/remove. The transition table itself lives in
`SignTransitionResolver` (a static-utility class, plain Java, `core.signs`) rather than on `SignManager` —
`SignManager` can't be unit tested directly (its constructor builds a `BlueMapAPIConnector`), but
`SignTransitionResolver` has no Minecraft/Fabric/BlueMap types in its signature, so extracting it makes the
transition logic directly testable (`SignTransitionResolverTest`, see `testing.md`). Callers (`SignManager`) pass
their own `getAllSigns()` snapshot in as the `allSigns` parameter rather than the resolver fetching it itself, so a
single already-taken snapshot can be reused across every sign's diff during a config reload (§ below) instead of
re-querying the cache once per sign.

`computeTransitionAction`'s logic (mirrors the table in the spec, generalized to a third `SHAPE` row/column
alongside `POI`/`LINE`):

| Old ＼ New | NONE | POI | LINE | SHAPE |
|---|---|---|---|---|
| **NONE** | no-op (`null`) | dispatch `createAddPOIAction` | `lineJoinAction` (recompute group *including* this sign; dispatch `SetLineMarkerAction` if ≥2 members, else no-op) | `shapeJoinAction` (same shape, gated on `SHAPE_MIN_MEMBERS = 3` instead of 2) |
| **POI** | dispatch `createRemovePOIAction` | same group (prefix unchanged): label/detail both unchanged is a no-op, either changed dispatches `createUpdatePOIAction`. Different group (prefix changed): leave-effect + join-effect, bundled | leave-effect (Remove POI) + join-effect (`lineJoinAction`) | leave-effect (Remove POI) + join-effect (`shapeJoinAction`) |
| **LINE** | `lineLeaveAction` (recompute group *excluding* this sign; `SetLineMarkerAction` if ≥2 remain, `RemoveLineMarkerAction` if exactly 1 remains, no-op if 0) | leave-effect (as above) + join-effect (Add POI) | same group+label: `lineJoinAction` with `sameGroupRecompute=true` (refreshes detail/points — always dispatches `Set` since ≥2 members necessarily already existed); different group/label: leave-effect + join-effect | leave-effect (`lineLeaveAction`) + join-effect (`shapeJoinAction`) |
| **SHAPE** | `shapeLeaveAction` (mirrors `lineLeaveAction`, but `RemoveShapeMarkerAction` once membership drops below 3) | leave-effect (`shapeLeaveAction`) + join-effect (Add POI) | leave-effect (`shapeLeaveAction`) + join-effect (`lineJoinAction`) | same group+label: `shapeJoinAction` with `sameGroupRecompute=true` (mirrors `LINE`/`LINE`); different group/label: leave-effect + join-effect |

The POI/POI cell deliberately compares only `group().prefix()`, not label — a label-only edit on an unchanged group
used to fall through to the different-group (leave+join) branch, because the older check compared
`sameGroupAndLabel` (group *and* label) before deciding whether to update-in-place; that made an ordinary label
edit dispatch a bundled remove+add instead of a single `UpdateMarkerAction`. The LINE/LINE and SHAPE/SHAPE cells
both use `sameGroupAndLabel(a, b)` (compares `group().prefix()` and `label()`) — the same-group-recompute shortcut
condition is `oldType == newType && oldType != MarkerGroupType.POI && sameGroupAndLabel(...)`, generalized to cover
either non-`POI` type rather than naming `LINE` explicitly, since a `LINE`/`SHAPE` group's identity (and marker id,
§5) is keyed on group+label either way. When a transition needs both a leave-effect and a join-effect (a
group/label/type change, for any pair of the three types), each effect is computed independently (`null` if that
half is a no-op, e.g. leaving a `LINE` group that still has ≥2 members after removal dispatches a `Set`, not a
leave at all) and both are collected into a `List<MarkerAction>`: zero effects → `null`, one effect → dispatch it
directly, two → bundle into a `GroupTransitionMarkerAction` (see §5/§6) so `ReactiveQueue`'s lack of
message-ordering guarantees (§7) can't transiently show a sign in two places. `groupIdentityObsolete` (used by the
config-reload path, below) is likewise type-agnostic, detecting a config-only type flip or rename for any of the
three types.

`lineJoinAction(allSigns, parentMap, rep, actionFactory, sameGroupRecompute)` and `lineLeaveAction(allSigns,
parentMap, rep, actionFactory)` both call `LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(),
rep.label())` against the caller-supplied snapshot — because `signCache` is a full, live snapshot (never cleared
for a config reload, see below), a snapshot taken once per dispatch always scans every sign currently known, so a
`LINE` group recompute is always complete and order-independent regardless of which member triggered it.
`lineJoinAction`'s `isFirstAppearance` flag (`!sameGroupRecompute && members.size() == 2`) is log-only — see §6 —
not a distinct dispatch type. `shapeJoinAction`/`shapeLeaveAction` are the direct `SHAPE` counterparts: they call
`ShapeGroupResolver.members(...)`, which itself just delegates to `LineGroupResolver.members(...)` (identical
filtering/ordering — the only real difference between `LINE` and `SHAPE` group resolution is the caller-side
minimum-member count, `2` vs. `SHAPE_MIN_MEMBERS = 3`), and dispatch via `actionFactory.createSetShapeAction`/
`createRemoveShapeAction` instead of the line equivalents.

`addOrUpdateSign(signEntry)` (called for every add/update event, from entry points 1-3 above — not §1.4's chunk-load
reconciliation, which only ever calls `removeByKey` directly): first runs the incoming `signEntry` through
`safeReparseFromRawLines(signEntry, config.parser())` (the same reparse helper reload uses, §3) — this matters at
server startup, where `SignProvider.loadSigns` (`config-and-persistence.md`) replays every persisted `V5` entry
through this method, so a sign whose prefix was renamed while the server was offline is still reclassified
correctly rather than dispatched under a stale cached parse. Then looks up `existing` from `signCache`, computes
`oldRep`/`newRep` from `existing`/the reparsed `signEntry` respectively, updates `signCache`/`chunkIndex` (removing the key if
`newRep == null` and something was cached, else caching the merged entry — the merge preserves the *existing*
cached `playerId` when the incoming entry's is the `WorldMap.UNKNOWN` chunk-load sentinel, and preserves the
existing entry's `createdAtMillis` rather than ever recomputing it), then dispatches whatever
`computeTransitionAction` returns (if non-`null`), passing a fresh `getAllSigns()` snapshot as `allSigns`.

`removeByKey(key)` removes from `signCache`/`chunkIndex`; if nothing was cached for that key, it logs and returns.
Otherwise it computes `oldRep` from the removed entry, `computeTransitionAction(getAllSigns(), key, oldRep, null,
...)`, and dispatches the result — the same NONE-target column of the table above, so removal reuses no separate
code path.

### Config reload (`/bluemap reload`) — `reset()`/`reloadConfig()`

`reset()` (from `IResetHandler`) calls `reloadConfig()`, which:
1. Captures `oldPrefixGroupMap = runtimeConfig.prefixGroupMap()` **before** touching anything else.
2. `ConfigManager.reload()` (re-reads `BMSM-Core.json` from disk), `SignHelper.reloadParser()`, then replaces
   `runtimeConfig` wholesale via `buildRuntimeConfig()` — a freshly rebuilt `prefixGroupMap` paired with a
   brand-new `ActionFactory` backed by a new `MarkerSetIdentifierCollection` — and calls
   `blueMapAPIConnector.clearMarkerSetsCache()` (see §6), so neither identifier cache accumulates entries keyed on
   a `MarkerGroup` value from before the last reload (`MarkerSetIdentifier` keys on the whole record by value, so
   a changed icon/offset/distance would otherwise be a new, never-evicted cache entry).
3. Before diffing, every currently-cached `SignEntry` is passed through `safeReparseFromRawLines(entry,
   newConfig.parser())` (a static, log-and-fall-back wrapper around `reparseFromRawLines`) and, if the result
   differs from the original reference, the reparsed entry replaces it in `signCache`. `reparseFromRawLines`
   re-runs `SignLinesParser.parse(...)` on the entry's persisted `frontRawLines`/`backRawLines` against the new
   config and returns `entry.withParsedText(freshFront, freshBack)` — or the *same* entry reference, cheaply
   detectable via `!=`, if either raw-lines array is `null` (an entry migrated from pre-V5 data, with no raw text
   on disk to re-parse; see `config-and-persistence.md`).
4. Takes one `getAllSigns()` snapshot (`allSigns`) up front, then for every currently-cached `SignEntry` (the cache
   is **not** cleared): computes `oldRep` under `oldPrefixGroupMap` from the sign's representation as cached
   *before* reload, `newRep` under the just-rebuilt `prefixGroupMap` from the (possibly reparsed, step 3) entry,
   and dispatches `computeTransitionAction(allSigns, entry.key(), oldRep, newRep, ...)` if non-`null` — the exact
   same transition table a live sign edit uses, just fed a before/after diff against the *config* instead of the
   *sign text*. Reusing one snapshot across the whole loop (rather than each sign's `LINE`/`SHAPE`-group recompute
   re-querying `getAllSigns()` independently) avoids an O(n²) re-scan of the cache when a config reload touches
   many signs at once.

This replaced the previous behavior (`reloadSigns()`: snapshot the cache, clear it, replay every entry through
`addOrUpdateSign` so every entry always took the Add branch) for a concrete bug fix documented in
`.scratch/codebase-review-followups/issues/10-reload-clear-and-replay-orphans-markers-on-id-scheme-change.md` and
`.scratch/line-markers/issues/07-config-reload-fix-id-scheme-change.md`: a replayed "add" only ever puts a marker
under the *new* id, it never explicitly removes an old one. That was silently safe only because a POI marker's id
is always the position-based `x_y_z`, unchanged across reloads. A `LINE` marker's id (`"line:" + label`) is
content-keyed, not position-keyed — so the first config change that flips a group's `type` between `POI` and
`LINE` (same signs, only the config changed) left the old id's marker entry behind in BlueMap's `MarkerSet` map
forever, since `clearMarkerSetsCache()` only evicts this mod's own `MarkerSetIdentifier`→`MarkerSet` lookup cache,
not the marker entries inside a `MarkerSet` BlueMap itself owns. Diffing old-vs-new representation and running it
through the transition table dispatches an explicit leave-effect whenever the id scheme changes, closing that gap
for both `LINE` groups and any future non-position-keyed marker type. A previously open limitation — a prefix
*rename* alone (no in-game re-edit) wasn't reclassified, since reload re-resolved already-parsed prefixes against
the new config rather than re-parsing sign text (`../plans/marker-group-config-reload-plan.md`) — is now fixed by
the `frontRawLines`/`backRawLines` self-heal in step 3 above: an entry with raw text on disk gets fully re-parsed
against the new config every reload, so a `REGEX` prefix edit correctly reclassifies (or drops) it without a
manual re-edit or restart. Entries migrated from pre-V5 data (raw lines `null`) still fall back to the old
diff-cached-parse-as-is behavior, since there's no raw text to re-parse — this only self-heals going forward. See
`README.md`'s "Troubleshooting" section for the end-user framing and `config-and-persistence.md` for the `V5`
persistence shape.

## 4. Chunk-load reconciliation — `SignChunkKey` / `SignChunkIndex`

Addresses GitHub issue #110 (plan: `../plans/chunk-load-sign-reconciliation-plan.md`). Nothing else detects a sign
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
  is available but no longer called on a config reload (§3) — `signCache`/`chunkIndex` are never cleared for a
  reload since no sign keys change, only their computed representation.
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

## 5. Marker identity — `DispatchedMarkerIdentifier` / `MarkerIdentifier` / `LineMarkerIdentifier` / `MarkerSetIdentifier` / `MarkerSetIdentifierCollection`

Two id schemes now exist side by side, unified behind one interface:

- `DispatchedMarkerIdentifier` (`core.markers`) — `{ MarkerSetIdentifier parentSet(); String getId(); }`. `MarkerAction`
  holds one of these (widened from the concrete `MarkerIdentifier` it used to hold) so a single dispatch hierarchy
  covers both id schemes below.
- `MarkerIdentifier(x, y, z, parentSet)` implements it — its `getId()` is `"x%d_y%d_z%d"`, **position-keyed**, the
  literal key used inside a BlueMap `MarkerSet`'s marker map for POI markers. **No dimension component** —
  uniqueness across dimensions is guaranteed only because each dimension maps to a separate
  `MarkerSetIdentifier`/`MarkerSet`, not because the id string itself is unique.
- `LineMarkerIdentifier(label, parentSet)` (record) also implements it — its `getId()` is `"line:" + label`,
  **content-keyed**, not position-keyed: a line's points move as members join/leave, but its id stays stable as
  long as the (group, label) key doesn't change. This is exactly the id-scheme difference that motivated
  `SignManager.reloadConfig()`'s rewrite (§3) — a naive replay only ever adds under a marker's *current* id, so an
  id scheme changing between reloads (e.g. a group's `type` flipping `POI`↔`LINE`↔`SHAPE`) needs an explicit
  dispatch removing the *old* id, not just adding the new one.
- `ShapeMarkerIdentifier(label, parentSet)` (record) is the `SHAPE`-type counterpart, structurally identical to
  `LineMarkerIdentifier` — same two fields, same interface — with `getId()` returning `"shape:" + label` instead.
- `MarkerSetIdentifier(mapId, markerGroup)` — one BlueMap marker-set per (map, marker-group) pair, unchanged by the
  line-markers work.
- `ActionFactory.createChangeGroupPOIAction(x, y, z, mapId, label, detail, oldMarkerGroup, newMarkerGroup)` now
  builds a `GroupTransitionMarkerAction` wrapping `List.of(new RemoveMarkerAction(oldIdentifier), new
  AddMarkerAction(newIdentifier, label, detail))` — two full `MarkerAction`s, not the older single action carrying
  two identifiers. `createSetLineAction(mapId, markerGroup, label, detail, points, isFirstAppearance)` and
  `createRemoveLineAction(mapId, markerGroup, label)` are the `LINE`-side counterparts, following the same
  `MarkerSetIdentifierCollection.getIdentifier` pattern as every other factory method; `createSetLineAction` builds
  a `LineMarkerIdentifier(label, ...)` and carries `markerGroup.lineWidth()`/`lineColor()` through unchanged.
  `createSetShapeAction(mapId, markerGroup, label, detail, points, isFirstAppearance)`/`createRemoveShapeAction(mapId,
  markerGroup, label)` mirror those two exactly but build a `ShapeMarkerIdentifier` and additionally carry
  `markerGroup.fillColor()` into `SetShapeMarkerAction`.
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
  `volatile Map<MarkerSetIdentifier, List<MarkerSet>> markerSetsCache`, a
  `volatile Map<String, RenderMaskEvaluator.RenderMask> renderMaskCache` (keyed by real `BlueMapMap` id — see §8),
  and a `volatile BlueMapAPI blueMapAPI`. All four are `volatile` because `resetQueue()`/`onEnable()`/
  `clearMarkerSetsCache()` always replace them wholesale with a brand-new object rather than mutating the existing
  one, so correctness only needs a reader to see the latest *reference*
  — that's what `volatile` guarantees (it says nothing about the referenced objects, which are mutated afterward
  through their own thread-safe methods: `ReactiveQueue.enqueue()`/`process()`, `ConcurrentHashMap.get()`/
  `putIfAbsent()`/`computeIfAbsent()`). No reader (`dispatch()`/`onDisable()`/`onEnable()` for the queue,
  `getMarkerSets()` for the marker-set cache, `getRenderMask()` for the render-mask cache, `getMaps()` for
  `blueMapAPI`) ever needs a joint snapshot of more than one of these fields at once, so per-field visibility is
  enough — a shared lock would additionally serialize `dispatch()` (hot path, every sign event) behind
  `processMarkerAction()`'s BlueMap API calls, an unrelated critical section. This resolves finding #12
  (`../plans/codebase-review-2026-07-11.md`, resolved 2026-07-22) and the field-visibility half of #11.
  `renderMaskCache` is invalidated (replaced with a fresh empty map) at the exact same call sites as
  `markerSetsCache` — `resetQueue()` and `clearMarkerSetsCache()` — so neither cache survives a config reload or a
  genuine BlueMap disable/enable cycle carrying stale entries.
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
- **Startup sign-load bugfix:** `onEnable`/`onDisable` used to gate the reload-vs-first-boot decision on
  `markerActionQueue.isShutdown()`, but a brand-new `ReactiveQueue` whose executor was never lazily created also
  reports `isShutdown() == true` — exactly what happens at server startup, when `SERVER_STARTING` dispatches an
  action for every migrated/loaded sign before BlueMap is available: `process()` returns early (`shouldRun()` is
  `false`) without ever creating an executor. That made the *first* `onEnable()` a server ever sees mistake startup
  for a reload, call `resetQueue()`, and discard every action enqueued during sign load before a single one was
  processed. Fixed with an explicit `volatile boolean disabledSinceLastEnable` field: `onDisable()` sets it `true`;
  `onEnable()` only treats the cycle as a genuine reload (and calls `resetQueue()`/`fireReset()`) `if
  (disabledSinceLastEnable)`, resetting the flag to `false` immediately after. A freshly constructed connector
  starts with the flag `false`, so the very first `onEnable()` always resumes draining the queue that startup
  already populated instead of replacing it.
- `BlueMapAPI.onEnable`/`onDisable` are registered in the constructor. `onDisable` shuts the queue down (actions
  keep enqueuing but stop draining) and sets `disabledSinceLastEnable = true`. `onEnable(api)`: assigns
  `this.blueMapAPI = api` **first**, then, if `disabledSinceLastEnable`, calls `resetQueue()` (fresh queue + fresh
  `markerSetsCache`) and `fireReset()` (→ every registered
  `IResetHandler`, i.e. `SignManager.reset()`) before resuming draining — this is why a BlueMap reload re-diffs the
  entire sign cache against the reloaded config rather than assuming stale `MarkerSet` state is still valid. The `blueMapAPI` assignment must
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
  The `markerSetsCache.putIfAbsent(...)`/debug-log call now happens **once, after** the per-map `forEach` loop
  (ticket 06) rather than repeated inside it against the same `markerSetsToReturn` list reference — the old
  placement was correct only by relying on the implicit invariant that `putIfAbsent` against the same key/list is
  a no-op on every iteration after the first, fragile to a future refactor breaking it silently.
- `logProcessingMessage` sanitizes sign-derived detail text via `LogUtils.sanitizeForLog` (`common`, ticket 06)
  before logging it at INFO — strips ANSI CSI escape sequences and normalizes `\r\n`/`\n`/`\r` to literal `\n`
  and `\r`, closing a log-injection/log-noise vector where only `\n` was previously escaped.
- `processMarkerAction` is `synchronized` (finding #5, resolved 2026-07-22): `addMarker`/`updateMarker`/
  `removeMarker`/`setLineMarker` mutate a `MarkerSet`'s marker `Map` (thread-safety of which is BlueMap's concern,
  not this mod's), and `ReactiveQueue`'s executor is sized to `availableProcessors()`, so without this lock two
  actions dispatched close together (e.g. many signs loading at server startup) could race on the same underlying
  map. Because `ReactiveQueue.shutdown()` only stops *new* submissions (already-submitted tasks still run — see
  §7), several such tasks can end up queued behind this monitor for a while after a shutdown is requested;
  `processMarkerAction` re-checks `BlueMapAPI.getInstance().isEmpty()` itself on entry so one of those queued tasks
  can't mutate a `MarkerSet` after BlueMap has actually disabled in the meantime. If the dispatched action is a
  `GroupTransitionMarkerAction`, it iterates `transitionAction.effects()` (a `List<MarkerAction>`, 0-2 entries —
  see §3/§5) calling `applySingleAction` on each **inside** the same synchronized call, so a bundled leave+join
  pair (or POI↔LINE swap) can never be observed half-applied by another thread; otherwise it calls
  `applySingleAction` directly on the one action. `applySingleAction` logs (`logProcessingMessage`) then dispatches
  on the concrete `MarkerAction` subtype via a `switch` pattern-match: `AddMarkerAction`/`RemoveMarkerAction`/
  `UpdateMarkerAction`/`SetLineMarkerAction`/`RemoveLineMarkerAction` each have a `case` arm — **`MarkerAction` is a
  plain abstract class, not `sealed`**, so adding a new subtype without adding a `case` here (and in
  `logProcessingMessage`'s switch) silently falls through to `default` instead of failing to compile — see
  `AGENTS.md`'s "Adding a new marker/BlueMap action" section. All five cases resolve their marker sets via a shared
  `applyToMarkerSets(markerIdentifier, consumer)` helper (parameter type `DispatchedMarkerIdentifier`, needs only
  `.parentSet()` — looks up via `getMarkerSets`, no-ops with a debug log if none found, otherwise hands the
  consumer a `Stream<Map<String, Marker>>`); `RemoveMarkerAction`, `RemoveLineMarkerAction`, and
  `RemoveShapeMarkerAction` all route through an id-based `removeMarkerById(String id, Stream<...>)` helper
  extracted out of the old `removeMarker` body. `SetShapeMarkerAction`/`RemoveShapeMarkerAction` have their own
  `case` arms alongside the line ones (both in `processMarkerAction`'s switch and in `logProcessingMessage`).
- `setLineMarker(SetLineMarkerAction, Stream<Map<String, Marker>>)` builds/replaces a BlueMap `LineMarker`: bails
  (defensively — `SignManager` should never dispatch below 2 points) if `action.getPoints().size() < 2`, otherwise
  builds a `de.bluecolored.bluemap.api.math.Line` from the action's `LinePoint`s (via `Vector3d`), parses
  `action.getLineColor()` through `ColorUtils.parseHex` (`common`, plain Java) into `de.bluecolored.bluemap.api.math.Color`,
  and `put`s a `LineMarker.builder().label(...).detail(HtmlUtils.toHtmlDetail(...)).line(line).lineWidth(...).lineColor(...).build()`
  into each marker set's map, keyed by `action.getMarkerIdentifier().getId()` (the content-keyed `"line:" + label`
  id, §5). `ColorUtils.parseHex` is the only conversion point from the persisted hex string to a real color object,
  mirroring how `HtmlUtils` is the only conversion point for HTML-escaped `detail` — both convert at the BlueMap-API
  call site, keeping the rest of the pipeline in plain-Java, unescaped/unconverted form.
- `setShapeMarker(SetShapeMarkerAction, Stream<Map<String, Marker>>)` is the `SHAPE` counterpart: bails
  (defensively, mirroring `setLineMarker`) if `action.getPoints().size() < 3`, builds a 2D `Shape` footprint from
  the points' `x`/`z` only, and takes the marker's rendered height from the **tallest member**:
  `points.stream().mapToInt(LinePoint::y).max()`. Parses both `lineColor` and `fillColor` through `ColorUtils.parseHex`
  (fill defaults to a translucent red, `#FF000033`, unlike `lineColor`'s opaque default — see
  `config-and-persistence.md`), then `put`s a `ShapeMarker.builder().label(...).detail(...).shape(shape,
  height).lineWidth(...).lineColor(...).fillColor(...).build()` into each marker set's map, keyed by
  `action.getMarkerIdentifier().getId()` (the content-keyed `"shape:" + label` id, §5).
- `addMarker` only actually builds a marker `if (markerGroup.type() == MarkerGroupType.POI)` — this is a real,
  live branch now that `MarkerGroupType.LINE`/`SHAPE` exist (no longer future-proofing for values that didn't
  exist): a `LINE`- or `SHAPE`-typed group's signs never reach `addMarker` at all, since `SignManager`'s transition
  table (§3) routes those representations to their own `Set`/`Remove` actions instead of `AddMarkerAction`.
- **HTML escaping (fixed)**: `addMarker`/`updateMarker` wrap `detail` with `HtmlUtils.toHtmlDetail(...)` (`common`
  package) before it reaches `POIMarker.builder().detail(...)` / `poiMarker.setDetail(...)` — BlueMap renders
  `detail` as raw HTML (unlike `label`, which BlueMap's own `Marker.setLabel()` escapes), and sign text is
  player-controlled, so this closed a live XSS vector (`../plans/html-detail-escaping-plan.md`). `toHtmlDetail` escapes
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
- **No ordering guarantee between messages** (investigated for ticket 09,
  `.scratch/codebase-review-followups/issues/09-reactivequeue-message-ordering.md`, confirmed): because each
  message becomes its own independent executor task, once the fixed thread pool has more than one worker thread
  there's no guarantee message N finishes — or even starts — before message N+1 submitted right after it.
  `ReactiveQueueTest.reactiveQueueGivesNoOrderingGuaranteeBetweenIndependentlySubmittedMessages` reproduces this
  deterministically (blocks the first message's processing, shows the second can complete first on a 2-thread
  pool). This is left as-is rather than changed — `ReactiveQueue` is a generic reusable primitive with no
  ordering requirement of its own; a caller needing two dispatches to apply in order must bundle them into a
  single message instead (see `BlueMapAPIConnector`'s `GroupTransitionMarkerAction`/`applySingleAction`, §5/§6, and
  §3's transition-table section, for the places this mod actually needs that guarantee).
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

## 8. Per-map render-bounds gating — `core.bounds.RenderMaskEvaluator`

High-level design and rationale: `../plans/map-bounds-filtering-plan.md` and `AGENTS.md`'s "Per-map render-bounds
gating" section. This section covers the code-level mechanics.

- **`RenderMaskEvaluator`** (plain Java, no Minecraft/Fabric/BlueMap types) hand-parses BlueMap's own
  `config/bluemap/maps/<id>.conf` — no HOCON library is used. `stripComments` removes `#`-led text line-by-line;
  regex constants (`RENDER_MASK_KEY`, `SHAPE_KEY`, `TYPE_PATTERN`, `FIELD_PATTERN`) locate the `render-mask:` array
  and each `{...}` shape entry (`findMatchingBracket` for bracket-matching, `splitObjectChunks` for
  brace-depth-aware splitting on commas); `extractFields`/`extractType` pull a shape's fields into a
  `Map<String, String>` (type defaults to `"box"` if omitted). `FIELD_PATTERN` allows an optional matching pair of
  `"` around a numeric/boolean literal (fixed so `subtract: "true"` parses identically to `subtract: true`, rather
  than silently falling back to the field's default because the quoted form went unmatched).
- Two entry points: `isInsideRenderBounds(mapId, mapsConfigDir, x, y, z)` (one-shot convenience) and
  `load(mapId, mapsConfigDir)` → `RenderMask` (a small class wrapping `List<RenderMaskShape>` with one method,
  `contains(x, y, z)`) — `BlueMapAPIConnector` always uses `load`, since it tests many points against the same
  map's mask over the connector's lifetime (see below) and reparsing the config file per point would be wasteful.
- **Evaluation is a last-entry-wins reverse scan**, matching BlueMap's own `CombinedMask`: walks `shapes` from the
  last-defined entry backward; the first (i.e. last-defined) shape whose `contains(x, y, z)` is `true` decides the
  result via `!shape.subtract()`. If no shape matches, the result is `shapes.get(0).subtract()` (an empty shape
  list — no `render-mask` key, or every failure path below — short-circuits to `true`, i.e. unbounded).
- **Every shape type** (`RenderMaskBox`, `RenderMaskCircle`, `RenderMaskEllipse`, `RenderMaskPolygon`, all records
  in `core.bounds` implementing `RenderMaskShape { contains(x,y,z); subtract(); }`) carries its own `boolean
  subtract` field, parsed once via `Boolean.parseBoolean(fields.getOrDefault("subtract", "false"))` — the evaluator
  itself is shape-agnostic about `subtract`, it just negates the match. `RenderMaskBox` is an axis-aligned min/max
  range check on x/y/z; `RenderMaskCircle`/`RenderMaskEllipse` check a y-range then a (normalized, for ellipse)
  radius check on x/z; `RenderMaskPolygon` checks a y-range then does XZ-plane ray-casting (handles non-convex
  shapes) against its `List<RenderMaskPoint>` vertices.
- **Fails open** (mask treated as unbounded — every point passes) on every failure path, all funneling to an empty
  shape list: no `config/bluemap/maps/` directory, or no file matching the sanitized map id (`findConfigFile`
  returns `null`); an `IOException` reading the matched file; a `RuntimeException` (malformed/unbalanced config,
  unrecognized shape type) while parsing it. Each of these logs a warning before returning the empty list — a
  broken render-mask config degrades to "no filtering" rather than crashing marker dispatch (see
  `project_no_server_crashes` guidance).
- **`BlueMapAPIConnector` integration**: `getRenderMask(mapId)` is `renderMaskCache.computeIfAbsent(mapId, id ->
  RenderMaskEvaluator.load(...))` — one parse per real map id, cached for the connector's lifetime until
  invalidated (see §6). `MappedMarkerSet(String mapId, MarkerSet markerSet)` is a private record pairing a cached
  `MarkerSet` with the real map id it came from, since gating needs the id to look up that map's `RenderMask` but
  the pre-existing `markerSetsCache` only stored bare `MarkerSet`s.
- **Gated vs. unconditional dispatch**: `prepareSingleAction` routes `AddMarkerAction`/`UpdateMarkerAction`
  (single point, wrapped via a `pointOf(MarkerIdentifier)` helper into a one-element point list) and
  `SetLineMarkerAction`/`SetShapeMarkerAction` (their own multi-point `getPoints()`) through
  `prepareGated(identifier, points, effect)`. For each target `MappedMarkerSet`, `isInsideRenderBounds`
  is `points.stream().anyMatch(p -> mask.contains(...))` — **any one point in bounds passes the whole marker**
  (all-or-nothing for `LINE`/`SHAPE`, no per-point clipping). On a pass, `effect.accept(markers)` runs as normal;
  on a gate failure, the marker id is actively removed from that map's set (`markers.remove(identifier.getId())`)
  instead of merely skipping the add/update — this is what sweeps a marker that already existed on a
  now-out-of-bounds map before this feature shipped, reusing `SignManager.reset()`'s existing reload-forced
  re-dispatch of every sign (§3) as the trigger. `RemoveMarkerAction`/`RemoveLineMarkerAction`/
  `RemoveShapeMarkerAction` instead go through `prepareUngated(identifier, effect)` — unconditional, no
  gating, since an explicit removal means the sign's representation is genuinely leaving, independent of bounds.
- **Cold-load-off-the-lock fix**: `prepareGated`/`prepareUngated` return a `Runnable` that performs only the
  marker-set mutation; `prepareGated` computes each target map's gate decision
  (`markerSets.get().stream().map(mapped -> Map.entry(mapped, isInsideRenderBounds(...))).toList()`) up front, before
  that `Runnable` is built, so the returned `Runnable` only iterates the already-computed decisions. `applySingleAction`
  calls `prepareSingleAction` to get that `Runnable`, then runs it inside a `synchronized (this)` block. Previously
  the render-mask lookup (a cold `RenderMaskEvaluator.load` call reads and parses a config file off disk) ran
  *inside* that same lock, so the first dispatch to a given map after a cache invalidation could block every other
  in-flight `processMarkerAction` call on disk I/O unrelated to their own map. `getMarkerSets`/`processMarkerAction`
  themselves are otherwise unchanged — the fix is localized to `prepareGated`.

---
*Last updated: 2026-08-22 | Verified against: feature/tpwalke2/67-map-bounds (217c17f)*

