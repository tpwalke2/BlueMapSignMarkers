# 09 — Update AGENTS.md architecture docs

**Spec:** `../spec.md` — not yet listed in "Changes (files)"; add as item 22.

**Blocked by:** 01-07 (docs should describe the code as it lands, not the plan)

**What to build:**

`../../../../AGENTS.md` describes the architecture this feature changes in several places. Update each:

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

**Status:** resolved

- [x] Marker groups and config section updated
- [x] Adding a new marker/BlueMap action section updated
- [x] Sign persistence and versioning section updated
- [x] Testable vs. game-coupled code section updated
- [x] Core pipeline section updated
- [x] `../../../../AGENTS.md` reads coherently end to end for the `LINE` feature (not just patched in isolation)

## Comments

Updated all five sections. Core pipeline's step 2 now describes `SignManager`'s add/update/remove/type-flip logic as
a single `Representation`-diff transition table (ticket 06/07), rather than the old separate-cases description, and
reload as capturing the pre-swap prefix→group map and diffing per-sign instead of clear-and-replay. Marker groups
section gained `lineWidth`/`lineColor` on `MarkerGroup` and a paragraph on `LineGroupResolver`'s membership/ordering
rules. Adding-a-new-action section lists `SetLineMarkerAction`/`RemoveLineMarkerAction`/`GroupTransitionMarkerAction`
and notes the last replaced `ChangeGroupMarkerAction`. Persistence section adds `Version4Converter`/`createdAtMillis`
to the version chain. Testable-code list gained `LineGroupResolver`, `ColorUtils`,
`DispatchedMarkerIdentifier`/`LineMarkerIdentifier`/`LinePoint`. Verified all referenced class names exist in
`../../../../src/main/java` before writing them in. Read the whole file end to end after editing — reads coherently.
