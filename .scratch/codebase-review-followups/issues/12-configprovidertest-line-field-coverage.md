# 12 — `ConfigProviderTest` has no coverage for `lineWidth`/`lineColor` defaulting or the type-mismatch warning

**What to build:** `ConfigProvider` (`config/ConfigProvider.java`) gained line-marker-related config handling with
the line-markers feature that `ConfigProviderTest` doesn't exercise:
- `convertToLoadedMarkerGroup` defaults `lineWidth` to `2` and `lineColor` to `"#FF0000FF"` when a `LINE`-type
  group's JSON omits them (lines 198-199).
- `warnOnTypeFieldMismatches` (line 160) logs a warning — but doesn't fail config load — when a `POI`-type group
  has `lineWidth` and/or `lineColor` set (lines 166-170); those fields are ignored for `POI` groups rather than
  validated as an error.

Add test methods (matching `ConfigProviderTest`'s existing per-behavior style):
- A `LINE`-type group with `lineWidth`/`lineColor` omitted in JSON gets the `2`/`"#FF0000FF"` defaults.
- A `LINE`-type group with both fields explicitly set keeps the configured values (no defaulting).
- A `POI`-type group with `lineWidth` and/or `lineColor` set still loads successfully (not treated as a validation
  error, unlike `validateMarkerGroups`'s other checks) — this is a warning-only path, so assert the group still
  loads correctly rather than asserting on log output.

**Blocked by:** None.

**Status:** resolved

- [x] `lineWidth`/`lineColor` defaulting for a `LINE` group has a dedicated test
- [x] Explicit `lineWidth`/`lineColor` values on a `LINE` group are preserved, not overwritten by defaults
- [x] A `POI` group with `lineWidth`/`lineColor` set still loads (warning path, not a load failure)
- [x] Full test suite still passes

**Resolved:** Added `loadConfigDefaultsLineWidthAndLineColorForALineGroupWhenOmitted`,
`loadConfigPreservesExplicitLineWidthAndLineColorForALineGroup`, and
`loadConfigStillLoadsAPOIGroupWithLineWidthAndLineColorSet` to `ConfigProviderTest`. Full suite passes (JDK 25
toolchain).

## Comments

Found alongside ticket 11 (`11-actionfactorytest-line-action-coverage.md`) while updating `agent-context/context/testing.md`
for the line-markers feature (2026-08-13) — checked `ConfigProvider.java` directly (`warnOnTypeFieldMismatches`,
the `lineWidth()`/`lineColor()` null-coalescing defaults) and confirmed `ConfigProviderTest` has no test methods
referencing either.
