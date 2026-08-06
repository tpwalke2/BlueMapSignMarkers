# Marker group config reload on `/bluemap reload`

## Context

Today, per `agent-context/context/config-and-persistence.md`: "Config is not hot-reloadable — a config file edit
requires a server restart." Three independent snapshots of marker-group config are taken once and never refreshed:

1. `ConfigManager.coreConfig` (`config/ConfigManager.java:10`) — `static final`, loaded once at class-init.
2. `SignHelper.signLinesParser` (`core/signs/SignHelper.java:15`) — `static final`, built once from
   `ConfigManager.get().getMarkerGroups()` at class-init. Used to parse sign text into `SignLinesParseResult`
   whenever a `SignEntry` is built (player edit, block-entity load).
3. `SignManager.prefixGroupMap` (`core/signs/SignManager.java:64,67-76`) — built once in the singleton constructor,
   plus one `MarkerSetIdentifierCollection`/`ActionFactory` pair built alongside it. Used to resolve a cached sign's
   prefix to its current `MarkerGroup` (icon, name, offsets, visibility, distances) on every add/update/remove
   dispatch.

`SignManager` already implements `IResetHandler.reset()` → `reloadSigns()`, fired from
`BlueMapAPIConnector.onEnable` whenever `markerActionQueue.isShutdown()` is true — i.e. after a prior `onDisable`.
Per `core-pipeline.md:140-144`, this is "why a BlueMap reload replays the entire sign cache rather than assuming
stale `MarkerSet` state is still valid" — the mod already treats an `onDisable`→`onEnable` cycle as BlueMap's
`/bluemap reload` signal. `onEnable` itself needs no change: it already fires `reset()` unconditionally through the
`IResetHandler` abstraction on every such cycle; the extension point is what `reset()` *does*, not `onEnable`.

`reloadSigns()` clears `signCache`/`chunkIndex` before replaying, so every cached sign takes the **Add** branch of
`addOrUpdateSign`'s decision table on replay (`existing` is always `null` post-clear) — it re-dispatches
`AddMarkerAction` for everything, and `BlueMapAPIConnector.addMarker` does a `Map.put` keyed by position, so a
replayed "add" overwrites whatever marker was already there. This means replay *already* has the right shape to
pick up changed icon/offset/distance/visibility values for existing signs — the only reason it doesn't today is
that `prefixGroupMap` (and the parser, and the identifier cache) are frozen at construction time. Fixing that
staleness is the entire scope of this change.

## Goal

Satisfy: *as a server operator, when I edit `BMSM-Core.json` and run `/bluemap reload`, markers reflect the new
group definitions (icon, name, offsets, visibility, distances, matchType, prefix) without a server restart.*

## Design

### `ConfigManager` — make the singleton reloadable

```java
private static volatile BMSMConfigV2 coreConfig = loadCoreConfig();

public static BMSMConfigV2 get() { return coreConfig; }

public static synchronized void reload() {
    coreConfig = loadCoreConfig();
}
```

`loadCoreConfig()` is unchanged — it already re-reads from disk via `ConfigProvider.loadConfig()` and falls back to
defaults on failure, so `reload()` is just "run that again and swap the reference." `volatile` is enough for safe
publication (the new `BMSMConfigV2` is fully constructed before the reference is swapped); no reader needs a lock.

### `SignHelper` — rebuild the cached parser

```java
private static volatile SignLinesParser signLinesParser = buildParser();

private static SignLinesParser buildParser() {
    return new SignLinesParser(Arrays.asList(ConfigManager.get().getMarkerGroups()));
}

public static void reloadParser() {
    signLinesParser = buildParser();
}
```

Needed so any sign parsed *after* the reload (a player edit, or a chunk loading in) uses the new prefixes/matchType,
not stale ones — without this, a config edit that changes `matchType` or `prefix` would parse newly-touched signs
inconsistently with what `SignManager.prefixGroupMap` now expects.

### `SignManager` — rebuild `prefixGroupMap`, `ActionFactory`, `MarkerSetIdentifierCollection` on reset

Extract the constructor's group-map-building loop into a private method, reuse it from both the constructor and a
new `reloadConfig()`:

```java
private volatile Map<String, MarkerGroup> prefixGroupMap;
private volatile ActionFactory actionFactory;

private SignManager() {
    prefixGroupMap = buildPrefixGroupMap();
    actionFactory = new ActionFactory(new MarkerSetIdentifierCollection());
    blueMapAPIConnector = new BlueMapAPIConnector();
    blueMapAPIConnector.addResetHandler(this);
}

private static Map<String, MarkerGroup> buildPrefixGroupMap() {
    var groups = ConfigManager.get().getMarkerGroups();
    Map<String, MarkerGroup> result = new TreeMap<>();
    for (var group : groups) {
        if (result.containsKey(group.prefix())) {
            LOGGER.warn("Duplicate marker group prefix found: {}", group.prefix());
            continue;
        }
        result.put(group.prefix(), group);
    }
    return result;
}

@Override
public void reset() {
    reloadConfig();
    reloadSigns();
}

private void reloadConfig() {
    LOGGER.info("Reloading marker group configuration...");
    ConfigManager.reload();
    SignHelper.reloadParser();
    prefixGroupMap = buildPrefixGroupMap();
    actionFactory = new ActionFactory(new MarkerSetIdentifierCollection());
}
```

`reloadConfig()` runs before `reloadSigns()` so the replay dispatches with the fresh group values.

Rebuilding `actionFactory`/`MarkerSetIdentifierCollection` (rather than reusing the existing one) closes the "cache-key
growth risk" flagged in `plans/codebase-review-2026-07-11.md:248-250`: `MarkerSetIdentifier`/`markerSetsCache` key on
the *entire* `MarkerGroup` record by value, so a changed icon/offset/distance produces a new, never-evicted entry in
a long-lived cache — starting a fresh `MarkerSetIdentifierCollection` on every reload means it never accumulates
entries for group values from before the last reload. This pairs with `BlueMapAPIConnector.resetQueue()`, which
already gives `markerSetsCache` a clean slate on every reload — now both identifier caches reset together.

`prefixGroupMap`/`actionFactory` change from `final` to `volatile` fields — reassigned wholesale (never mutated in
place), so `volatile` alone gives readers on other threads (mixins, `ReactiveQueue` worker threads) a consistent,
fully-built reference without needing a lock.

### `SignEntryHelper.isMarkerType` — guard against a prefix no longer configured

```java
public static boolean isMarkerType(SignEntry signEntry, Map<String, MarkerGroup> prefixGroupMap, MarkerGroupType markerGroupType) {
    var prefix = getPrefix(signEntry);
    if (prefix == null) return false;
    var group = prefixGroupMap.get(prefix);
    return group != null && group.type() == markerGroupType;
}
```

Today `prefixGroupMap` is only ever built from the same config a cached sign was parsed against, so `getPrefix`
always resolves — `prefixGroupMap.get(prefix)` can never be `null`, and the direct `.type()` call is safe. Once
config is reloadable, an operator can rename or delete a group's prefix entirely, and existing cached signs still
carry the *old* prefix string — this NPEs today without the guard. Unit-testable (`SignEntryHelper` is plain Java,
already covered per `testing.md`); add a case to a new `SignEntryHelperTest` (see Tests below).

## Known limitation (not fixed here)

If a group's **`name`** changes (the field BlueMap uses as the `MarkerSet`/layer label,
`BlueMapAPIConnector.getMarkerSets:190-200`) while its `prefix` stays the same, replay adds the marker into a
*new*-named `MarkerSet` but nothing removes it from the old-named one (nothing in `SignManager` remembers which
`MarkerGroup` a cached sign was last dispatched under, only its prefix string) — the marker briefly appears under
both the old and new set names until the old one is manually removed in BlueMap's web UI, or the sign is edited or
the server restarted. Fixing this correctly means tracking, per cached sign, the last-dispatched `MarkerGroup`
value (not just its prefix) — a larger change than "pick up config edits on reload" calls for. Renaming a group's
`prefix` (what text must appear on the sign) already goes through the pre-existing remove-then-add path
(`addOrUpdateSign`'s prefix-changed branch) *only when a player re-edits that specific sign* — reload's clear-and-replay
doesn't hit that branch (every replayed entry looks "new" against the just-cleared cache), so a prefix rename alone,
with no in-game re-edit, also leaves the marker under its old prefix's group semantics until the operator edits or
removes the physical sign. Both are edge cases outside "change icon/offset/visibility/distances and see it reflected
on reload," the case this plan targets.

## Out of scope

- Fixing `SignManager`'s non-`volatile` singleton `instance` field (double-checked locking bug flagged separately in
  `plans/codebase-review-2026-07-11.md`) — pre-existing, unrelated to config reload.
- A config file watcher / auto-reload without `/bluemap reload` — not requested; this rides the existing
  `IResetHandler` reset signal, the only reload trigger the mod has.
- Fully resolving the group-rename duplicate-marker-set edge case above.

## Changes (files)

1. **`config/ConfigManager.java`** — `coreConfig` field → `volatile`, add `public static synchronized void reload()`.
2. **`core/signs/SignHelper.java`** — `signLinesParser` field → `volatile`, extract `buildParser()`, add
   `public static void reloadParser()`.
3. **`core/signs/SignManager.java`** — extract `buildPrefixGroupMap()` (static), `prefixGroupMap`/`actionFactory`
   fields → `volatile` (drop `final`), add `reloadConfig()`, call it from `reset()` before `reloadSigns()`.
4. **`core/signs/SignEntryHelper.java`** — null-guard `isMarkerType` against an unrecognized prefix.
5. **New test** `src/test/java/.../core/signs/SignEntryHelperTest.java` — covers `getPrefix`/`isMarkerType`/
   `getLabel`/`getDetail` (currently untested per `testing.md:68`), including the new case: a prefix not present in
   `prefixGroupMap` returns `false` from `isMarkerType` rather than throwing.

No `mod_version`/`SignFileVersions` bump — no persisted-format change, this is runtime-cache-only.

## Verification

- `./gradlew test` — new `SignEntryHelperTest` passes alongside the existing suite.
- `./gradlew build` — full build still succeeds.
- Manual, via `./gradlew runServer` with BlueMap loaded (`ConfigManager`/`SignManager`/`SignHelper`/
  `BlueMapAPIConnector` are game/API-coupled, no automated coverage possible per `testing.md`):
  1. Place a `[poi]` sign, confirm its marker appears with the default icon/offset. Edit `BMSM-Core.json`'s `[poi]`
     group: change `icon`, `offsetX`/`offsetY`, `defaultHidden`, `minDistance`/`maxDistance`. Run `/bluemap reload`.
     Confirm the marker updates in place (icon/position-offset/visibility/distance-culling) with no server restart
     and no duplicate marker appears.
  2. Add a second marker group to the config with a new prefix, run `/bluemap reload`, place a sign using the new
     prefix, confirm it's recognized without a restart (proves `SignHelper`'s parser picked up the new group).
  3. Remove a marker group's prefix entirely from config (with an existing sign still using it), run
     `/bluemap reload`, confirm no exception/crash in the server log (the `isMarkerType` null-guard) — the sign's
     marker is expected to remain in BlueMap until the sign itself is edited/removed (documented limitation above).
  4. Rename a group's `name` field only, run `/bluemap reload`, confirm the marker appears under the new set name
     (and note, per the documented limitation, whether it also still appears under the old set name).
