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

`BlueMapSignMarkersMod` (`DedicatedServerModInitializer`) wires three lifecycle hooks:
- `SERVER_STARTING` → `SignProvider.loadSigns(...)` reads the world's persisted `signs.json`
- `SERVER_STOPPING` → `SignProvider.saveSigns(...)` then `SignManager.stop()`
- `BLOCK_ENTITY_LOAD` → for any loaded `SignBlockEntity`, calls `SignManager.addOrUpdate(...)`

The markers file path is per-world: `config/bluemapsignmarkers/<world-save-name>/signs.json`.

Two Mixins (`src/main/resources/bluemapsignmarkers.mixins.json`) catch the events the lifecycle hooks above can't:
- `SignBlockEntityInject` injects into `SignBlockEntity.updateSignText` (a player edits a sign) → `SignManager.addOrUpdate`
- `AbstractBlockInject` injects into `BlockBehaviour.affectNeighborsAfterRemoval` (a sign block is removed) →
  `SignManager.remove`

### Core pipeline: sign text → marker action

1. **`SignHelper`** builds a `SignEntry` (immutable snapshot: position/dimension key, player id, parsed front/back
   text) from a `SignBlockEntity`, running sign text through a `SignLinesParser` configured with the current
   `MarkerGroup`s.
2. **`SignManager`** (singleton, holds a `ConcurrentMap<SignEntryKey, SignEntry>` cache of all known signs) is the
   decision point: given a new/updated `SignEntry`, it figures out whether this is an add, update, remove, or
   prefix-change (remove-then-add into a different marker group) relative to what's cached, then dispatches the
   corresponding `MarkerAction` (`AddMarkerAction`/`UpdateMarkerAction`/`RemoveMarkerAction`, built via
   `ActionFactory`) to the BlueMap connector. It also implements `IResetHandler.reset()` to replay the whole sign
   cache when BlueMap resets its state.
3. **`BlueMapAPIConnector`** owns the `ReactiveQueue<MarkerAction>` and all actual BlueMap API calls. Because the
   BlueMap API is only available while BlueMap itself is enabled, actions are queued and only drained
   (`markerActionQueue.process()`) while `BlueMapAPI.getInstance().isPresent()`; `BlueMapAPI.onEnable`/`onDisable`
   start/stop draining and clear/rebuild the marker-set cache. `MarkerSet`s are looked up/created per
   `MarkerSetIdentifier` (map id + marker group) and cached in `markerSetsCache`.

`ReactiveQueue<T>` (`core/reactive`) is a small generic building block: an unbounded queue plus a "should I run right
now" predicate, draining onto a fixed thread pool sized to `availableProcessors()`. It isn't BlueMap-specific — reuse
it if another part of the mod needs the same "buffer while a dependency is unavailable" behavior.

### Marker groups and config

`MarkerGroup` (record: prefix, matchType, type, name, icon, offsetX/Y, defaultHidden, minDistance/maxDistance) is the
unit of configuration described in `README.md`. `ConfigManager` lazily loads a singleton `BMSMConfigV2` via
`ConfigProvider` from `config/bluemapsignmarkers/BMSM-Core.json`, creating sane defaults (a single `[poi]` group) if
the file is missing or fails to load. `SignLinesParser` matches sign text against groups using either `STARTS_WITH`
or `REGEX` (see `MarkerGroupMatchType`) — note that `REGEX` uses `String.matches(...)`, which requires the *entire*
line to match the pattern (unlike `STARTS_WITH`, a regex prefix can't share its line with label text).

### Sign persistence and versioning

Sign state is persisted per-world as `signs.json`, wrapped in a `VersionedSignFile` envelope (`{version, data}`) so
the format can evolve without breaking old saves. `SignProvider.loadSigns` tries, in order: the versioned-file loader
(`VersionedFileSignEntryLoader`, handling V2→V3 migration via `Version3Converter`, and current V3 files directly),
then falls back to `Version1SignEntryLoader` for pre-versioning files. When adding a new persisted field, bump
`SignFileVersions` and add a loader/converter rather than changing an existing version's shape in place — old
`signs.json` files on live servers must keep loading.

### Adding a new marker/BlueMap action

New `MarkerAction` subtypes go through `ActionFactory` (construction) and need a `case` arm added in both
`BlueMapAPIConnector.processMarkerAction`'s switch and `logProcessingMessage`'s switch — `MarkerAction` is a plain
abstract class (not sealed), so a missing case silently falls through to the `default` branch instead of failing to
compile.

### Testable vs. game-coupled code

When adding logic, prefer keeping it in plain Java classes with no Minecraft/Fabric/BlueMap API types in their
signature (like `SignLinesParser`, `SignEntryHelper`, `MarkerGroup`/`MarkerGroupMatchType`, `ConfigManager`/
`ConfigProvider`, `ReactiveQueue`, the persistence loaders/converters, `ActionFactory`/`MarkerSetIdentifierCollection`)
— these can be unit tested directly (see `src/test/java/.../core/signs/SignLinesParserTest.java` for the pattern).
Code that must reference game types (`SignHelper`, the mixins, `BlueMapSignMarkersMod`, `BlueMapAPIConnector`)
should stay thin glue around the testable core, since it can only be verified manually via `runServer`.

There is a documented, currently-unfixed gap here: `BlueMapAPIConnector` passes sign text into BlueMap's POI marker
`detail` field unescaped, and BlueMap renders `detail` as raw HTML (unlike `label`, which BlueMap escapes itself) —
see `plans/html-detail-escaping-plan.md` for the planned fix.

## Planning documents

Design/implementation plans for larger pieces of work are written to the `plans/` folder before being implemented,
so they can be reviewed independently of the eventual code change.
