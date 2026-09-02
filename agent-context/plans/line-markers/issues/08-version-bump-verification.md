# 08 — Version bump, full manual verification pass

**Spec:** `../spec.md` §8 ("`LineMarker` fields not covered above"), "Changes (files)" items
20-21, and "Verification"

**Blocked by:** 01-07 (this is the closing pass over the whole feature)

**What to build:**
- `../../../../README.md` — confirm `LINE` type, `lineWidth`, `lineColor` are documented (ticket 01 covers the field docs;
  this ticket covers cross-checking the full feature reads coherently end to end).
- `../../../../gradle.properties` — bump `mod_version` (new marker type + persisted-format change).
- Confirm `minDistance`/`maxDistance`/`defaultHidden` are inherited (`DistanceRangedMarker`) and reused unchanged
  from `MarkerGroup` for `LINE` groups — no line-specific handling needed (spec §8).
- New tests sanity check (should already exist from earlier tickets, confirm they're present and passing):
  `LineGroupResolverTest`, a `ColorUtils` test, `Version4ConverterTest`.

**Full manual verification** (`./gradlew runServer`):
1. Place 3 signs with the same `[line]`-style prefix and label at different times; confirm no marker appears
   until the 2nd sign, then a line grows to include the 3rd.
2. Break the middle sign; confirm the line reconnects the remaining two. Break down to 1 remaining sign; confirm
   the marker disappears.
3. Edit one sign's prefix from a `LINE` group to a `POI` group and back; confirm no duplicate/orphaned markers in
   either direction.
4. Edit a marker group's `type` in `BMSM-Core.json` from `POI` to `LINE` (existing signs, unchanged text) and run
   `/bluemap reload`; confirm the POI markers disappear and a line marker appears with no leftover POI marker in
   BlueMap's web UI. Repeat in reverse.
5. Set an invalid `lineColor` hex string in config; confirm the server logs a warning and falls back to the
   default rather than crashing.
6. Restart the server after placing lines spanning two region files (far-apart signs, same prefix+label); confirm
   the line still renders correctly after the region-sharded reload.
7. Start from a pre-V4 world save (signs from before this change); confirm signs load, migrate to V4, and any
   newly-configured `LINE` group involving old signs renders (documented arbitrary ordering) rather than erroring.

**Status:** done

- [x] `../../../../README.md` reviewed end to end for the `LINE` feature — `type`/`lineWidth`/`lineColor` documented, example
      config includes a `LINE` group, prose reads coherently
- [x] `mod_version` bumped in `../../../../gradle.properties` (`26.2-0.18.0` → `26.2-0.19.0`)
- [x] `./gradlew test` and `./gradlew build` pass; `LineGroupResolverTest`, `ColorUtilsTest`,
      `Version4ConverterTest` all present and passing
- [x] All 7 manual verification scenarios above pass — `runServer` pass completed by the author
- [x] Known limitations section of the spec (no reordering, prefix-rename needs live re-edit, no closed/filled
      shapes) still accurate — no surprises found during manual testing that should be added there

## Comments

`../../../../README.md` already covered the `LINE` type/`lineWidth`/`lineColor` fields from ticket 01 and reads coherently
end to end; no changes needed there. `mod_version` bumped to `26.2-0.19.0` (new marker type + persisted-format
change). `./gradlew build` passes and the three tests this ticket calls out to confirm
(`LineGroupResolverTest`, `ColorUtilsTest`, `Version4ConverterTest`) all exist and pass. The 7-scenario manual
`runServer` verification pass has been completed by the author, matching the PR description.
