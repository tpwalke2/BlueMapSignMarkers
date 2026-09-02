# 01 — Add sorting/toggleable/depthTest/cssClasses fields to MarkerGroup config

**What to build:** Four new `MarkerGroup` config fields (`sorting`, `toggleable`, `depthTest`, `cssClasses`) with
`ConfigProvider` resolution, defaulting, malformed-value fallback, and type-scope mismatch warnings, mirroring the
existing `lineWidth`/`lineColor`/`fillColor` pattern.

See `../spec.md` for full context (Implementation/Testing Decisions sections).

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] `MarkerGroup` record gains `sorting` (int, default `0`, all types), `toggleable` (boolean, default `true`, all
      types), `depthTest` (boolean, default `true`, `LINE`/`SHAPE` only), `cssClasses` (`List<String>`, default
      empty, `POI` only). All existing call sites (main + test) updated for the new constructor arity.
- [x] `ConfigProvider` gains `resolveSorting`/`resolveToggleable`/`resolveDepthTest`/`resolveCssClasses`: default
      when unset, malformed `sorting` value falls back to `0` and logs a warning.
- [x] Field-mismatch warnings extend to `depthTest` set on a `POI` group and `cssClasses` set on a `LINE`/`SHAPE`
      group (warn-and-ignore, same as existing `icon`-on-`LINE`/`fillColor`-on-`POI` warnings).
- [x] `ConfigProviderTest` covers default/valid/malformed resolution for all four fields and both new mismatch
      warnings.
- [x] `./gradlew build` passes (compiles + all unit tests).

**Implementation note:** `LoadingMarkerGroupV2.sorting` is typed as a raw `JsonElement` (not `Integer`), so a
non-integer `sorting` value falls back with a warning instead of failing Gson's parse for the whole config;
`ConfigProvider.resolveSorting` validates it manually via `getAsInt()`.
