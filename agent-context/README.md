# Agent Context — BlueMap Sign Markers

BlueMap Sign Markers is a server-side Fabric mod (Java 25) for Minecraft. It watches in-game signs and, when a
sign's text matches a configured prefix (e.g. `[poi]`), creates/updates/removes a corresponding marker on a
[BlueMap](https://bluemap.bluecolored.de/) map. Signs are tracked persistently, sharded into one JSON file per
(dimension, 32x32-chunk region) under `{server_root}/bluemapsignmarkers/{level}/`, so markers survive restarts.
Multiple "marker groups" (prefix, match rule, icon, visibility) can be configured at once via
`config/bluemapsignmarkers/BMSM-Core.json`.

Human-facing docs already exist:
- `README.md` (repo root) — end-user install/config instructions and the marker-group config format
- `AGENTS.md` / `CLAUDE.md` (repo root) — AI agent guidance (build commands, high-level architecture, conventions)

This `agent-context/` directory goes deeper than `AGENTS.md` on implementation specifics — exact decision logic,
method-level behavior, data-shape history — so an agent doesn't have to re-derive it from source every time.

## Documents

| Document | What it covers |
|----------|----------------|
| `context/architecture.md` | Tech stack, directory map, package taxonomy, build/CI/publish tooling |
| `context/core-pipeline.md` | Sign text → marker action: parsing state machine, `SignManager`'s representation-diff transition table (POI and `LINE` marker groups), chunk-load sign reconciliation (`SignChunkKey`/`SignChunkIndex`), `ReactiveQueue`/`BlueMapAPIConnector` mechanics, marker ID/set identity scheme (position-keyed POI ids and content-keyed line ids) |
| `context/config-and-persistence.md` | Marker-group config loading/migration (V1→V2, incl. `LINE`-type fields), region-sharded sign persistence, per-region-file versioning (V1→V2→V3→V4), legacy-file migration and backup-on-migrate behavior |
| `context/testing.md` | Test infra, testable-vs-game-coupled split, CI test-result summarization, current coverage and known gaps |

## Document Scopes

| Document | Codebase paths watched |
|----------|------------------------|
| `context/architecture.md` | `build.gradle`, `gradle.properties`, `settings.gradle`, top-level dirs, `.github/workflows/` |
| `context/core-pipeline.md` | `src/main/java/.../core/signs/` (excl. `persistence/`), `core/bluemap/`, `core/reactive/`, `core/markers/`, `mixin/`, `BlueMapSignMarkersMod.java`, `common/` (`HtmlUtils`, `LogUtils`, `ColorUtils`) |
| `context/config-and-persistence.md` | `src/main/java/.../config/`, `core/signs/persistence/`, `README.md` (config format section), `common/FileUtils.java` |
| `context/testing.md` | `src/test/java/`, `.github/workflows/build.yml`, `.github/workflows/publish.yml` |

---
*Last updated: 2026-08-13 | Verified against: main (374d6db)*
