# 09 — Update AGENTS.md architecture docs

**Spec:** `.scratch/line-markers/spec.md` — not yet listed in "Changes (files)"; add as item 22.

**Blocked by:** 01-07 (docs should describe the code as it lands, not the plan)

**What to build:**

`AGENTS.md` describes the architecture this feature changes in several places. Update each:

- "Marker groups and config" section — `MarkerGroup` gains `lineWidth`/`lineColor`; `MarkerGroupType` gains `LINE`.
- "Adding a new marker/BlueMap action" section — new `SetLineMarkerAction`/`RemoveLineMarkerAction`/
  `GroupTransitionMarkerAction` go through the same `ActionFactory` + dual-switch convention; note
  `ChangeGroupMarkerAction` was replaced by `GroupTransitionMarkerAction`.
- "Sign persistence and versioning" section — add `V4` (`createdAtMillis`) to the version chain description
  alongside existing V1-V3 mentions.
- "Testable vs. game-coupled code" section — add `LineGroupResolver`, `ColorUtils`, `DispatchedMarkerIdentifier`/
  `LineMarkerIdentifier`/`LinePoint` to the list of plain-Java testable classes.
- "Core pipeline: sign text → marker action" section — `SignManager`'s add/update/remove decision logic is
  rewritten as a representation lookup table (spec §7); update the description of step 2 to match.

**Status:** open

- [ ] Marker groups and config section updated
- [ ] Adding a new marker/BlueMap action section updated
- [ ] Sign persistence and versioning section updated
- [ ] Testable vs. game-coupled code section updated
- [ ] Core pipeline section updated
- [ ] `AGENTS.md` reads coherently end to end for the `LINE` feature (not just patched in isolation)
