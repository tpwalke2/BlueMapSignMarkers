# Plan: Extract & test `SignManager`'s transition table

Addresses finding #4 from `../reviews/adversarial-review-feature-tpwalke2-7-line-markers-2026-08-14.md`:
`computeTransitionAction` (the "decision point" per AGENTS.md) has zero automated test coverage. Findings #1-3 from
that same review were regressions this table's logic already caused once; a table-driven test is the safety net
that would have caught them before merge.

## Approach

`computeTransitionAction` and its helpers have no Minecraft/Fabric/BlueMap types in their signatures — they're
already testable, just stuck as private methods on `SignManager`, which itself can't be unit-tested directly
because its constructor builds a `BlueMapAPIConnector`, whose constructor touches live `BlueMapAPI` static state.
Extracting the pure logic sidesteps that blocker entirely and matches the existing `SignLinesParser`/
`LineGroupResolver`/`ActionFactory` testable-core convention (AGENTS.md, "Testable vs. game-coupled code").

## Changes

### 1. New class: `core/signs/SignTransitionResolver.java`

Static-utility class (private constructor, all static methods — same shape as `LineGroupResolver`). Move from
`SignManager` verbatim, including their doc comments (the "leave-effect/join-effect" explanation, the
reload-safety notes):

- `Representation` record (stays package-private to this class; nothing outside it needs to name the type)
- `computeRepresentation(SignEntry, Map<String, MarkerGroup>)`
- `sameGroupAndLabel(Representation, Representation)`
- `computeTransitionAction(List<SignEntry> allSigns, SignEntryKey key, Representation oldRep, Representation newRep, ActionFactory actionFactory)`
- `lineJoinAction(...)`, `lineLeaveAction(...)`, `toPoints(...)`, `joinLineDetail(...)`

`ActionFactory` and `List<SignEntry>` keep being passed as parameters on each call, same as today — the resolver
holds no state of its own.

### 2. `SignManager.java` changes

Delete the moved methods/record. At the three call sites (`addOrUpdateSign`, `removeByKey`, `reloadConfig`), call
`SignTransitionResolver.computeRepresentation(...)` / `SignTransitionResolver.computeTransitionAction(...)`
directly — no pass-through wrapper kept on `SignManager`. Everything else (`signCache`, `chunkIndex`,
`BlueMapAPIConnector` dispatch, `RuntimeConfig`, locking) stays exactly as-is; this is a pure move, not a
behavior change.

### 3. New test: `src/test/java/.../core/signs/SignTransitionResolverTest.java`

Table-driven, one test per row/sub-branch of the transition table in `../../.scratch/line-markers/spec.md` §6. Build
`Representation`s only via `computeRepresentation(SignEntry, Map<String, MarkerGroup>)` (mirrors how `SignManager`
itself gets them) rather than reaching into the private record. Reuse the `signEntry(...)` test-helper pattern
from `LineGroupResolverTest`.

Cases to cover:

| # | Old → New | Case |
|---|---|---|
| 1 | NONE → NONE | both `null` → `null` result |
| 2 | NONE → POI | dispatch Add POI |
| 3 | NONE → LINE, <2 members after add | no-op (line still incomplete) |
| 4 | NONE → LINE, ≥2 members after add | dispatch Set, `isFirstAppearance=true` at exactly 2 |
| 5 | NONE → LINE, join makes a 3rd member | dispatch Set, `isFirstAppearance=false` |
| 6 | POI → NONE | dispatch Remove POI |
| 7 | POI → POI, same group+label, text unchanged | no-op |
| 8 | POI → POI, same group+label, text changed | dispatch Update |
| 9 | POI → POI, different group/label | `GroupTransitionMarkerAction` wrapping Remove(old) + Add(new) |
| 10 | LINE → NONE, drops to 1 remaining member | dispatch Remove Line |
| 11 | LINE → NONE, drops to 0 remaining members | no-op (never had a marker) |
| 12 | LINE → NONE, ≥2 members remain | dispatch Set (refreshed point list, excludes departing sign) |
| 13 | LINE → LINE, same group+label, detail unchanged | no-op |
| 14 | LINE → LINE, same group+label, detail changed | dispatch Set, `isFirstAppearance=false` (recompute forces false) |
| 15 | LINE → LINE, different group/label | leave-effect + join-effect bundled in `GroupTransitionMarkerAction` |
| 16 | POI → LINE | leave (Remove POI) + join (Set or no-op depending on member count), bundled |
| 17 | LINE → POI | leave (Remove Line/Set/no-op depending on remaining count) + join (Add POI), bundled |

Assertions use `assertInstanceOf(ExpectedType.class, action)` followed by getter checks (id/label/detail/points/
effects) — no `equals()`/`hashCode()` added to `MarkerAction` subclasses just for test convenience.

## Out of scope

- Finding #5 (`lineWidth`/`lineColor` validation in `ConfigProvider`) — separate subsystem, no coupling to this
  refactor, left for its own pass.
- No behavior change: this is a mechanical extraction plus new tests. If a test fails, it means the *existing*
  logic doesn't do what the spec says — fix the test to match intended behavior only after confirming against
  `../../.scratch/line-markers/spec.md` §6, not by loosening the assertion.

## Verification

- `./gradlew test --tests "*.SignTransitionResolverTest"` — new tests pass
- `./gradlew build` — full suite + jar build still green (confirms the extraction didn't change `SignManager`'s
  runtime behavior)
