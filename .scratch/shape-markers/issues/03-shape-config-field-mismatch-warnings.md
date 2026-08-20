# 03 — Config validation polish: field-mismatch warnings

**What to build:** A server admin who sets a `POI`/`LINE`-only field (`icon`, `offsetX`, `offsetY`) on a `SHAPE`
group, or sets `fillColor` on a `POI`/`LINE` group, sees a warning logged and the field ignored — never a crash or
silent unexpected behavior. This matches the existing warn-and-ignore convention `ConfigProvider` already applies
for `LINE` groups with `icon`/`offsetX`/`offsetY` set.

See `.scratch/shape-markers/spec.md` for full context.

**Blocked by:** 01 — requires `MarkerGroupType.SHAPE` and the `fillColor` field to exist.

**Status:** resolved

- [x] Setting `icon` on a `SHAPE` group logs a warning ("...is type SHAPE but has 'icon' set...") and the field is
      ignored, mirroring the existing `LINE` warning.
- [x] Setting `offsetX` or `offsetY` on a `SHAPE` group logs a warning and the field is ignored, mirroring the
      existing `LINE` warnings.
- [x] Setting `fillColor` on a `POI` or `LINE` group logs a warning ("...is type POI/LINE but has 'fillColor'
      set...") and the field is ignored.
- [x] Unit tests: `ConfigProviderTest` additions covering each of the above mismatch cases.

## Comments

Completed as a side effect of ticket 01: `ConfigProvider.warnOnTypeFieldMismatches` gained a `SHAPE` branch
(warns on `icon`/`offsetX`/`offsetY`, mirroring the existing `LINE` branch) and a `fillColor` check in both the
`POI` and `LINE` branches. Field values are still loaded (not nulled out) despite the warning, matching the
existing `LINE`-field-on-`POI` convention (`loadConfigStillLoadsAPOIGroupWithLineWidthAndLineColorSet`).
`ConfigProviderTest` gained `loadConfigStillLoadsAPOIGroupWithFillColorSet`,
`loadConfigStillLoadsALineGroupWithFillColorSet`, and `loadConfigStillLoadsAShapeGroupWithIconAndOffsetsSet`. See
ticket 01's Comments for the full implementation summary.
