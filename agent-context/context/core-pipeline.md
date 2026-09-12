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
   `SignHelper.createSignEntry(entity, PlayerIds.UNKNOWN)` → `SignManager.addOrUpdate(...)`. Player id is the
   `PlayerIds.UNKNOWN` (`"unknown"`) sentinel here because chunk load isn't attributable to a player. `PlayerIds`
   (`core.signs`) is the single canonical source for that sentinel — deliberately a separate constant from
   `WorldMap.UNKNOWN` (the no-dimension-known sentinel), even though both currently hold `"unknown"`, since
   `PlayerIds.UNKNOWN` is compared/persisted as a real `playerId` and must keep that exact value regardless of
   what `WorldMap.UNKNOWN` does.
3. **Mixins** (`src/main/resources/bluemapsignmarkers.mixins.json`, server-only, `JAVA_21` compat level):
   - `SignBlockEntityInject` injects `SignBlockEntity.updateSignText` at `HEAD` (sets a `@Unique` guard flag,
     `bluemapsignmarkers$inUpdateSignText`) and at `TAIL` (clears the flag) → a player edited a sign →
     `SignManager.addOrUpdate(SignHelper.createSignEntry(this, player.getStringUUID()))`. The same mixin also
     injects `SignBlockEntity.updateText(UnaryOperator<SignText>, boolean)` at `RETURN`, gated on a `true` return
     value and on the guard flag being `false` — `updateSignText` calls `updateText` internally, so without the
     guard a plain text edit would dispatch twice (harmless but wasteful). This second hook is what makes
     dyeing/glowing/un-glowing an already-placed sign (right-clicking it with a dye, ink sac, or glow ink sac —
     none of which call `updateSignText`) visible to the mod at all: `SignManager.addOrUpdate(SignHelper.createSignEntry(this,
     PlayerIds.UNKNOWN))`, `PlayerIds.UNKNOWN` because no `Player` is available at this injection point. See
     `../plans/player-marker-colors/spec.md` "Detecting a dye change: mixin" for why a mixin (not a Fabric API
     event) is the only clean hook for this.
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
(§3's reload self-heal). It also reads each side's `SignText.getColor()` (a `DyeColor`, defaulting to `BLACK` for
an undyed sign) into `SignEntry.frontDye`/`backDye` as the enum name string — see `config-and-persistence.md`'s
`V6` section and `../plans/player-marker-colors/spec.md`. `SignHelper.reloadParser()` rebuilds the parser from the
current config — called from `SignManager.reloadConfig()` (see §3) on every BlueMap reset, so a sign parsed *after*
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

`SignEntryHelper` (plain Java, `core.signs`) derives the values `SignManager` dispatches from a `SignEntry`'s
front/back `SignLinesParseResult`s: `getPrefix` prefers the front side's prefix, falling back to the back side's
(`null` if neither matched); `getLabel` likewise prefers front, falling back to back — but only falls back when
front and back either match the *same* group or the front side didn't match at all; if front and back matched two
*different* groups, a blank front label returns `""` rather than the back's label, since the marker belongs to the
front's group (mirrors `getDetail`'s same-group rule below, and closes the same class of bug: previously a blank
front label on a group-mismatched sign silently borrowed the other group's label text). `getDetail` merges both
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

`addOrUpdateSign`, `removeByKey`, and `reloadConfig`'s phase 1 (below) are all `synchronized` on the same monitor
(finding #17, `../plans/codebase-review-2026-07-11.md`, resolved 2026-07-23) — `reset()`'s config-swap-then-reparse
sequence runs on whatever thread `BlueMapAPI.onEnable` fires on, not necessarily the server thread, so without this a
live sign edit/removal arriving from the mixins mid-swap could be clobbered by a stale dispatch, or a sign removed
mid-swap could be silently re-added. `reloadConfig`'s phase 2 (the actual dispatch loop) runs **unlocked** — see
below, `.scratch/concurrency-pass-2026-09/issues/05-signmanager-reloadconfig-lock-contention.md` — since it's O(n²)
work (a membership filter/sort per `LINE`/`SHAPE`/`EXTRUDE` sign) that would otherwise block every concurrent sign
edit for the reload's full duration. `dispatch()`
only enqueues onto `ReactiveQueue` under this lock (no blocking BlueMap API work), so it doesn't add hot-path
contention the way locking around `processMarkerAction` would.

### Representation and the transition table (`../plans/line-markers/spec.md` §6)

A private record `Representation(MarkerGroup group, String label, String detail, String dye)` captures what a sign
currently *is* to the marker layer: `null` means the sign matches no configured group (NONE); a non-null
`Representation` whose `group.type()` is `POI`, `LINE`, `SHAPE`, or `EXTRUDE` says which kind. `dye` is the sign's
own raw dye (`SignEntryHelper.getDye`, GitHub issue #198) — **not** a resolved marker-wide colour, since
`computeRepresentation` only ever sees one `SignEntry` at a time; conflict resolution across a marker's members
happens separately, in `ColorResolver` (below). `computeRepresentation(SignEntry, prefixGroupMap)` derives
it from `SignEntryHelper.getPrefix`/`getLabel`/`getDetail`/`getDye`, returning `null` if the entry has no resolvable
prefix or the prefix isn't in `prefixGroupMap` (an operator removed/renamed that group's prefix since this sign was
last dispatched — logged as a warning, not thrown).

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

`computeTransitionAction`'s logic (mirrors the table in the spec, generalized to a 4x4 grid of `POI`/`LINE`/`SHAPE`/
`EXTRUDE` alongside `NONE` — see `docs/adr/0002-shape-duplicates-line-pattern.md` for why each new multi-point type
duplicates the same pattern rather than generalizing it):

| Old ＼ New | NONE | POI | LINE | SHAPE | EXTRUDE |
|---|---|---|---|---|---|
| **NONE** | no-op (`null`) | dispatch `createAddPOIAction` | `lineJoinAction` (recompute group *including* this sign; dispatch `SetMultiPointMarkerAction` if ≥2 members, else no-op) | `shapeJoinAction` (same shape, gated on `SHAPE_MIN_MEMBERS = 3` instead of 2) | `extrudeJoinAction` (same shape again, gated on `EXTRUDE_MIN_MEMBERS = 3`) |
| **POI** | dispatch `createRemovePOIAction` | same group (prefix unchanged): label/detail both unchanged is a no-op, either changed dispatches `createUpdatePOIAction`. Different group (prefix changed): leave-effect + join-effect, bundled | leave-effect (Remove POI) + join-effect (`lineJoinAction`) | leave-effect (Remove POI) + join-effect (`shapeJoinAction`) | leave-effect (Remove POI) + join-effect (`extrudeJoinAction`) |
| **LINE** | `lineLeaveAction` (recompute group *excluding* this sign; `SetMultiPointMarkerAction` if ≥2 remain, `RemoveMultiPointMarkerAction` if exactly 1 remains, no-op if 0) | leave-effect (as above) + join-effect (Add POI) | same group+label: `lineJoinAction` with `sameGroupRecompute=true` (refreshes detail/points — always dispatches `Set` since ≥2 members necessarily already existed); different group/label: leave-effect + join-effect | leave-effect (`lineLeaveAction`) + join-effect (`shapeJoinAction`) | leave-effect (`lineLeaveAction`) + join-effect (`extrudeJoinAction`) |
| **SHAPE** | `shapeLeaveAction` (mirrors `lineLeaveAction`, but `RemoveMultiPointMarkerAction` once membership drops below 3) | leave-effect (`shapeLeaveAction`) + join-effect (Add POI) | leave-effect (`shapeLeaveAction`) + join-effect (`lineJoinAction`) | same group+label: `shapeJoinAction` with `sameGroupRecompute=true` (mirrors `LINE`/`LINE`); different group/label: leave-effect + join-effect | leave-effect (`shapeLeaveAction`) + join-effect (`extrudeJoinAction`) |
| **EXTRUDE** | `extrudeLeaveAction` (mirrors `shapeLeaveAction`, `RemoveMultiPointMarkerAction` once membership drops below 3) | leave-effect (`extrudeLeaveAction`) + join-effect (Add POI) | leave-effect (`extrudeLeaveAction`) + join-effect (`lineJoinAction`) | leave-effect (`extrudeLeaveAction`) + join-effect (`shapeJoinAction`) | same group+label: `extrudeJoinAction` with `sameGroupRecompute=true` (mirrors `SHAPE`/`SHAPE`); different group/label: leave-effect + join-effect |

The POI/POI cell deliberately compares only `group().prefix()`, not label — a label-only edit on an unchanged group
used to fall through to the different-group (leave+join) branch, because the older check compared
`sameGroupAndLabel` (group *and* label) before deciding whether to update-in-place; that made an ordinary label
edit dispatch a bundled remove+add instead of a single `UpdateMarkerAction`. The LINE/LINE, SHAPE/SHAPE, and
EXTRUDE/EXTRUDE cells all use `sameGroupAndLabel(a, b)` (compares `group().prefix()` and `label()`) — the
same-group-recompute shortcut condition is `oldType == newType && oldType != MarkerGroupType.POI &&
sameGroupAndLabel(...)`, generalized to cover any non-`POI` type rather than naming `LINE` explicitly, since a
`LINE`/`SHAPE`/`EXTRUDE` group's identity (and marker id, §5) is keyed on group+label either way. The recompute
shortcut's no-op guard is `oldRep.detail().equals(newRep.detail()) && dyeUnchanged && !isReload`, where
`dyeUnchanged = !newRep.group().allowPlayerColors() || Objects.equals(oldRep.dye(), newRep.dye())` — `dye` was added
to that guard (GitHub issue #198) alongside `detail` precisely so a dye-only edit (detail unchanged) doesn't get
silently swallowed: it makes `oldRep != newRep`, defeats the no-op check, and falls through to the normal recompute
path (`joinEffect`), which re-derives the marker's colour from the *current full membership* via `ColorResolver`
regardless of which member's dye actually changed. `Objects.equals` (not `oldRep.dye().equals(newRep.dye())`) is a
post-review fix (`../reviews/copilot-review-2026-09-07.md`): a `Representation`'s dye can be `null` in practice
(corrupted/hand-edited persisted data), and the direct `.equals()` call threw an NPE on that path. The
`allowPlayerColors()` short-circuit was added in the same fix so a group that hasn't opted into dye-derived colour
doesn't pay the full membership scan/sort/dispatch cost on every dye change to one of its signs — dye is compared
only when it could actually change the rendered colour. When a transition
needs both a leave-effect and a join-effect (a group/label/type change, for any pair of the four types), each
effect is computed independently (`null` if that half is a no-op, e.g. leaving a `LINE` group that still has ≥2
members after removal dispatches a `Set`, not a leave at all) and both are collected into a `List<MarkerAction>`:
zero effects → `null`, one effect → dispatch it directly, two → bundle into a `GroupTransitionMarkerAction` (see
§5/§6) so `ReactiveQueue`'s lack of message-ordering guarantees (§7) can't transiently show a sign in two places.
`groupIdentityObsolete` (used by the config-reload path, below) is likewise type-agnostic, detecting a config-only
type flip or rename for any of the four types.

`lineJoinAction(allSigns, parentMap, rep, actionFactory, sameGroupRecompute)` and `lineLeaveAction(allSigns,
parentMap, rep, actionFactory)` both call `LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(),
rep.label())` against the caller-supplied snapshot — because `signCache` is a full, live snapshot (never cleared
for a config reload, see below), a snapshot taken once per dispatch always scans every sign currently known, so a
`LINE` group recompute is always complete and order-independent regardless of which member triggered it.
`lineJoinAction`'s `isFirstAppearance` flag (`!sameGroupRecompute && members.size() == 2`) is log-only — see §6 —
not a distinct dispatch type. `shapeJoinAction`/`shapeLeaveAction` and `extrudeJoinAction`/`extrudeLeaveAction` are
the direct `SHAPE`/`EXTRUDE` counterparts: they call `ShapeGroupResolver.members(...)`/`ExtrudeGroupResolver.members(...)`,
both of which just delegate to `LineGroupResolver.members(...)` (identical filtering/ordering — the only real
difference between `LINE`, `SHAPE`, and `EXTRUDE` group resolution is the caller-side minimum-member count, `2` vs.
`SHAPE_MIN_MEMBERS = 3` vs. `EXTRUDE_MIN_MEMBERS = 3`), and dispatch via the single `actionFactory.createSetMultiPointAction`/
`createRemoveMultiPointAction` pair for all three types (§5) — `ActionFactory` derives which kind (and minimum-member
threshold) to build from `markerGroup.type()` itself, so `SignTransitionResolver` doesn't need per-type factory calls
here. Every join/leave-recompute call site calls `ColorResolver.resolve(members, rep.group())` (see below) immediately
before dispatching, and passes the resolved `lineColor`/`fillColor` into `createSetMultiPointAction` as explicit
parameters (`fillColor` is `null` for `LINE`, which has none) — `ActionFactory` no longer reads
`markerGroup.lineColor()`/`fillColor()` itself for this factory method, keeping it a dumb builder while
`SignTransitionResolver` owns colour resolution. These call sites also now pass `rep.detail()` (not `rep.label()`)
as the dispatched detail text — fixing a bug where a `LINE`/`SHAPE`/`EXTRUDE` marker's rendered detail was always
just its label repeated, since detail text on these signs was never actually threaded through.

### Conflict resolution: `ColorResolver` (player-controlled marker colours, GitHub issue #198)

`ColorResolver` (`core.signs`, plain Java, same shape as `LineGroupResolver`/`ShapeGroupResolver`/
`ExtrudeGroupResolver` — directly unit-testable) resolves a `LINE`/`SHAPE`/`EXTRUDE` marker's rendered
`lineColor`/`fillColor` from its current membership. Full design: `../plans/player-marker-colors/spec.md`.

- `resolve(List<SignEntry> members, MarkerGroup group)` returns the group's configured `lineColor`/`fillColor`
  unchanged (`ResolvedColors`) if `group.allowPlayerColors()` is `false`, or if no member has a non-`"BLACK"` dye
  (`SignEntryHelper.UNDYED_DYE`) — zero behavior change for a group that hasn't opted in, or where nobody's dyed
  anything.
- Otherwise: sorts `members` by `createdAtMillis` itself (doesn't trust the caller's ordering, even though
  `LineGroupResolver.members` happens to already return earliest-first) and picks the **earliest-placed member with
  a non-default dye** as the winner — re-evaluated fresh on every call against the current membership snapshot, not
  cached, so removing or redyeing the winner hands off to the next-earliest dyed survivor automatically.
- Looks up the winner's dye in a fixed `DYE_RGB` map (duplicating `net.minecraft.world.item.DyeColor
  .getTextureDiffuseColor()`'s values from the decompiled source for the pinned Minecraft version, so this class
  stays plain Java with no Minecraft type dependency) and combines that RGB with the **alpha byte already present in
  the group's configured** `lineColor`/`fillColor` (via `ColorUtils.parseHex`/`toHex`) — only the hue comes from the
  dye, never the alpha.
- Called from `SignTransitionResolver` at every join/leave-recompute call site above, for all three multi-point
  types, both when a sign joins a group and when the leave-recompute path re-derives colour after a member leaves.
- Known limitation (by design, not a bug): `SignText.getColor()` returns `DyeColor.BLACK` both for a genuinely
  undyed sign and one a player explicitly dyed black, so a black-dyed sign can never win — documented in `README.md`
  and the spec's "Out of scope" section.

`addOrUpdateSign(signEntry)` (called for every add/update event, from entry points 1-3 above — not §1.4's chunk-load
reconciliation, which only ever calls `removeByKey` directly): first runs the incoming `signEntry` through
`safeReparseFromRawLines(signEntry, config.parser())` (the same reparse helper reload uses, §3) — this matters at
server startup, where `SignProvider.loadSigns` (`config-and-persistence.md`) replays every persisted `V5` entry
through this method, so a sign whose prefix was renamed while the server was offline is still reclassified
correctly rather than dispatched under a stale cached parse. Then looks up `existing` from `signCache`, computes
`oldRep`/`newRep` from `existing`/the reparsed `signEntry` respectively, updates `signCache`/`chunkIndex` (removing the key if
`newRep == null` and something was cached, else caching the merged entry — the merge preserves the *existing*
cached `playerId` when the incoming entry's is the `PlayerIds.UNKNOWN` chunk-load sentinel, and preserves the
existing entry's `createdAtMillis` rather than ever recomputing it), then dispatches whatever
`computeTransitionAction` returns (if non-`null`), passing a fresh `getAllSigns()` snapshot as `allSigns`.

`removeByKey(key)` removes from `signCache`/`chunkIndex`; if nothing was cached for that key, it logs and returns.
Otherwise it computes `oldRep` from the removed entry, `computeTransitionAction(getAllSigns(), key, oldRep, null,
...)`, and dispatches the result — the same NONE-target column of the table above, so removal reuses no separate
code path.

### Config reload (`/bluemap reload`) — `reset()`/`reloadConfig()`

`reset()` (from `IResetHandler`) calls `reloadConfig()`, which is split into a locked **phase 1** (config swap +
reparse) and an unlocked **phase 2** (dispatch) — see the `synchronized`-monitor note above and
`.scratch/concurrency-pass-2026-09/issues/05-signmanager-reloadconfig-lock-contention.md`:

**Phase 1** (`synchronized`):
1. Captures `oldPrefixGroupMap = runtimeConfig.prefixGroupMap()` **before** touching anything else.
2. `ConfigManager.reload()` (re-reads `BMSM-Core.json` from disk), `SignHelper.reloadParser()`, then replaces
   `runtimeConfig` wholesale via `buildRuntimeConfig()` — a freshly rebuilt `prefixGroupMap` paired with a
   brand-new `ActionFactory` backed by a new `MarkerSetIdentifierCollection` — and calls
   `blueMapAPIConnector.clearMarkerSetsCache()` (see §6), so neither identifier cache accumulates entries keyed on
   a `MarkerGroup` value from before the last reload (`MarkerSetIdentifier` keys on the whole record by value, so
   a changed icon/offset/distance would otherwise be a new, never-evicted cache entry).
3. Takes one `getAllSigns()` snapshot (`allSigns`), and for every entry in it computes `oldRep` under
   `oldPrefixGroupMap` (stored in an `oldReps` map keyed by `SignEntryKey`) and passes the entry through
   `safeReparseFromRawLines(entry, newConfig.parser())` (a static, log-and-fall-back wrapper around
   `reparseFromRawLines`); if the result differs from the original reference, the reparsed entry replaces it in
   `signCache`. `reparseFromRawLines` re-runs `SignLinesParser.parse(...)` on the entry's persisted
   `frontRawLines`/`backRawLines` against the new config and returns `entry.withParsedText(freshFront, freshBack)` —
   or the *same* entry reference, cheaply detectable via `!=`, if either raw-lines array is `null` (an entry
   migrated from pre-V5 data, with no raw text on disk to re-parse; see `config-and-persistence.md`).

**Phase 2** (unlocked, runs after the `synchronized` block exits): for every `(key, oldRep)` pair captured in phase
1, re-reads `current = signCache.get(key)` fresh and computes `newRep` under the just-rebuilt `prefixGroupMap` from
`current` (`null` if the key's no longer cached), then dispatches `computeTransitionAction(allSigns, key, oldRep,
newRep, ...)` if non-`null` — the exact same transition table a live sign edit uses, just fed a before/after diff
against the *config* instead of the *sign text*. The `allSigns` snapshot passed to `computeTransitionAction` (via
`this::getAllSigns`, an `allSignsSupplier`) is read **live** off `signCache` on every call, not a snapshot frozen at
the end of phase 1 — a frozen snapshot could go stale mid-phase-2 if a sign is concurrently added/removed/edited
(e.g. a concurrent `removeByKey` dropping a `LINE` member), which would make `LineGroupResolver.members` recompute
against membership that no longer matches `signCache` and could resurrect a member a concurrent dispatch had just
independently removed. A sign edited concurrently during phase 2 is not clobbered: phase 2 only ever reads
`signCache` (never mutates it), so it can only race a concurrent edit's own independent dispatch for the same key or
line/shape — both are best-effort idempotent set/remove actions applied through `ReactiveQueue`, which already gives
no cross-dispatch ordering guarantee (§7), so unlocking phase 2 doesn't introduce a new class of risk, only widens
an existing one to a rarer window. Reading `signCache` fresh per multi-point dispatch costs an extra
`signCache.values()` copy compared to one shared snapshot, but that's the same cost every live sign edit already
pays via `dispatchTransition`.

This replaced the previous behavior (`reloadSigns()`: snapshot the cache, clear it, replay every entry through
`addOrUpdateSign` so every entry always took the Add branch) for a concrete bug fix documented in
`.scratch/codebase-review-followups/issues/10-reload-clear-and-replay-orphans-markers-on-id-scheme-change.md` and
`../plans/line-markers/issues/07-config-reload-fix-id-scheme-change.md`: a replayed "add" only ever puts a marker
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

## 5. Marker identity — `DispatchedMarkerIdentifier` / `MarkerIdentifier` / `MultiPointMarkerIdentifier` / `MarkerSetIdentifier` / `MarkerSetIdentifierCollection`

Two id schemes now exist side by side (position-keyed and content-keyed), unified behind one interface:

- `DispatchedMarkerIdentifier` (`core.markers`) — `{ MarkerSetIdentifier parentSet(); String getId(); }`. `MarkerAction`
  holds one of these (widened from the concrete `MarkerIdentifier` it used to hold) so a single dispatch hierarchy
  covers both id schemes below.
- `MarkerIdentifier(x, y, z, parentSet)` implements it — its `getId()` is `"x%d_y%d_z%d"`, **position-keyed**, the
  literal key used inside a BlueMap `MarkerSet`'s marker map for POI markers. **No dimension component** —
  uniqueness across dimensions is guaranteed only because each dimension maps to a separate
  `MarkerSetIdentifier`/`MarkerSet`, not because the id string itself is unique.
- `MultiPointMarkerIdentifier(kind, label, parentSet)` (record) also implements it — its `getId()` is `kind + ":" +
  label`, **content-keyed**, not position-keyed: a line/shape/extrude's points move as members join/leave, but its
  id stays stable as long as the (group, label) key doesn't change. `kind` is one of the string literals `"line"`/
  `"shape"`/`"extrude"` (`ActionFactory`'s `LINE_KIND`/`SHAPE_KIND`/`EXTRUDE_KIND` constants), so `getId()` returns
  `"line:" + label`/`"shape:" + label`/`"extrude:" + label` respectively. This one record replaces what used to be
  three near-identical records (`LineMarkerIdentifier`/`ShapeMarkerIdentifier`/`ExtrudeMarkerIdentifier`), which
  differed only by that hardcoded id-prefix. The content-keyed vs. position-keyed distinction is exactly what
  motivated `SignManager.reloadConfig()`'s rewrite (§3) — a naive replay only ever adds under a marker's *current*
  id, so an id scheme changing between reloads (e.g. a group's `type` flipping `POI`↔`LINE`↔`SHAPE`↔`EXTRUDE`) needs
  an explicit dispatch removing the *old* id, not just adding the new one.
- `MarkerSetIdentifier(mapId, markerGroup)` — one BlueMap marker-set per (map, marker-group) pair, unchanged by the
  line-markers work.
- `ActionFactory.createGroupTransitionPOIAction(x, y, z, mapId, label, detail, oldMarkerGroup, newMarkerGroup)`
  (renamed from `createChangeGroupPOIAction`) builds a `GroupTransitionMarkerAction` wrapping `List.of(new
  RemoveMarkerAction(oldIdentifier), new AddMarkerAction(newIdentifier, label, detail))` — two full `MarkerAction`s,
  not a single action carrying two identifiers. A private `markerIdentifier(x, y, z, mapId, markerGroup)` helper
  wraps the repeated `new MarkerIdentifier(x, y, z, markerSetIdentifierCollection.getIdentifier(mapId,
  markerGroup))` construction shared by `createAddPOIAction`/`createRemovePOIAction`/`createUpdatePOIAction`/
  `createGroupTransitionPOIAction`.
  `createSetMultiPointAction(mapId, markerGroup, label, detail, points, lineColor, fillColor, isFirstAppearance)`
  and `createRemoveMultiPointAction(mapId, markerGroup, label)` are the single `LINE`/`SHAPE`/`EXTRUDE` factory pair
  — replacing the former per-type `createSetLineAction`/`createSetShapeAction`/`createSetExtrudeAction` and
  `createRemoveLineAction`/`createRemoveShapeAction`/`createRemoveExtrudeAction` methods. Both derive a private
  `MultiPointKind(String name, int minMembers)` from `markerGroup.type()` via a private `multiPointKind` switch
  (`LINE`/`SHAPE`/`EXTRUDE` map to their kind string + `MultiPointGroupThresholds` constant; `POI` throws
  `IllegalArgumentException`, replacing the former per-method `requireGroupType` checks — safe because every call
  site in `SignTransitionResolver` already dispatches by `rep.group().type()` before reaching either method, §3) and
  build the `MultiPointMarkerIdentifier(kind.name(), label, ...)` accordingly. `createSetMultiPointAction` carries
  `markerGroup.lineWidth()` plus the caller-supplied `lineColor`/`fillColor` through — the caller
  (`SignTransitionResolver`, via `ColorResolver`, GitHub issue #198) passes the *resolved* colour(s) explicitly
  rather than `ActionFactory` reading `markerGroup.lineColor()`/`fillColor()` itself, since a group with
  `allowPlayerColors` set may render a dye-derived colour instead of the configured one; `LINE` callers pass `null`
  for `fillColor`, which it has none of.
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
  `volatile Map<MarkerSetIdentifier, List<MarkerSet>> markerSetsCache`, and a
  `volatile Map<String, RenderMaskEvaluator.RenderMask> renderMaskCache` (keyed by real `BlueMapMap` id — see §8).
  All three are `volatile` because `resetQueue()`/`clearMarkerSetsCache()` always replace them wholesale with a
  brand-new object rather than mutating the existing one, so correctness only needs a reader to see the latest
  *reference* — that's what `volatile` guarantees (it says nothing about the referenced objects, which are mutated
  afterward through their own thread-safe methods: `ReactiveQueue.enqueue()`/`process()`, `ConcurrentHashMap.get()`/
  `putIfAbsent()`/`computeIfAbsent()`). No reader (`dispatch()`/`onDisable()`/`onEnable()` for the queue,
  `getMarkerSets()` for the marker-set cache, `getRenderMask()` for the render-mask cache) ever needs a joint
  snapshot of more than one of these fields at once, so per-field visibility is enough — a shared lock would
  additionally serialize `dispatch()` (hot path, every sign event) behind `processMarkerAction()`'s BlueMap API
  calls, an unrelated critical section. This resolves finding #12
  (`../plans/codebase-review-2026-07-11.md`, resolved 2026-07-22) and the field-visibility half of #11.
  `renderMaskCache` is invalidated (replaced with a fresh empty map) at the exact same call sites as
  `markerSetsCache` — `resetQueue()` and `clearMarkerSetsCache()` — so neither cache survives a config reload or a
  genuine BlueMap disable/enable cycle carrying stale entries. There is no cached `blueMapAPI` field anymore (see
  "No cached `BlueMapAPI` reference" below) — `getMaps()` re-fetches `BlueMapAPI.getInstance()` on every call.
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
  `shutdown()` also now retires `markerActionQueue` itself (`markerActionQueue.shutdown()`, warning if it returns
  `false` — couldn't confirm every in-flight task stopped), not just the two listeners — previously this left the
  queue's fixed thread pool alive and any in-flight marker action unawaited on server stop, so
  `BlueMapSignMarkersMod.onServerStopping`'s configured shutdown timeout (§7) had no effect at all on the connector's
  own `shutdown()` path. Mirrors `onDisable()`'s own `shutdown()` call, but runs on server stop regardless of
  whether BlueMap ever fired `onDisable` first.
- **Startup sign-load bugfix, superseded twice:** `onEnable`/`onDisable` originally gated the reload-vs-first-boot
  decision on `markerActionQueue.isShutdown()`, but a brand-new `ReactiveQueue` whose executor was never lazily
  created also reported `isShutdown() == true` — exactly what happens at server startup, when `SERVER_STARTING`
  dispatches an action for every migrated/loaded sign before BlueMap is available: `process()` returns early
  (`shouldRun()` is `false`) without ever creating an executor. That made the *first* `onEnable()` a server ever sees
  mistake startup for a reload, call `resetQueue()`, and discard every action enqueued during sign load before a
  single one was processed. That was first fixed with an explicit `volatile boolean disabledSinceLastEnable` field,
  then that field was removed once `ReactiveQueue.isShutdown()` itself was corrected to stop conflating "genuinely
  shut down" with "never started" (finding 63, `.scratch/concurrency-pass-2026-09/issues/04-reactivequeue-isshutdown-semantics.md`
  — see §7's `isShutdown()`/`hasStarted()` split) — `onEnable()` now asks `markerActionQueue.isShutdown()` directly
  again, correctly this time.
- `BlueMapAPI.onEnable`/`onDisable` are registered in the constructor. `onDisable` shuts the queue down (actions
  keep enqueuing but stop draining) and records `lastShutdownConfirmedClean = markerActionQueue.shutdown()`'s return
  value (see §7's `shutdown()`) — read by the next `onEnable()` to log a warning if a non-interruptible straggler
  task may still race the reset replay below (no better recovery is available; this only makes the violation
  observable). `onEnable(api)` runs its whole isGenuineReload check-then-act **`synchronized (this)`** (shares the
  monitor `processMarkerAction`/`applySingleAction` use) — BlueMap's listener dispatch is presumed single-threaded
  today, but the check-then-act wasn't atomic on its own, so a hypothetical concurrent `onEnable()` call could
  otherwise also observe `isShutdown()==true` and double-fire both (finding 44,
  `agent-context/reviews/full-codebase-review_2026-09-07_0900.md`) — safe to share since `onEnable()` only runs on a
  genuine BlueMap enable/reload, never the hot path. Inside the lock: if `markerActionQueue.isShutdown()` is a
  genuine reload, it calls `ConfigManager.reload()` **before** `resetQueue()` (not after) — `resetQueue()` reads
  `ConfigManager.get().getShutdownAwaitSeconds()` to build the new queue, and the old ordering left an edited
  `shutdownAwaitSeconds` value with no effect until a later disable/re-enable cycle, since the queue's value is
  fixed at construction; `fireReset()` still runs its own `ConfigManager.reload()` right after (harmless — re-reads
  the same just-reloaded file) since it also needs to rebuild `SignManager`'s parser/prefix map — then calls
  `resetQueue()` (fresh queue + fresh `markerSetsCache`) and `fireReset()` (→ every registered `IResetHandler`, i.e.
  `SignManager.reset()`). Otherwise, if `markerActionQueue.consumeOverflowSinceLastCheck()` reports at least one
  message was rejected for capacity since the last check (see §7's `DEFAULT_CAPACITY`/`overflowedSinceLastCheck`) —
  checked and cleared even on this very first `onEnable()`, since a large sign count enqueuing every migrated/loaded
  sign's add action while BlueMap is still unavailable at startup can silently overflow, and `isGenuineReload` is
  `false` on a first boot so `fireReset()` wouldn't otherwise run to recover the dropped markers (the "capacity does
  not bound..." finding, `agent-context/reviews/copilotreview.2026-09-09.md`) — it logs a warning and calls
  `fireReset()` to replay every currently tracked sign's marker state and recover the ones capacity dropped. No
  `blueMapAPI` field is assigned or read anymore (see below); the previous ordering bug where a cached `BlueMapAPI`
  reference could go stale mid-replay no longer applies since `getMaps()` re-fetches the instance fresh every call.
- **No cached `BlueMapAPI` reference:** the connector used to hold a `volatile BlueMapAPI blueMapAPI` field, set in
  the constructor (`BlueMapAPI.getInstance().orElse(null)`, since BlueMap may already be enabled by construction
  time) and reassigned first thing in `onEnable`, with `getMaps()` reading `this.blueMapAPI.getWorld(mapId)`. That
  field is gone (finding 43, `agent-context/reviews/full-codebase-review_2026-09-07_0900.md`): `getMaps(mapId)` now
  calls `BlueMapAPI.getInstance()` itself on every invocation, returning `Optional.empty()` (logged at debug) if
  empty — a disable/re-enable landing between `processMarkerAction`'s own guard check and this call could otherwise
  operate against a defunct cached instance instead of the current one.
- `getMarkerSets(identifier)` is `synchronized`; on cache miss it resolves `BlueMapAPI.getWorld(mapId)` →
  `.getMaps()`, and for each map either fetches an existing `MarkerSet` by `markerGroup.name()` or builds+registers
  one (`label`, `defaultHidden`, `sorting`, `toggleable` from the `MarkerGroup` — `sorting`/`toggleable` are thin
  BlueMap `MarkerSet` passthroughs, unlike `depthTest`/`cssClasses` below which are per-marker). One
  `MarkerSetIdentifier` can map to *multiple*
  `MarkerSet`s if a world has multiple BlueMap maps rendered for it — every dispatched action applies to all of them.
  The `markerSetsCache.putIfAbsent(...)`/debug-log call now happens **once, after** the per-map `forEach` loop
  (ticket 06) rather than repeated inside it against the same `markerSetsToReturn` list reference — the old
  placement was correct only by relying on the implicit invariant that `putIfAbsent` against the same key/list is
  a no-op on every iteration after the first, fragile to a future refactor breaking it silently.
- `logProcessingMessage` sanitizes sign-derived detail text via `LogUtils.sanitizeForLog` (`common`, ticket 06)
  before logging it at INFO — strips ANSI CSI escape sequences and normalizes `\r\n`/`\n`/`\r` to literal `\n`
  and `\r`, closing a log-injection/log-noise vector where only `\n` was previously escaped.
- `processMarkerAction` is `synchronized` (finding #5, resolved 2026-07-22): the `MarkerMutations` effects it invokes
  mutate a `MarkerSet`'s marker `Map` (thread-safety of which is BlueMap's concern, not this mod's), and
  `ReactiveQueue`'s executor is sized to `availableProcessors()`, so without this lock two actions dispatched close
  together (e.g. many signs loading at server startup) could race on the same underlying map. Because
  `ReactiveQueue.shutdown()` only stops *new* submissions (already-submitted tasks still run — see §7), several such
  tasks can end up queued behind this monitor for a while after a shutdown is requested; `processMarkerAction`
  re-checks `BlueMapAPI.getInstance().isEmpty()` itself on entry so one of those queued tasks can't mutate a
  `MarkerSet` after BlueMap has actually disabled in the meantime. If the dispatched action is a
  `GroupTransitionMarkerAction`, it iterates `transitionAction.effects()` (a `List<MarkerAction>`, 0-2 entries —
  see §3/§5) calling `applySingleAction` on each **inside** the same synchronized call, so a bundled leave+join
  pair (or POI↔LINE swap) can never be observed half-applied by another thread; otherwise it calls
  `applySingleAction` directly on the one action. `applySingleAction` logs (`logProcessingMessage`) then dispatches
  on the concrete `MarkerAction` subtype via a `switch` pattern-match: `AddMarkerAction`/`RemoveMarkerAction`/
  `UpdateMarkerAction`/`SetMultiPointMarkerAction`/`RemoveMultiPointMarkerAction` each have a `case` arm — **`MarkerAction` is a
  plain abstract class, not `sealed`**, so adding a new subtype without adding a `case` here (and in
  `logProcessingMessage`'s switch) silently falls through to `default` instead of failing to compile — see
  `AGENTS.md`'s "Adding a new marker/BlueMap action" section. All cases resolve their marker sets via a shared
  `prepareGated`/`prepareUngated` helper (parameter type `DispatchedMarkerIdentifier`, needs only `.parentSet()` —
  looks up via `getMarkerSets`, returns a no-op `Runnable` with a debug log if none found, otherwise hands the
  effect a `Map<String, Marker>` per target map) — but each `case` arm's actual mutation is now a method reference
  into `MarkerMutations` (below), not code inlined in `BlueMapAPIConnector` itself (ticket 04,
  `.scratch/marker-action-consolidation/issues/04-shrink-bluemapapiconnector-marker-mutations.md`).
- **`MarkerMutations`** (new class in `core.bluemap`, package-private, all-`static`) holds every state-free
  marker-construction method `BlueMapAPIConnector` used to implement inline — no reference to the connector's
  queue/cache/lifecycle fields, so this logic can be read (and tested where possible) independent of dispatch/
  gating/cache concerns. `BlueMapAPIConnector.prepareSingleAction` calls into it via lambdas
  (`markers -> MarkerMutations.addMarker(addAction, markers)`, etc.) for every `MarkerAction` case.
  `addMarker`/`updateMarker`/`removeMarker`/`removeMarkerById`/`setMultiPointMarker`/`toBlueMapColor`/
  `resolveExtrudeHeightRange` all live here now; `pointOf` and `isInsideRenderBounds` (render-bounds gating, §8)
  stayed on `BlueMapAPIConnector` since they're part of the gating logic, not marker construction, and were
  explicitly out of scope for ticket 04.
- `MarkerMutations.setMultiPointMarker(SetMultiPointMarkerAction, Map<String, Marker>)` replaces the former separate
  `setLineMarker`/`setShapeMarker`/`setExtrudeMarker` methods (ticket 04) with one entry point covering all three
  kinds: it reads `MultiPointMarkerIdentifier.kind()` off the action's identifier, resolves that kind's minimum
  member count via a private `minMembersFor(kind)` switch (`MultiPointGroupThresholds.LINE_MIN_MEMBERS`/
  `SHAPE_MIN_MEMBERS`/`EXTRUDE_MIN_MEMBERS`, warning-and-return if unknown or below threshold — the same defensive
  guard the three separate methods used to have individually), then parses `action.getLineColor()` once via
  `toBlueMapColor(ColorUtils.parseHex(...))` before branching into a `switch (kind)` **statement** (not an
  expression) with one arm per kind:
  - `"line"` builds a `de.bluecolored.bluemap.api.math.Line` from the action's `LinePoint`s (via `Vector3d`), then
    `put`s a `LineMarker.builder().label(...).detail(HtmlUtils.toHtmlDetail(...)).line(line).lineWidth(...)
    .lineColor(...).depthTestEnabled(markerGroup.depthTest()).build()`, keyed by
    `action.getMarkerIdentifier().getId()` (the content-keyed `"line:" + label` id, §5).
  - `"shape"` builds a 2D `Shape` footprint from the points' `x`/`z` only, taking the marker's rendered height from
    the **tallest member** (`points.stream().mapToInt(LinePoint::y).max()`), additionally parses `fillColor`
    (defaults to a translucent red, `#FF000033`, unlike `lineColor`'s opaque default — see
    `config-and-persistence.md`), and `put`s a `ShapeMarker.builder()...build()` keyed by the content-keyed
    `"shape:" + label` id.
  - `"extrude"` builds the same 2D `Shape` footprint as `"shape"`, but instead of a single tallest-member height
    calls `resolveExtrudeHeightRange(label, points)` for the volume's floor/ceiling — `minY`/`maxY` are the
    lowest/tallest member's Y independently (not both from the tallest member), so the extrusion spans the full
    height range its members were placed at regardless of placement order; if every member is at the same Y
    (`maxY <= minY`, e.g. one flat floor), it logs at debug and bumps `maxY` to `minY + 1` rather than building a
    zero-height (invisible) volume. Also parses `fillColor` the same way `"shape"` does, and `put`s an
    `ExtrudeMarker.builder()...build()` keyed by the content-keyed `"extrude:" + label` id.

  Each arm builds, sets `minDistance`/`maxDistance`, and `put`s its own concretely-typed marker
  (`LineMarker`/`ShapeMarker`/`ExtrudeMarker`) independently, rather than converging all three into one shared
  variable typed as their common supertype (`ObjectMarker`) after the switch — `bluemap-api` is `compileOnly` and
  absent from the test runtime classpath, and a switch **expression** assigning `LineMarker`/`ShapeMarker`/
  `ExtrudeMarker` branches into one `ObjectMarker`-typed variable forces the JVM verifier to resolve `ObjectMarker`
  the moment `MarkerMutations` is first loaded — which broke `MarkerMutationsTest`'s coverage of the *unrelated*
  `resolveExtrudeHeightRange` method purely by being in the same class. The switch **statement** form avoids that
  merge entirely. `resolveExtrudeHeightRange` returns a private `ExtrudeHeightRange(float minY, float maxY)` record —
  a plain value holder with no `bluemap-api` types, so it stays directly unit-testable (`MarkerMutationsTest`,
  `testing.md`) even though the rest of `MarkerMutations` is game-coupled.
- Two more small helpers stayed on `BlueMapAPIConnector` itself, package-private (not `private`) specifically for
  direct unit testing: `pointOf(MarkerIdentifier)` (builds the single-point `List<LinePoint>` used by the
  render-bounds gate, §8, for a position-keyed POI marker) and `isInsideRenderBounds(RenderMaskEvaluator.RenderMask,
  List<LinePoint>)` (the pure mask-vs-points predicate, split out of the `mapId`-taking overload that does the cache
  lookup) — both covered in `BlueMapAPIConnectorTest` without needing a live connector instance or `bluemap-api`
  (`compileOnly`) on the test classpath.
- `MarkerMutations.addMarker` only actually builds a marker `if (markerGroup.type() == MarkerGroupType.POI)` — this
  is a real, live branch now that `MarkerGroupType.LINE`/`SHAPE`/`EXTRUDE` exist (no longer future-proofing for
  values that didn't exist): a `LINE`-, `SHAPE`-, or `EXTRUDE`-typed group's signs never reach `addMarker` at all,
  since `SignManager`'s transition table (§3) routes those representations to their own `Set`/`Remove` actions
  instead of `AddMarkerAction`. It also conditionally calls
  `markerBuilder.styleClasses(markerGroup.cssClasses().toArray(new String[0]))` when `cssClasses` is non-empty —
  the only marker-builder call gated on the field being present rather than always called with a possibly-default
  value, since BlueMap's `styleClasses` has no meaningful "unset" default to fall back to.
- **HTML escaping (fixed)**: `MarkerMutations.addMarker`/`updateMarker` wrap `detail` with
  `HtmlUtils.toHtmlDetail(...)` (`common` package) before it reaches `POIMarker.builder().detail(...)` /
  `poiMarker.setDetail(...)` — BlueMap renders `detail` as raw HTML (unlike `label`, which BlueMap's own
  `Marker.setLabel()` escapes), and sign text is player-controlled, so this closed a live XSS vector
  (`../plans/html-detail-escaping-plan.md`). `toHtmlDetail` escapes first, then converts `\n` to `<br>` so
  multi-line detail renders line breaks correctly — escaping before the `<br>` substitution matters, otherwise the
  inserted tags would themselves get escaped. `SignEntry`/persisted `signs.json` data stays raw/unescaped; escaping
  happens only at this BlueMap-API call site.

## 7. `ReactiveQueue<T>` — generic buffer-while-unavailable primitive

Lives in `core.reactive`, not BlueMap-specific — reusable anywhere something needs to "queue while a dependency is
unavailable, drain once it's back."

- `enqueue(message)`: reserves a capacity slot via `tryReserveSlot()` (an `AtomicInteger queuedCount` compare-and-set
  loop against `capacity`, default `DEFAULT_CAPACITY = 100_000`); on success offers to an internal
  `ConcurrentLinkedQueue` and calls `process()`. On failure (capacity reached), `warnAtCapacity(message)` sets
  `overflowedSinceLastCheck` and logs a throttled warning (at most once per `CAPACITY_WARNING_THROTTLE_MILLIS` =
  1000ms, so sustained overflow doesn't flood the log), and the message is **permanently dropped** — `enqueue()` has
  no failure signal back to its caller and this queue has no retry/replay path of its own. `capacity` is a defensive
  cap (finding 61, `agent-context/reviews/full-codebase-review_2026-09-07_0900.md`) for a class documented as
  reusable beyond today's sign-count-bounded usage; set high specifically because `SignManager.reset()` (a
  `/bluemap reload`) re-dispatches one message per cached sign and region-sharded persistence exists to support
  large sign counts — a tight cap would risk silently dropping markers on reload for exactly the large-server case
  this mod targets. `consumeOverflowSinceLastCheck()` (an `AtomicBoolean.getAndSet(false)`) lets a caller with its
  own replay mechanism recover from overflow — `BlueMapAPIConnector.onEnable()` polls it and calls `fireReset()` if
  it was ever set (§6) — rather than the dropped message staying silently lost forever.
- `process()`: bails immediately if `shutdownRequested` or `!shouldRunCallback.shouldRun()` (for
  `BlueMapAPIConnector`, `shouldRun` is `BlueMapAPI.getInstance().isPresent()`); otherwise, if an `AtomicBoolean
  draining` compare-and-set from `false`→`true` succeeds (finding 62,
  `agent-context/reviews/full-codebase-review_2026-09-07_0900.md` — guards against a burst of concurrent `enqueue()`
  calls each submitting their own redundant drain-loop task; if the CAS fails, a drain loop is already active or
  about to be, and it will drain this message too, so `process()` just returns), submits `processMessages` to
  `getExecutor()`'s fixed thread pool (`Executors.newFixedThreadPool(availableProcessors())`) — resetting `draining`
  back to `false` if `getExecutor()` returns `null` or submission throws `RejectedExecutionException` (shut down
  concurrently between the check and the submission — nothing more to schedule on a retired instance).
- `processMessages` loops while `canContinueDraining()` (`!shutdownRequested && !queue.isEmpty() && shouldRun()`,
  extracted so the loop condition and its post-drain recheck below can't drift apart) holds, polling one message at
  a time and submitting **each individual message** as its own task to the same executor (so message processing
  itself is also concurrent, not just the drain loop) — a per-message `RejectedExecutionException` during a
  shutdown race just returns; any other exception from the submission reaches `messageProcessorErrorCallback`.
  `queuedCount` is decremented only once a message's submitted task actually *finishes* (in a `finally` around the
  processor/error-callback calls) or is known never to run (executor `null`, submission threw) — **not** right
  after `poll()` — because decrementing immediately would free a capacity slot the instant a message left `queue`,
  even though it then sat in the executor's own unbounded internal work queue awaiting a free worker thread; a slow
  processor could accumulate arbitrarily many not-yet-run tasks there while `enqueue()` kept accepting more,
  defeating the point of capacity (it would only ever bound `queue` itself, not total outstanding work). A `finally`
  block around the whole loop always resets `draining` back to `false` on exit, then re-checks
  `canContinueDraining()` and calls `process()` again if it's still true — closing a race window between the loop
  observing "nothing left to do" and the flag actually clearing, where a concurrent `enqueue()`/`process()` call
  could otherwise see `draining` still true, skip submitting, and never get drained.
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
  `getExecutor()` call can't deadlock against it — blocks up to `shutdownAwaitSeconds` on
  `awaitTermination`, falling back to `shutdownNow()` (then one more bounded `awaitTermination`) if the timeout
  elapses, returning `true`/`false` for whether it confirmed a clean stop (read by `BlueMapAPIConnector.onDisable`/
  `shutdown()`, §6). This is what lets a caller that awaits `shutdown()` returning (e.g. `BlueMapAPIConnector.onDisable`)
  rely on there being no straggler task still able to touch shared state afterward, which otherwise could run
  after a subsequent `resetQueue()`/`fireReset()` replay and clobber the state that replay just established.
  `shutdownAwaitSeconds` is now **configurable** rather than the fixed `DEFAULT_SHUTDOWN_AWAIT_SECONDS` (5): the
  public/BlueMapAPIConnector-facing constructor takes it as a parameter, sourced from
  `ConfigManager.get().getShutdownAwaitSeconds()` — a `BMSMConfigV2` field (`config-and-persistence.md`) resolved
  the same validating-with-fallback way as `sorting`/`lineWidth` (malformed or non-positive falls back to the
  default with a warning, `ConfigProvider.resolveShutdownAwaitSeconds`) — so an operator can tune how long server
  stop/BlueMap-disable waits for in-flight marker actions before forcing them.
- Once `shutdownRequested` is set, `getExecutor()` **never creates a replacement executor** — a shut-down queue is
  permanently retired rather than self-healing (finding #2). This is why `BlueMapAPIConnector.onEnable` has to call
  `resetQueue()` (a brand-new `ReactiveQueue` instance) rather than relying on the old one to resurrect itself.
- `executor` is `volatile` (finding #12, resolved 2026-07-22) so `isShutdown()`/`hasStarted()` — callable with no
  lock held, from any thread — see `getExecutor()`'s synchronized write without needing their own synchronization.
  `isShutdown()` returns `shutdownRequested` alone now (finding 63,
  `.scratch/concurrency-pass-2026-09/issues/04-reactivequeue-isshutdown-semantics.md`) — it used to also return
  `true` for `executor == null || executor.isShutdown()`, conflating "genuinely shut down" with "never started" (a
  queue whose `process()` calls all returned early via `shouldRun()`/`shutdownRequested` before ever lazily creating
  an executor). That conflation was real: `BlueMapAPIConnector.onEnable()` needed a whole separate
  `disabledSinceLastEnable` flag to tell a genuine BlueMap disable/re-enable apart from the very first `onEnable()`
  a server ever sees (§6), precisely because the old `isShutdown()` also reported `true` for the never-started case.
  `shutdownRequested` alone is sufficient — only `shutdown()` ever sets it, always synchronously with calling
  `executor.shutdown()` on whatever executor exists at that moment, and nothing outside this class can shut the
  internal executor down through any other path. `hasStarted()` (`executor != null`) exposes the other half of the
  old conflation directly, for any caller that genuinely needs to know "has this instance ever lazily created its
  executor" regardless of shutdown state.
- A package-private constructor overload accepts an `ExecutorService` directly (the public constructor
  delegates to it with `null`, same as before) — test-only seam so `ReactiveQueueTest` can inject a synchronous or
  failure-simulating fake executor instead of the lazily-created fixed thread pool, with no change to real
  behavior. A previously-documented gap — an exception thrown by the processor callback itself was swallowed
  (captured on an unawaited `Future`, never reaching `messageProcessorErrorCallback`), only a submission-time
  failure reached that callback — is now fixed: the task submitted per-message in `processMessages` wraps
  `messageProcessorCallback.processMessage(message)` in its own `try/catch`, forwarding any exception to
  `messageProcessorErrorCallback` (itself wrapped in a `try/catch` so a broken error callback can't kill the worker
  thread or propagate back to `enqueue()`'s caller). See `testing.md` for full coverage.

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
  than silently falling back to the field's default because the quoted form went unmatched). `intField` parses its
  int-typed fields (`min-x`/`max-x`/`min-y`/`max-y`/`min-z`/`max-z`) via `new BigDecimal(value).intValueExact()`
  rather than `Integer.parseInt` — `FIELD_PATTERN`'s numeric alternative allows exponent notation (e.g. `"1e3"`) on
  every numeric field, int-typed ones included, and `Integer.parseInt` can't handle that form (would throw, failing
  the whole map open on an otherwise-valid value); a genuinely fractional value (e.g. `"1.5"`) still throws via
  `intValueExact`, same as before.
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
  unrecognized shape type, or an ellipse's `radius-x`/`radius-z` parsed as `<= 0` — `requiredPositiveDoubleField`
  rejects it before it can reach `RenderMaskEllipse.contains()`'s division and produce `Infinity`/`NaN`) while
  parsing it. Each of these logs a warning before returning the empty list — a broken render-mask config degrades
  to "no filtering" rather than crashing marker dispatch (see `project_no_server_crashes` guidance).
- **`BlueMapAPIConnector` integration**: `getRenderMask(mapId)` is `renderMaskCache.computeIfAbsent(mapId, id ->
  RenderMaskEvaluator.load(...))` — one parse per real map id, cached for the connector's lifetime until
  invalidated (see §6). `MappedMarkerSet(String mapId, MarkerSet markerSet)` is a private record pairing a cached
  `MarkerSet` with the real map id it came from, since gating needs the id to look up that map's `RenderMask` but
  the pre-existing `markerSetsCache` only stored bare `MarkerSet`s.
- **Gated vs. unconditional dispatch**: `prepareSingleAction` routes `AddMarkerAction`/`UpdateMarkerAction`
  (single point, wrapped via a `pointOf(MarkerIdentifier)` helper into a one-element point list) and
  `SetMultiPointMarkerAction` (its own multi-point `getPoints()`, covering `LINE`/`SHAPE`/`EXTRUDE` alike) through
  `prepareGated(identifier, points, effect)`. For each target `MappedMarkerSet`, `isInsideRenderBounds`
  is `points.stream().anyMatch(p -> mask.contains(...))` — **any one point in bounds passes the whole marker**
  (all-or-nothing for `LINE`/`SHAPE`, no per-point clipping). On a pass, `effect.accept(markers)` runs as normal;
  on a gate failure, the marker id is actively removed from that map's set (`markers.remove(identifier.getId())`)
  instead of merely skipping the add/update — this is what sweeps a marker that already existed on a
  now-out-of-bounds map before this feature shipped, reusing `SignManager.reset()`'s existing reload-forced
  re-dispatch of every sign (§3) as the trigger. `RemoveMarkerAction`/`RemoveMultiPointMarkerAction` instead go
  through `prepareUngated(identifier, effect)` — unconditional, no gating, since an explicit removal means the
  sign's representation is genuinely leaving, independent of bounds.
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
*Last updated: 2026-09-12 | Verified against: feature/tpwalke2/209-bluemapapiconnector (40f2273)*

