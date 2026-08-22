# 01 — Config: `MarkerGroupType.LINE`, `lineWidth`/`lineColor`

**Spec:** `../spec.md` §1 ("Config: `MarkerGroupType.LINE`, `lineWidth`/`lineColor`")

**Blocked by:** None.

**What to build:**
- `core/markers/MarkerGroupType.java` — add `LINE`.
- `core/markers/MarkerGroup.java` — add `int lineWidth`, `String lineColor` trailing components; update
  `DEFAULT_POI_GROUP`, `withType`.
- `config/persistence/LoadingMarkerGroupV2.java` — add nullable `Integer lineWidth`/`String lineColor`.
- `config/ConfigProvider.java`:
  - `convertToLoadedMarkerGroup` defaults `lineWidth`/`lineColor` to `2`/`"#FF0000FF"` when absent (same pattern as
    `offsetX`/`offsetY`).
  - `loadV1Config`'s inline `MarkerGroup` construction gets the same defaults.
  - `validateMarkerGroups` gains a warning pass (never throws): a `POI` group with an explicit non-default
    `lineWidth`/`lineColor` in the raw `LoadingMarkerGroupV2`, or a `LINE` group with an explicit
    `icon`/`offsetX`/`offsetY`, logs a warning naming the group and field. Check against the raw loading record
    (still distinguishes "field omitted" via `null`), not the defaulted `MarkerGroup`.
- `../../../../README.md` — `lineWidth`/`lineColor` entries in the Marker Groups section plus a `LINE` example.

**Status:** done

- [x] `MarkerGroupType.LINE` exists; `MarkerGroup` carries `lineWidth`/`lineColor`
- [x] Missing `lineWidth`/`lineColor` in config default to `2`/`"#FF0000FF"` without crashing
- [x] Mismatched fields for a group's type log a warning, never throw
- [x] `../../../../README.md` documents the new fields and a `LINE` example
- [x] `./gradlew test` and `./gradlew build` pass
