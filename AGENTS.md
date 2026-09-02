# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Cursor, Copilot, Aider, etc.) when working with code
in this repository.

## Project overview

BlueMap Sign Markers is a server-side Fabric mod for Minecraft. It watches in-game signs and, when a sign's text
matches a configured prefix (e.g. `[poi]`), creates/updates/removes a corresponding marker on a BlueMap map. Signs
are tracked persistently so markers survive server restarts, and multiple "marker groups" (each with its own prefix,
match rule, icon, and visibility rules) can be configured at once. See `README.md` for the end-user configuration
format (`config/bluemapsignmarkers/BMSM-Core.json`).

## Build / dev / test commands

This is a Fabric Loom Gradle project (Java 25 toolchain, targeting Minecraft version in `gradle.properties`).

- `./gradlew build` — compile, run unit tests, and build the mod jar (output in `build/libs/`); fails if any test fails
- `./gradlew test` — run the unit test suite only (JUnit 5, under `src/test/java`)
- `./gradlew test --tests "*.SignLinesParserTest"` — run a single test class
- `./gradlew runServer` — launch a dev Minecraft server (uses the `run/` directory as its working dir) with the mod
  and BlueMap loaded, for manual in-game testing
- `./gradlew runClient` — launch a dev Minecraft client

Unit tests only cover plain-Java logic that has no Minecraft/Fabric/BlueMap API types in its signature (see
Architecture below for which packages qualify). Anything that touches live Minecraft/Fabric/BlueMap types (mixins,
the mod entrypoint, `BlueMapAPIConnector`) has no automated coverage and is verified manually via `runServer` and
placing signs in-game.

Version/dependency info (Minecraft, Fabric loader/API, BlueMap, Java version) all lives in `gradle.properties` — bump
values there, not in `build.gradle`. `mod_version` follows `<minecraft_version>-<mod_semver>`.

CI (`.github/workflows/build.yml`) runs the unit tests and then `./gradlew build` on push/PR to `main` and
`releases/**`; either failing fails the job. `.github/workflows/publish.yml` is manually dispatched, also runs the
unit tests first (a failure blocks publishing), then runs `./gradlew modrinth` to publish to Modrinth (alpha from
branch dispatch, release from a `v*` tag dispatch with `-PisRelease`).

Both workflows also have a `summarize test results` step right after `run unit tests` (`if: always()`, so it still
runs when tests fail). It sums the `tests`/`failures`/`errors`/`skipped` attributes out of Gradle's JUnit XML reports
(`build/test-results/test/*.xml`) with plain shell (`sed`, no `xmllint` or third-party action) and writes a markdown
table to `$GITHUB_STEP_SUMMARY`, so pass/fail counts show up on the workflow run's summary page instead of only in
the raw log. This was a deliberate choice over `checks: write`-based reporter actions, which don't get that
permission on PRs from forks by default in a public repo.

## Architecture

### Entry point and Minecraft hooks

`BlueMapSignMarkersMod` (`DedicatedServerModInitializer`) wires four lifecycle hooks:
- `SERVER_STARTING` → `SignProvider.loadSigns(...)` reads the world's persisted, region-sharded sign storage
  (migrating a pre-sharding single `signs.json` on first boot after an upgrade)
- `SERVER_STOPPING` → `SignProvider.saveSigns(...)` then `SignManager.stop()`
- `BLOCK_ENTITY_LOAD` → for any loaded `SignBlockEntity`, calls `SignManager.addOrUpdate(...)`
- `CHUNK_LOAD` → reconciles signs the mod's cache still knows about in a loading chunk against what's actually
  there; if a tracked sign's block is gone (e.g. its region file was deleted/regenerated externally while
  unloaded), calls `SignManager.remove(...)` for it. Addresses GitHub issue #110.

Sign state is stored per-world, region-sharded: one JSON file per (dimension, 32x32-chunk region) under
`{server_root}/bluemapsignmarkers/{level}/{dimension_namespace}/{dimension_path}/r.{regionX}.{regionZ}.json`. A
pre-sharding single `config/bluemapsignmarkers/<world-save-name>/signs.json` is migrated in place on first boot
after upgrading (backed up, not deleted).

Two Mixins (`src/main/resources/bluemapsignmarkers.mixins.json`) catch the events the lifecycle hooks above can't:
- `SignBlockEntityInject` injects into `SignBlockEntity.updateSignText` (a player edits a sign) → `SignManager.addOrUpdate`
- `AbstractBlockInject` injects into `BlockBehaviour.affectNeighborsAfterRemoval` (a sign block is removed) →
  `SignManager.remove`

### Core pipeline: sign text → marker action

1. **`SignHelper`** builds a `SignEntry` (immutable snapshot: position/dimension key, player id, parsed front/back
   text) from a `SignBlockEntity`, running sign text through a `SignLinesParser` configured with the current
   `MarkerGroup`s. `SignHelper.reloadParser()` rebuilds that parser from the current config; it's called on every
   config reload (see below) so a sign parsed after `/bluemap reload` picks up an edited prefix/matchType.
2. **`SignManager`** (singleton, holds a `ConcurrentMap<SignEntryKey, SignEntry>` cache of all known signs) is the
   decision point. For a given sign it computes a `Representation` (group, label, detail; `null` if no group
   matches) both before and after the change, then looks up the (old, new) `Representation` pair in a transition
   table (`SignTransitionResolver.computeTransitionAction`) to get the single `MarkerAction` to dispatch — covering
   plain add/update/remove,
   a prefix change moving a sign between groups, and a `POI`↔`LINE` group `type` flip, all as the same kind of
   representation diff. `LINE` groups additionally dispatch `SetLineMarkerAction`/`RemoveLineMarkerAction`/
   `GroupTransitionMarkerAction` (built via `ActionFactory`) when a sign joins/leaves a line (see
   `LineGroupResolver` below). It also implements `IResetHandler.reset()`, which BlueMap fires on `/bluemap reload`:
   reloads config (`ConfigManager.reload()`, `SignHelper.reloadParser()`, rebuilding its prefix→group lookup and
   `ActionFactory`), captures the prefix→group map as it was before the swap, then for each cached `SignEntry` runs
   the same old-vs-new `Representation` diff through `SignTransitionResolver.computeTransitionAction` — so an edited marker-group's
   icon/offset/visibility/prefix/type takes effect without a server restart, and without leaving an orphaned marker
   behind when a sign's marker id scheme changes between reloads. `signCache`/`chunkIndex` are never cleared for a
   reload since no sign keys change. The `Representation` record and the transition table itself live in
   `SignTransitionResolver` (a static-utility class, same shape as `LineGroupResolver`), not on `SignManager` —
   `SignManager` can't be unit tested directly (its constructor builds a `BlueMapAPIConnector`, which touches live
   `BlueMapAPI` static state), but the transition table has no Minecraft/Fabric/BlueMap types in its signature, so
   extracting it makes it directly testable (`SignTransitionResolverTest`).
3. **`BlueMapAPIConnector`** owns the `ReactiveQueue<MarkerAction>` and all actual BlueMap API calls. Because the
   BlueMap API is only available while BlueMap itself is enabled, actions are queued and only drained
   (`markerActionQueue.process()`) while `BlueMapAPI.getInstance().isPresent()`; `BlueMapAPI.onEnable`/`onDisable`
   start/stop draining and clear/rebuild the marker-set cache. `MarkerSet`s are looked up/created per
   `MarkerSetIdentifier` (map id + marker group), cached in `markerSetsCache` as `MappedMarkerSet` (pairing the
   `MarkerSet` with the real `BlueMapMap.getId()` it came from — needed for the per-map render-bounds gating below).

`ReactiveQueue<T>` (`core/reactive`) is a small generic building block: an unbounded queue plus a "should I run right
now" predicate, draining onto a fixed thread pool sized to `availableProcessors()`. It isn't BlueMap-specific — reuse
it if another part of the mod needs the same "buffer while a dependency is unavailable" behavior.

### Per-map render-bounds gating

BlueMap maps can restrict what terrain they render via a `render-mask` (box/circle/ellipse/polygon shapes, each
optionally `subtract`) in that map's own `config/bluemap/maps/<id>.conf` — BlueMap's API exposes no bounds accessor,
so `RenderMaskEvaluator` (`core/bounds`, plain Java, no Minecraft/Fabric/BlueMap types) reads and evaluates that file
directly, matching BlueMap's own `CombinedMask` algorithm (last-entry-wins reverse scan; a mask fails open —
treated as unbounded — on any missing/unreadable/unparseable config or unmatched map id). `RenderMaskEvaluator.load`
returns a reusable `RenderMask`; `BlueMapAPIConnector` caches one per real map id in `renderMaskCache`, invalidated
alongside `markerSetsCache` (config reload, genuine BlueMap disable/enable).

A sign's marker only exists on a given map if its position (POI) or at least one member point (LINE/SHAPE,
all-or-nothing — no per-point clipping) is inside that map's render bounds. `applySingleAction` gates Add/Update/
SetLine/SetShape actions through `prepareGated`, which tests the action's point(s) against each map's
`RenderMask` and, on a gate failure, actively removes the marker id from that map instead of skipping the effect —
this is what sweeps a marker that already existed on a now-out-of-bounds map before this feature shipped (reusing
`SignManager.reset()`'s existing reload-forced re-dispatch of every sign). Explicit remove actions
(Remove/RemoveLine/RemoveShape) go through `prepareUngated` instead and apply unconditionally on every map, no
gating — the sign's representation is genuinely leaving, independent of bounds. See
`agent-context/plans/map-bounds-filtering-plan.md` for the full design and rationale, including why a plain server
restart alone does not trigger the upgrade sweep (only a genuine BlueMap disable/enable, or an explicit
`/bluemap reload`, does).

### Marker groups and config

`MarkerGroup` (record: prefix, matchType, type, name, icon, offsetX/Y, defaultHidden, minDistance/maxDistance,
lineWidth, lineColor, fillColor, sorting, toggleable, depthTest, cssClasses) is the unit of configuration described
in `README.md`. `type` (`MarkerGroupType`: `POI`, `LINE`, or `SHAPE`) picks which kind of marker the group's signs produce;
`lineWidth`/`lineColor` apply to `LINE`/`SHAPE` groups (setting them on a `POI` group is a warning, not an error).
`sorting`/`toggleable` are thin BlueMap `MarkerSet` passthroughs (menu order, hideability) that apply to every group
type; `depthTest` (terrain occlusion) is `LINE`/`SHAPE`-only and `cssClasses` (custom.css hooks) is `POI`-only, each
resolved in `ConfigProvider` and wired into the corresponding BlueMap builder call in `BlueMapAPIConnector`.
`ConfigManager` lazily loads a singleton `BMSMConfigV2`
via `ConfigProvider` from `config/bluemapsignmarkers/BMSM-Core.json`, creating sane defaults (a single `[poi]` group)
if the file is missing or fails to load. `SignLinesParser` matches sign text against groups using either
`STARTS_WITH` or `REGEX` (see `MarkerGroupMatchType`) — note that `REGEX` uses `String.matches(...)`, which requires
the *entire* line to match the pattern (unlike `STARTS_WITH`, a regex prefix can't share its line with label text).

For `LINE` groups, `LineGroupResolver` finds all signs sharing a group's prefix and the same label (the rest of the
sign text after the prefix) — that shared (group, label) is a line's membership key. A line marker only appears once
2+ members exist; it's removed once membership drops back to 1 or 0. Point ordering within a line follows
`SignEntry.createdAtMillis` (insertion order, not spatial order — see "Known limitations" in
`agent-context/plans/line-markers/spec.md`).

### Sign persistence and versioning

Sign state is stored per-world, region-sharded (one file per dimension + 32x32-chunk region — see "Entry point"
above), with each region file wrapped in a `VersionedSignFile` envelope (`{version, data}`) so the format can evolve
without breaking old saves. `SignProvider.loadSigns` checks whether the storage root already has region files
(`RegionShardedSignEntryLoader.hasSignData`); if so, it loads every region file the same version-aware way as
before sharding — the versioned-file loader (`VersionedFileSignEntryLoader`, handling V2→V3 migration via
`Version3Converter`, V3→V4 migration via `Version4Converter` (adds `createdAtMillis`, needed to order points within
a line marker; backfilled for pre-V4 entries), and current V4 files directly), falling back to
`Version1SignEntryLoader` for pre-versioning files. If no region files exist yet, `LegacySignFileMigrator` reads a
pre-sharding single `signs.json` (if present) through that same version chain, writes it out region-sharded, and
backs up the legacy file (renamed, not deleted)
only once every expected region file is confirmed on disk. When adding a new persisted field, bump
`SignFileVersions` and add a loader/converter rather than changing an existing version's shape in place — old
region files (or a not-yet-migrated legacy `signs.json`) on live servers must keep loading.

### Adding a new marker/BlueMap action

New `MarkerAction` subtypes go through `ActionFactory` (construction) and need a `case` arm added in both
`BlueMapAPIConnector.processMarkerAction`'s switch and `logProcessingMessage`'s switch — `MarkerAction` is a plain
abstract class (not sealed), so a missing case silently falls through to the `default` branch instead of failing to
compile. Line markers add `SetLineMarkerAction` (create/update a line's rendered points), `RemoveLineMarkerAction`
(a line drops back below 2 members), and `GroupTransitionMarkerAction` (a sign's representation changes id scheme
between reloads, e.g. a group's `type` flipping `POI`↔`LINE` — dispatches an explicit remove of the old marker
alongside the new one so nothing is orphaned in BlueMap's web UI); `GroupTransitionMarkerAction` replaced the earlier
`ChangeGroupMarkerAction`.

### Testable vs. game-coupled code

When adding logic, prefer keeping it in plain Java classes with no Minecraft/Fabric/BlueMap API types in their
signature (like `SignLinesParser`/`ParsingContext`, `SignEntry`/`SignEntryHelper`, `SignChunkKey`/`SignChunkIndex`,
`MarkerGroup`/`MarkerGroupMatchType`, `ConfigManager`/`ConfigProvider`, `ReactiveQueue`, `HtmlUtils`, `FileUtils`,
the persistence loaders/converters (including `Version1SignEntryLoader`, `Version4Converter`),
`ActionFactory`/`MarkerSetIdentifierCollection`, `LineGroupResolver`, `SignTransitionResolver`, `ColorUtils`,
`DispatchedMarkerIdentifier`/`LineMarkerIdentifier`/`LinePoint`, `RenderMaskEvaluator`) — these can be unit tested
directly (see
`src/test/java/.../core/signs/SignLinesParserTest.java` for the pattern).
Code that must reference game types (`SignHelper`, the mixins, `BlueMapSignMarkersMod`, `BlueMapAPIConnector`)
should stay thin glue around the testable core, since it can only be verified manually via `runServer`.

`BlueMapAPIConnector` escapes sign text (`HtmlUtils.toHtmlDetail`, in `common`) before it reaches BlueMap's POI
marker `detail` field — BlueMap renders `detail` as raw HTML (unlike `label`, which BlueMap escapes itself), and
sign text is player-controlled, so this closes a live XSS vector. See `agent-context/plans/html-detail-escaping-plan.md` for the
design. Persisted sign data stays raw/unescaped; escaping happens only at this BlueMap API call site.

## Planning documents

Design/implementation plans for larger pieces of work are written to the `agent-context/plans/` folder before being implemented,
so they can be reviewed independently of the eventual code change.

## Agent skills

### Issue tracker

Issues and specs live as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
