# Minecraft 26.3 port

## Scope and dependency baseline

Target Minecraft 26.3 with Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3,
stable Loom 1.17.21, and BlueMap 5.24 / BlueMap API 2.8.0. Keep Gradle 9.6.1.
All dependency versions stay in `gradle.properties`; use mod version `26.3-1.0.0`.

Upstream references:

- [Fabric 26.3 migration notes](https://fabricmc.net/2026/09/15/263.html)
- [Fabric API artifacts](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/)
- [Loom artifacts](https://maven.fabricmc.net/net/fabricmc/fabric-loom/)
- [BlueMap 5.28 release](https://github.com/BlueMap-Minecraft/BlueMap/releases/tag/v5.28)
- [BlueMap 5.24 release](https://github.com/BlueMap-Minecraft/BlueMap/releases/tag/v5.24)
- [BlueMap API Maven metadata](https://repo.bluecolored.de/releases/de/bluecolored/bluemap-api/maven-metadata.xml)

## Minecraft API changes confirmed from the 26.3 server jar

- `SignBlockEntity.updateSignText` and `updateText` now take `SignTextSlot` instead of a front/back boolean.
  Update all three Mixin callbacks, preserving the guard that avoids duplicate dispatch during text edits.
- `getFrontText()` / `getBackText()` are replaced by `getText(SignTextSlot.FRONT/BACK)`.
  Read each side once when creating a sign snapshot, keeping raw lines and dye from the same text object.
- `SignText.getMessages(boolean)` now returns a `List<Component>` instead of an array; stream the list directly.
- Set Mixin `compatibilityLevel` to `JAVA_25`, matching the compiled class-file version (69).
- Verify the removal hook `BlockBehaviour.affectNeighborsAfterRemoval`, dimension identifiers, world paths,
  and Fabric lifecycle callback signatures against the target dependencies.

## BlueMap integration

Compile against API 2.8.0, bundled in BlueMap 5.24, the first release supporting Minecraft 26.3.
Declare BlueMap 5.24 as the minimum supported version; the initial 5.28 verification is recorded below.
Check its Fabric dependencies when preparing the dev runtime; preserve the project's per-module Fabric setup.

## Validation

1. Build and run the complete existing JUnit suite with the target dependencies.
2. Inspect the built mod metadata and both Mixin target descriptors.
3. Start an isolated development server with BlueMap 5.24 to check runtime linkage and Mixin application.
4. Where a full server can run, check sign creation/edit/removal, front/back dye updates, reload,
   multi-point markers, and persistence. Clearly record any checks requiring an interactive player.
5. Update README requirements and current architecture notes, then review the final diff.

Do not change persisted sign formats, group configuration, or marker identifiers as part of the port.

## Implementation and validation results (2026-10-01)

- Updated dependencies, mod version, sign snapshots, all three sign Mixin callbacks, and Java 25 Mixin metadata.
  The mod now declares BlueMap `>=5.28`; README installation requirements and current architecture notes match.
- Comparing all Java sources in the official API 2.8.0 and 2.8.1 source jars found no public source changes.
  Existing marker calls were retained and exercised against the API bundled in BlueMap 5.28.
- `build` succeeded against the target dependency set. JUnit reports: 454 tests, 452 passed, 2 skipped,
  0 failures, 0 errors. The two skips are `RenderMaskEvaluatorTest` permission-denial cases: this Windows
  environment could not revoke directory-listing/file-read permissions, so their assumptions aborted the tests.
- Verified Fabric lifecycle module 4.1.9+ffef5f675d: chunk-load callbacks still take
  `(ServerLevel, LevelChunk, boolean)`, and block-entity-load callbacks still take `(BlockEntity, ServerLevel)`.
- Launched `runServer` with MC 26.3, Loader 0.19.5, and BlueMap 5.28 in an isolated directory under
  `build/compat-26.3/server`, using a temporary Gradle init script and smoke-test mod (both ignored build artifacts).
  Both required Mixins applied. Inspection of the exported `SignBlockEntity` bytecode confirmed the HEAD/TAIL
  text-edit callbacks and both `updateText` RETURN callbacks use the new `SignTextSlot` descriptors.
- The temporary smoke mod passed ordinary/hanging sign snapshot checks (front/back raw text, parsing, position,
  and independent red/blue dyes) and exercised actual BlueMap POI add/update/remove, LINE/SHAPE/EXTRUDE builders,
  escaped detail text, and marker-set APIs. Snapshots used detached block entities populated by reflection;
  these checks do not simulate a player editing a live sign.
- Correcting `JAVA_21` to `JAVA_25` removed the class-version-69 mismatch warning.
- The server stopped normally at the Minecraft EULA check. The test did not accept the EULA or start a world.
  Live player edits/dye/ink interactions, world-backed removal, BlueMap enable/reload, chunk reconciliation,
  and persistence across a full server restart remain manual acceptance checks.
- Final diff whitespace check passed. No persisted sign-format or marker-identifier changes were made.

## PR review follow-up (2026-10-02)

- Corrected the minimum supported BlueMap version from 5.28 to 5.24 in `gradle.properties` and README.
  BlueMap 5.24 already supports Minecraft 26.3 and bundles API 2.8.0, so the compile-only API dependency now
  matches that minimum. BlueMap 5.28 was the initial verification version, not an API requirement.
- Recompared the official API 2.8.0 and 2.8.1 source jars: all 27 Java files are identical, with none added or
  removed. The existing marker builders, colors, marker-set properties, and listener calls need no changes.
- Rebuilt with API 2.8.0, rerunning all build tasks and tests: 454 tests, 452 passed, 2 skipped, no failures or
  errors. Confirmed the generated mod metadata declares `bluemap >=5.24`.
- Loaded the official BlueMap 5.24 Fabric jar in an isolated MC 26.3 dev server under
  `build/compat-bluemap-5.24/server`. Both Mixins applied; the temporary smoke mod passed the same sign-snapshot
  and POI/LINE/SHAPE/EXTRUDE checks, plus connector listener registration/unregistration.
  The server stopped at the EULA check without starting a world; live gameplay/reload checks remain manual.
