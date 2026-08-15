# Fabric API module split

## Context

The mod depends on the full Fabric API bundle (`../../build.gradle`: `implementation
"net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"`, and `fabric.mod.json`: `"fabric-api": "*"`),
but only uses one Fabric API package. Reducing this to the specific module actually needed is general best-practice
surface-area reduction, not a response to any bug or user report.

A full-project grep of `net.fabricmc.fabric.(api|impl)` imports turns up exactly three classes, all in
`BlueMapSignMarkersMod.java`, all from the same package `net.fabricmc.fabric.api.event.lifecycle.v1`:
`ServerLifecycleEvents` (`SERVER_STARTING`/`SERVER_STOPPING`), `ServerBlockEntityEvents` (`BLOCK_ENTITY_LOAD`), and
`ServerChunkEvents` (`CHUNK_LOAD`). No mixin or other source file touches Fabric API. That package belongs to a
single Fabric API subproject: `fabric-lifecycle-events-v1` — already namedropped (but never wired into the build) in
`-load-sign-reconciliation-plan.md`.

## Goal

Depend on `fabric-lifecycle-events-v1` instead of the full `fabric-api` bundle, both at build time and at runtime,
with no behavior change.

## Design

### `../../build.gradle`

Replace:
```groovy
implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
```
with the Loom module helper:
```groovy
implementation fabricApi.module("fabric-lifecycle-events-v1", project.fabric_api_version)
```

**Risk**: `fabric_api_version=0.152.1+26.2` targets Minecraft `26.2`. If `fabricApi.module(...)` can't resolve
`fabric-lifecycle-events-v1` for this version/loom combo (`fabric_loom_version=1.17-SNAPSHOT`), fall back to pinning
the full `fabric-api` bundle as before and file a follow-up rather than blocking this change — this is a
verify-during-implementation risk, not a reason to redesign the approach.

### `fabric.mod.json`

Replace:
```json
"fabric-api": "*"
```
with:
```json
"fabric-lifecycle-events-v1": "*"
```
Kept as a bare wildcard, matching the existing style for `bluemap` (external mod deps are unconstrained here;
`fabricloader`/`minecraft`/`java` are the ones version-constrained).

## Out of scope

- Any change to the mod's actual event usage/logic — this is a dependency-declaration change only.
- Constraining the new `depends` entry to a specific version — matches existing bare-wildcard style for mod deps.
- A formal tracked issue in `../../.scratch` — scope is self-contained (two files) and low-risk.

## Changes (files)

1. **`../../build.gradle`** — swap the full `fabric-api` dependency for `fabricApi.module("fabric-lifecycle-events-v1",
   project.fabric_api_version)`.
2. **`../../src/main/resources/fabric.mod.json`** — swap `"fabric-api": "*"` for `"fabric-lifecycle-events-v1": "*"` in
   `depends`.

No source code changes — the three imported classes live in the same package either way.

## Verification

- `./gradlew build` — confirms the module resolves and the mod still compiles/tests/jars successfully.
- Manual, via `./gradlew runServer` (per `testing.md`, the mod entrypoint has no automated coverage): confirm the
  mod still initializes without errors, and that all four lifecycle hooks still fire — place a sign (`BLOCK_ENTITY_LOAD`),
  confirm its marker appears in BlueMap, restart the server (`SERVER_STARTING`/`SERVER_STOPPING`) and confirm the
  marker persists, and walk into a chunk with a tracked sign (`CHUNK_LOAD`) to confirm no regression in the
  chunk-load reconciliation behavior.
