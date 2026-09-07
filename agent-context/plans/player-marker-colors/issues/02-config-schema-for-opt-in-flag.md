Type: grilling
Status: resolved

## Question

Decide the exact shape of the per-group opt-in flag (map Notes: "opt-in per marker group, default off").

- Field name on `MarkerGroup` (e.g. `playerColors: boolean`)?
- Does it apply uniformly to `LINE`/`SHAPE`/`EXTRUDE`, or could a group want it for one but not another aspect
  (e.g. player controls `lineColor` but admin keeps `fillColor` fixed)? `MarkerGroup` already has type-specific
  fields that warn (not error) when set on the wrong group `type` (see AGENTS.md "Marker groups and config") —
  does this flag follow that same validation pattern?
- Does `ConfigProvider`/`LoadingMarkerGroupV2` need a default value written for existing configs on load, or is
  Java's default (`false`) sufficient given `LoadingMarkerGroupV2` likely already has a defaulting mechanism —
  check its current pattern for other optional boolean fields (e.g. `toggleable`, `depthTest`) before deciding.

## Answer

- Field name: `allowPlayerColors` (Boolean, nullable in `LoadingMarkerGroupV2`) — "allow" makes the
  permission-grant nature explicit, unlike the terser `playerColors`.
- Granularity: one flag covers both `lineColor` and `fillColor` together — no separate control per channel.
- Type restriction & resolution: follows the exact shape of `resolveDepthTest`/`resolveLineWidth` in
  `ConfigProvider` —
  - `LINE`/`SHAPE`/`EXTRUDE`-only; unset (`null`) resolves to `false` (opt-in, default off).
  - Setting it on a `POI` group is a warning (not an error) in `warnOnTypeFieldMismatches`, matching how
    `lineColor`/`fillColor`/`depthTest` are already warned on the wrong type.
  - New `resolveAllowPlayerColors(LoadingMarkerGroupV2)` in `ConfigProvider`, called from
    `convertToLoadedMarkerGroup`, added as a new field on the `MarkerGroup` record (and its `withType`
    method).
