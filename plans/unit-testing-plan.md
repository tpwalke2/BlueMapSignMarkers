# Add unit testing support + tests for `SignLinesParser`

## Context

The project currently has no automated tests — `src/main` only, no `src/test`, and `build.gradle` declares no test
dependencies or config. Verification today is manual (`./gradlew runServer` + placing signs in-game). We want a
standard JUnit 5 setup so pure-logic classes (starting with `SignLinesParser`) can be covered by fast, automated
tests, without any of that test code ending up in the shipped mod jar.

This doesn't require anything Minecraft/Fabric-specific: `SignLinesParser` and its collaborators
(`ParsingContext`, `SignLinesParseResult`, `MarkerGroup`, `MarkerGroupMatchType`) are plain Java with no Minecraft,
Fabric, or BlueMap API references, so they run as ordinary JUnit tests with no game environment needed.

## Why tests won't ship in the mod jar

Gradle's `java` plugin (applied transitively by `fabric-loom`) already keeps `src/test/java` fully separate from
`src/main/java`: the `jar` task only packages `sourceSets.main.output`. No exclusion rules are needed — this is
automatic as long as test code lives under `src/test/java`.

## Changes

1. **`build.gradle`** — add a JUnit 5 test setup:
   - `testImplementation platform('org.junit:junit-bom:5.11.4')`
   - `testImplementation 'org.junit.jupiter:junit-jupiter'`
   - `test { useJUnitPlatform() }`
   - No changes needed to the `jar`, `loom`, or `processResources` blocks.
   - Note: `compileOnly` dependencies (e.g. `bluemap-api`) are not visible to `src/test` by default. Not needed for
     `SignLinesParser` tests, but call this out as a note in case future tests touch BlueMap-API-dependent code —
     they'd need an explicit `testCompileOnly`/`testImplementation` for that dependency.

2. **New file: `src/test/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignLinesParserTest.java`**
   Package-mirrors the main source layout. Uses JUnit 5 (`@Test`, `@ParameterizedTest` where useful) to construct
   `SignLinesParser` instances directly with hand-built `MarkerGroup` records (via its existing constructor —
   no test-only production code needed) and assert on the returned `SignLinesParseResult`.

   Planned test cases, based on the actual parsing behavior in
   `src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignLinesParser.java`:
   - **STARTS_WITH match, label on the prefix line** — e.g. `["[poi] Town Hall"]` → prefix `[poi]`, label
     `Town Hall`, detail `Town Hall`.
   - **STARTS_WITH match, label on a following line** — e.g. `["[poi]", "Town Hall"]` → label pulled from the next
     non-empty line.
   - **Multi-line detail** — 3+ non-empty content lines all get joined into `detail` (newline-separated, trimmed),
     while `label` stays the first content line.
   - **Leading blank lines are skipped** before the prefix is found (state stays `START`).
   - **Blank lines between content lines are skipped** and do not appear in `detail`.
   - **No configured group matches** → result is `(null, "", "")`.
   - **All-blank sign** (never leaves `START`) → result is `(null, "", "")`.
   - **REGEX match type** — prefix pattern like `\\[[vV][iI][lL][lL][aA][gG][eE]\\]`, verifying `line.matches(...)`
     is used for matching and `replaceAll` for label extraction (per current implementation).
   - **Multiple configured groups, first match wins** — two groups whose prefixes could both apply to a line,
     confirming list order determines the match (`findFirst()` semantics).
   - **Whitespace tolerance** — lines with leading/trailing whitespace around the prefix/content still parse
     correctly (each line is `.trim()`-ed before matching).

3. **No other files change.** `README.md`, mixins, `fabric.mod.json`, and CI workflows are unaffected — CI's
   existing `./gradlew clean build` already runs `check`/`test` as a dependency of `build`, so tests will start
   running in CI automatically once added, with no workflow file changes required.

## Verification

- Run `./gradlew test` — confirms the new JUnit 5 wiring works and all `SignLinesParserTest` cases pass.
- Run `./gradlew clean build` — confirms the full build (including `runServer`-independent test run) still succeeds
  and that `build/libs/*.jar` contains no `src/test` classes (spot-check with `jar tf` if desired).
