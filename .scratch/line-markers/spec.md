# Line markers (issue #7)

Split into implementation tickets at `.scratch/line-markers/issues/01`-`08`. Each ticket cites the spec section(s)
it implements — load only that ticket + the cited section(s) when working a ticket, not this whole file.

## Context

Addresses GitHub issue #7. Three branches attempted this and were abandoned, none merged:

| Branch | Approach | Where it stalled |
|---|---|---|
| `7-lines` | Big if/switch reducer (`SignMarkerReducer`, 281 lines) over transition cases | All commits "WIP"; left `LOGGER.info` debug spam; simply stopped getting attention while unrelated issues kept landing on `main` |
| `7-lines-redux` | Same reducer, rebased onto a later `main` | Tip commit is literally `"#7 WIP attempt at merging"` — died mid-rebase against `main`'s new regex/multi-map/prefix-change features |
| `feature/tpwalke2/7-lines` | Strategy/Command pattern (`IReducerCommandFactory` per transition: 9 factory classes) | Tip has `return Stream.empty();` followed by ~90 lines of dead unreachable code from the pre-refactor reducer — **does not compile**. Open `// TODO removing line signs not updating` bug. Empty `Migrate{POI,Line}ToLineMarkerCommandFactory` shells |

All three converge on the same grouping idea (signs sharing a prefix + label become points of one line) and all three died the same way: a new grouping/reducer abstraction that had to keep pace with `main`'s independently-evolving sign-decision logic, and didn't. That's the constraint this plan designs around — extend the existing decision point in `SignManager`, add no parallel abstraction.

**Current model** (`core/signs/SignManager.java`): one sign = one marker, always. `addOrUpdateSign` (`SignManager.java:119-254`) decides add/update/remove/change-group purely from `(existing SignEntry, isPOIMarker)`; `MarkerGroupType` (`core/markers/MarkerGroupType.java`) only has `POI`; `BlueMapAPIConnector.addMarker` (`BlueMapAPIConnector.java:203-225`) silently no-ops for anything else. `MarkerAction` (`core/bluemap/actions/`) has `Add`/`Update`/`Remove`/`ChangeGroupMarkerAction` — the last one (`ChangeGroupMarkerAction.java`) bundles a remove+add into one dispatch so `ReactiveQueue`'s lack of ordering guarantees can't leave a marker duplicated across groups mid-transition.

## Goal

A marker group can be `type: LINE`. Signs sharing that group's prefix and the same parsed label become ordered points of one `de.bluecolored.bluemap.api.markers.LineMarker`. Fully player-editable in-game — no admin config beyond the marker group itself. Scope: open polylines only; closed/filled shapes are a separate future issue.

## Design

### 1. Config: `MarkerGroupType.LINE`, `lineWidth`/`lineColor`

`MarkerGroupType` gains `LINE`. `MarkerGroup` (`core/markers/MarkerGroup.java`) gains two trailing components: `int lineWidth`, `String lineColor` (hex, e.g. `"#FF0000FF"` — mirrors BlueMap's own `LineMarker` defaults: 2px, opaque red). `LoadingMarkerGroupV2` gains matching nullable `Integer lineWidth`/`String lineColor`; `ConfigProvider.convertToLoadedMarkerGroup` defaults them (`2`, `"#FF0000FF"`) when absent, same pattern as `offsetX`/`offsetY` today.

`ConfigProvider.validateMarkerGroups` gains a warning pass (never throws — a misconfigured line-only field must not crash the server, same as every other config-error path): if a `POI` group has an explicit non-default `lineWidth`/`lineColor` in the raw `LoadingMarkerGroupV2`, or a `LINE` group has an explicit `icon`/`offsetX`/`offsetY`, log a warning naming the group and field. Checked against the raw loading record (which still distinguishes "field omitted" via `null`), not the defaulted `MarkerGroup`.

`README.md`'s Marker Groups section gets `lineWidth`/`lineColor` entries alongside a `LINE` example.

### 2. Point order: `SignEntry.createdAtMillis`

Order = placement order. Can't be recovered from `ConcurrentMap` iteration and must survive restarts, so it's persisted: `SignEntry` (`core/signs/SignEntry.java`) gains `long createdAtMillis`, set once when a sign is first observed by `SignManager`, never recomputed afterward. No group-scoped sequence counter — a timestamp needs no coordination against other members of the same group, unlike an incrementing "next number for this group" scheme.

This is a persisted-shape change → `SignFileVersions` (`core/signs/persistence/SignFileVersions.java`) gains `V4`. Migration in the "Persistence migration" section below.

### 3. Grouping key and membership resolution

A line's key is `(parentMap, prefix, label)` — the same `(mapId, group)` axis `MarkerSetIdentifier` already uses, plus the label distinguishing multiple lines under one prefix. Two signs with the same prefix+label in *different* dimensions are different lines (already naturally scoped by `parentMap`, which doubles as BlueMap's `mapId`).

New pure-Java, unit-testable class `core/signs/LineGroupResolver.java`:

```java
public static List<SignEntry> members(Collection<SignEntry> allSigns, String parentMap, String prefix, String label) {
    return allSigns.stream()
        .filter(e -> parentMap.equals(e.key().parentMap()))
        .filter(e -> prefix.equals(SignEntryHelper.getPrefix(e)))
        .filter(e -> label.equals(SignEntryHelper.getLabel(e)))
        .sorted(Comparator.comparingLong(SignEntry::createdAtMillis))
        .toList();
}
```

Takes a plain `Collection<SignEntry>` (not `SignManager`'s cache type), so it's testable with a hand-built list — no Minecraft/BlueMap types in its signature, per `AGENTS.md`'s testable-core convention.

### 4. `MarkerAction`/`ActionFactory`: two new subtypes, generalized identifier

A line marker's dispatch always carries its *entire current point list* (every change is a full recompute), so add/update collapse into one type — no meaningful "add vs. update" distinction the way POI markers have:

- `SetLineMarkerAction` — `label`, `detail`, `List<LinePoint>` (new plain record `core/markers/LinePoint(int x, int y, int z)`), `lineWidth`, `lineColor`, and a **log-only** `boolean isFirstAppearance` flag (not a distinct subtype — exists purely so `logProcessingMessage` can say "Adding" vs. "Updating" without growing the `MarkerAction` hierarchy or the two switch statements beyond what a real behavioral difference requires).
- `RemoveLineMarkerAction` — just the identifier.

Both are keyed differently from POI markers: a POI marker's id is `x_y_z` (`MarkerIdentifier.getId()`); a line marker's id must be stable per-group, not per-position (a line's points move as members join/leave). `MarkerAction`'s field type widens from the concrete `MarkerIdentifier` to a small interface:

```java
public interface DispatchedMarkerIdentifier {
    MarkerSetIdentifier parentSet();
    String getId();
}
```

`MarkerIdentifier` (unchanged otherwise) implements it. New `record LineMarkerIdentifier(String label, MarkerSetIdentifier parentSet) implements DispatchedMarkerIdentifier { getId() -> "line:" + label }`. `MarkerAction` drops its `getX/Y/Z()` (only ever used for logging) in favor of exposing `getMarkerIdentifier(): DispatchedMarkerIdentifier`; `AddMarkerAction`/`UpdateMarkerAction`/`RemoveMarkerAction`/`ChangeGroupMarkerAction` need no changes beyond this — they already just pass a `MarkerIdentifier` to `super()`, which still satisfies the narrower interface. `BlueMapAPIConnector.logProcessingMessage` pattern-matches on `action.getMarkerIdentifier()` to log x/y/z for `MarkerIdentifier` or label/point-count for `LineMarkerIdentifier`.

`ActionFactory` gains `createSetLineAction(mapId, markerGroup, label, detail, points, isFirstAppearance)` and `createRemoveLineAction(mapId, markerGroup, label)`, following the existing `create*POIAction` pattern exactly.

### 5. `BlueMapAPIConnector`: building the actual `LineMarker`

New cases in `processMarkerAction`'s switch (`BlueMapAPIConnector.java:118-127`) and `logProcessingMessage`'s switch (`:153-160`) — required per `AGENTS.md`'s note that `MarkerAction` isn't sealed, so a missing case silently falls through to `default` instead of failing to compile.

```java
case SetLineMarkerAction setAction ->
        applyToMarkerSets(setAction.getMarkerIdentifier(), maps -> setLineMarker(setAction, maps));
case RemoveLineMarkerAction removeAction ->
        applyToMarkerSets(removeAction.getMarkerIdentifier(), maps -> removeMarkerById(removeAction.getMarkerIdentifier().getId(), maps));
```

`applyToMarkerSets` narrows its parameter type from `MarkerIdentifier` to `DispatchedMarkerIdentifier` (needs only `.parentSet()`, already on the interface). `removeMarker`'s body is extracted into an id-based `removeMarkerById(String id, Stream<...>)`, reused by both `RemoveMarkerAction` and `RemoveLineMarkerAction`.

```java
private static void setLineMarker(SetLineMarkerAction action, Stream<Map<String, Marker>> markerSetMaps) {
    if (action.getPoints().size() < 2) return; // defensive - SignManager should never dispatch below 2
    var line = new Line(action.getPoints().stream().map(p -> new Vector3d(p.x(), p.y(), p.z())).toList());
    var color = ColorUtils.parseHex(action.getLineColor()); // new plain-Java common/ColorUtils.java, unit-testable
    markerSetMaps.forEach(markers -> markers.put(action.getMarkerIdentifier().getId(),
            LineMarker.builder()
                    .label(action.getLabel())
                    .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                    .line(line)
                    .lineWidth(action.getLineWidth())
                    .lineColor(new Color(color[0], color[1], color[2], color[3]))
                    .build()));
}
```

`ColorUtils.parseHex(String hex) -> int[]{r,g,b,a}` stays plain Java (no BlueMap types) so it's unit-testable; only `BlueMapAPIConnector` converts the result into `de.bluecolored.bluemap.api.math.Color`, same "escape/convert at the point of use" pattern `HtmlUtils` already follows for `detail`.

Line detail text: the line's `detail` is the group label, shown once regardless of member count. (Originally spec'd as each member's own detail joined in point order - reverted because members sharing a line typically share the same bare label text, which duplicated the label once per member in the rendered popup. See `SignTransitionResolverTest`'s line-transition tests, which assert `detail == label`.)

### 6. Sign-role transitions: a lookup table, not a class hierarchy

Every sign change (edit, removal, or — see §7 — a config reload) reduces to a `(oldRepresentation, newRepresentation)` pair, where a representation is `null` (no group), `POI(group, label, detail)`, or `LINE(group, label)`. This is deliberately a plain lookup table in `SignManager`, not first-class `State`/`Transition` objects — that class-hierarchy shape is exactly what `feature/tpwalke2/7-lines`'s Strategy/Command pattern attempted and is the branch that ended up non-compiling.

| Old ＼ New | NONE | POI | LINE |
|---|---|---|---|
| **NONE** | no-op | dispatch Add POI | recompute line group **including** this sign; dispatch Set if ≥2 members, else no-op (line still incomplete) |
| **POI** | dispatch Remove POI | same group+label & text unchanged: no-op. Same group+label & text changed: dispatch Update. Different group/label: leave-effect + join-effect, bundled (see below) | leave-effect (Remove POI) + join-effect (recompute including this sign; Set if ≥2, else no-op) |
| **LINE** | recompute line group **excluding** this sign; dispatch Set if ≥2 remain, Remove if it drops below 2 (and a marker existed) | leave-effect (as above) + join-effect (Add POI) | same group+label: recompute (Set — refreshes detail/points); different group/label: leave-effect + join-effect |

"Leave-effect" and "join-effect" are each a `MarkerAction` or nothing — 0, 1, or 2 concrete dispatches per transition, computed synchronously before dispatch. They're bundled into one dispatched unit for the same reason `ChangeGroupMarkerAction` already bundles remove+add: `ReactiveQueue` gives no ordering guarantee between independently-submitted messages, so under load a join could run before a leave and transiently show a sign in two places. `ChangeGroupMarkerAction` is generalized from a fixed 2-action bundle into `GroupTransitionMarkerAction(List<MarkerAction> effects)` (0-2 entries); `BlueMapAPIConnector.processMarkerAction`'s existing `ChangeGroupMarkerAction` case becomes a `GroupTransitionMarkerAction` case that iterates `effects` inside the same synchronized method — same atomicity guarantee, generalized to a variable count.

`addOrUpdateSign` keeps its existing responsibilities (cache/`chunkIndex` maintenance, `WorldMap.UNKNOWN` playerId-preservation on update) and calls this table as its dispatch step, replacing the current `shouldAdd/Remove/UpdatePOIMarker` + prefix-change branch (`SignManager.java:142-253`).

Concurrency: synchronous recompute-then-dispatch, no new locking. Every dispatch carries the *complete* current point list, so `ReactiveQueue`'s serial draining plus BlueMap's put-by-id upsert makes a stale in-flight dispatch harmless even if overtaken by a fresher one.

### 7. Config reload (`/bluemap reload`) — fixes a real bug the current replay strategy has

This surfaced only while working out the reload path in detail, and applies to the *existing* reload mechanism too, not just lines — worth flagging plainly.

`SignManager.reloadSigns()` today (`SignManager.java:109-117`) clears `signCache`/`chunkIndex` and replays every sign through `addOrUpdateSign`, so every entry always takes the "add" branch (`existing` is always `null` post-clear) and a replayed add simply `Map.put`s over whatever marker was already there by the **same id**. That's silently safe today only because a POI marker's id is always `x_y_z`, position-based, and never changes between reloads. A line marker's id is `"line:" + label` — content-based, not position-based — so the *first* config change that causes a marker's id scheme to change between reloads (e.g. flipping a group's `type` from `POI` to `LINE`, or vice versa, with the same signs) leaves the *old* id's marker entry behind in BlueMap's `MarkerSet` map forever: replay only ever adds under the new id, nothing ever explicitly removes the old one. `clearMarkerSetsCache()` (already called from `reloadConfig()`) doesn't help — it only evicts this mod's own `MarkerSetIdentifier → MarkerSet` lookup cache, not the marker entries inside an existing `MarkerSet` that BlueMap itself owns.

Fix: stop clearing `signCache`/`chunkIndex` for reload. Instead, capture `oldPrefixGroupMap` before swapping in the new config, then for each currently-cached `SignEntry`, compute its representation under the **old** map and under the **new** map (using the sign's already-parsed text — reload does not, and never has, re-parsed cached sign text against a changed prefix/regex; that's the pre-existing "prefix rename needs a live re-edit" limitation from `../../agent-context/plans/marker-group-config-reload-plan.md`, unaffected by this change) and run that pair through the exact same transition table from §6. No separate "bulk resync" code path is needed: because the cache is never cleared, `LineGroupResolver.members(...)` always scans the *full, unchanged* sign cache regardless of which sign in a group is processed first — a type flip from `POI` to `LINE` sees every member's join-effect immediately compute the complete final point list on its first iteration (order-independent, later iterations for other members in the same group re-dispatch the identical, idempotent `Set`). This also drops the now-unnecessary `chunkIndex.clear()`/rebuild, since no sign keys actually change during a reload.

`reloadConfig()` (`SignManager.java:316-322`) becomes: swap config, then run the above per-sign old-vs-new-representation pass instead of calling `reloadSigns()`.

### 8. `LineMarker` fields not covered above

`minDistance`/`maxDistance`/`defaultHidden` are inherited (`DistanceRangedMarker`) and reused unchanged from `MarkerGroup` — no line-specific handling needed, same as today's POI path.

## Persistence migration (V3 → V4)

Freeze the current `SignEntry` shape as a new legacy model, `core/signs/persistence/models/SignEntryV3.java` (identical fields to today's `SignEntry`, no `createdAtMillis`) — mirrors how `SignEntryV2` already exists alongside the "current" model. The live `SignEntry` class gains `createdAtMillis`.

New `core/signs/persistence/loaders/Version4Converter.java`, mirroring `Version3Converter`:

```java
public static SignEntry convertToV4(SignEntryV3 entry, int indexInFile, long fileLastModifiedMillis) {
    return new SignEntry(entry.key(), entry.playerId(), entry.frontText(), entry.backText(),
            fileLastModifiedMillis + indexInFile);
}
```

No real placement history exists for pre-existing signs — line support didn't exist when they were created — so `createdAtMillis` for migrated entries is **arbitrary but stable**, derived from the region file's own last-modified time plus its array index, not a reconstruction of true history. This is deliberate and documented, not a bug: inventing a more "plausible-looking" value would be worse than being honest that pre-migration order is undefined.

Because `VersionedFileSignEntryLoader`/`RegionShardedSignEntryLoader` convert **per file**, independently and lazily (`AGENTS.md`'s persistence section), and a line group's members can legitimately live in different region files, two old signs in the same eventual group but different files can end up with a duplicate `createdAtMillis` after migration. Accepted: `LineGroupResolver.members`'s sort needs a deterministic secondary key for ties — sort by `(createdAtMillis, key.x(), key.y(), key.z())`. A whole-world-scan migration pass to guarantee cross-file-unique values was considered and rejected: it's new architecture for a narrow edge of a narrow edge (pre-existing signs, spanning multiple regions, in a group later retyped to `LINE`).

`VersionedFileSignEntryLoader.loadSignEntries` (`:23-63`) gains a `V3` branch analogous to the existing `V2` branch: parse as `SignEntryV3[]`, convert each via `Version4Converter.convertToV4` (index = array position, file mtime read once via `Files.getLastModifiedTime` — falls back to `0L` on any `IOException` rather than throwing, consistent with the "never crash the server" rule), back up the original as `.v3.bak` (same `FileUtils.createBackup` convention as `.v2.bak`) before overwriting. `RegionShardedSignEntryWriter.writeRegionFile` (`:55-66`) writes `SignFileVersions.V4` going forward.

## Known limitations (out of scope for v1)

- Reordering a line: none supported. `createdAtMillis` is immutable once assigned; reordering means removing and replacing signs in the desired order.
- A prefix *rename* in config (not a type change) still requires a live sign re-edit to take effect, same pre-existing limitation `marker-group-config-reload-plan.md` documents for POI groups — reload alone re-resolves already-parsed prefixes against the new config, it doesn't re-parse sign text.
- Closed/filled shape markers (BlueMap's `Shape` marker) — separate future issue.

## Changes (files)

1. `core/markers/MarkerGroupType.java` — add `LINE`.
2. `core/markers/MarkerGroup.java` — add `lineWidth`, `lineColor`; update `DEFAULT_POI_GROUP`, `withType`.
3. `config/persistence/LoadingMarkerGroupV2.java` — add nullable `lineWidth`/`lineColor`.
4. `config/ConfigProvider.java` — default `lineWidth`/`lineColor` in `convertToLoadedMarkerGroup`; extend `validateMarkerGroups` with the type/field-mismatch warning; same for `loadV1Config`'s inline `MarkerGroup` construction.
5. `core/signs/SignEntry.java` — add `createdAtMillis`; update `withKey`, `equals`/`hashCode`/`toString`.
6. `core/signs/persistence/SignFileVersions.java` — add `V4`.
7. New `core/signs/persistence/models/SignEntryV3.java` — frozen copy of today's `SignEntry` shape.
8. New `core/signs/persistence/loaders/Version4Converter.java`.
9. `core/signs/persistence/loaders/VersionedFileSignEntryLoader.java` — add the `V3` conversion branch, `.v3.bak` backup.
10. `core/signs/persistence/RegionShardedSignEntryWriter.java` — write `V4`.
11. New `core/signs/LineGroupResolver.java`.
12. `core/bluemap/actions/MarkerAction.java` — widen to `DispatchedMarkerIdentifier`, drop `getX/Y/Z()`.
13. New `core/markers/DispatchedMarkerIdentifier.java`, `core/markers/LineMarkerIdentifier.java`, `core/markers/LinePoint.java`.
14. New `core/bluemap/actions/SetLineMarkerAction.java`, `RemoveLineMarkerAction.java`.
15. `core/bluemap/actions/ChangeGroupMarkerAction.java` → replaced by new `GroupTransitionMarkerAction.java` (`List<MarkerAction> effects`).
16. `core/bluemap/actions/ActionFactory.java` — add `createSetLineAction`, `createRemoveLineAction`; `createChangeGroupPOIAction` → build a `GroupTransitionMarkerAction`.
17. `core/bluemap/BlueMapAPIConnector.java` — new switch cases (both switches), `setLineMarker`, `removeMarkerById` extraction, `applyToMarkerSets` parameter widened, `processChangeGroupAction` → `processGroupTransitionAction` iterating `effects`.
18. New `common/ColorUtils.java`.
19. `core/signs/SignManager.java` — representation lookup table replacing `shouldAdd/Remove/UpdatePOIMarker` + the prefix-change branch; `reloadConfig()` rewritten per §7 (no more `signCache.clear()`/`reloadSigns()` for config reload).
20. `README.md` — document `LINE` type, `lineWidth`, `lineColor`.
21. `gradle.properties` — `mod_version` bump (new marker type + persisted-format change).
22. `AGENTS.md` — update marker groups/config, action-factory switch-case, persistence versioning, testable-code,
    and core-pipeline sections for the `LINE` feature.

New tests (`src/test/java/.../core/signs/LineGroupResolverTest.java`, `.../core/markers/MarkerGroupTest` or similar for `ColorUtils`, `.../persistence/loaders/Version4ConverterTest.java`): membership scanning across dimensions/prefixes/labels, tie-breaking on duplicate `createdAtMillis`, hex color parsing (valid/invalid input), V3→V4 conversion including the arbitrary-timestamp behavior. `SignManager`'s transition table itself stays untestable without a live BlueMap/Minecraft harness (same as today), verified manually.

## Verification

- `./gradlew test` — new tests pass alongside the existing suite.
- `./gradlew build` — full build succeeds.
- Manual, via `./gradlew runServer`:
  1. Place 3 signs with the same `[line]`-style prefix and label at different times; confirm no marker appears until the 2nd sign, then a line grows to include the 3rd.
  2. Break the middle sign; confirm the line reconnects the remaining two. Break down to 1 remaining sign; confirm the marker disappears.
  3. Edit one sign's prefix from a `LINE` group to a `POI` group and back; confirm no duplicate/orphaned markers in either direction.
  4. Edit a marker group's `type` in `BMSM-Core.json` from `POI` to `LINE` (existing signs, unchanged text) and run `/bluemap reload`; confirm the POI markers disappear and a line marker appears with no leftover POI marker in BlueMap's web UI. Repeat in reverse.
  5. Set an invalid `lineColor` hex string in config; confirm the server logs a warning and falls back to the default rather than crashing.
  6. Restart the server after placing lines spanning two region files (far-apart signs, same prefix+label); confirm the line still renders correctly after the region-sharded reload.
  7. Start from a pre-V4 world save (signs from before this change); confirm signs load, migrate to V4, and any newly-configured `LINE` group involving old signs renders (with documented arbitrary ordering) rather than erroring.
