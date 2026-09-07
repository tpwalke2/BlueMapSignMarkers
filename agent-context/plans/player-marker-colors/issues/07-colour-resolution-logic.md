# 07 — Colour-resolution logic

**What to build:** Given a `LINE`/`SHAPE`/`EXTRUDE` marker's current members, the system correctly computes
which member's dye colours the marker — the earliest-placed member (`createdAtMillis`) with a non-default dye
wins, re-evaluated fresh against the current membership every time so removing or redyeing the winner hands
off to the next-earliest dyed survivor. Separately, a dye-only sign edit stops being silently swallowed by
`SignTransitionResolver`'s existing detail-equality no-op guard. This ticket is pure logic, fully verified by
unit tests — no game engine involved. See `agent-context/plans/player-marker-colors/spec.md`'s "Representation"
and "Conflict resolution" sections for the full rationale (including why mismatched dyes across members
resolve correctly with no extra design).

**Blocked by:** 05 (needs the dye field on `SignEntry`)

**Status:** ready-for-agent

- [ ] `SignTransitionResolver.Representation` gains each sign's own raw dye (not a resolved marker-wide
      colour — `computeRepresentation` only ever sees one `SignEntry` at a time)
- [ ] Confirm a dye-only change (detail unchanged) now falls through
      `oldRep.detail().equals(newRep.detail()) && !isReload` to the existing recompute path, instead of being
      dropped as a no-op
- [ ] New `ColorResolver` (plain Java, no Minecraft/Fabric/BlueMap types): takes a group's current members plus
      its `MarkerGroup`, returns the effective `lineColor`/`fillColor` — the group's configured value unchanged
      when `allowPlayerColors` is off or no member is dyed; otherwise the winning member's dye RGB
      (`DyeColor.getTextureDiffuseColor()`) combined with the alpha byte already present in the group's
      configured color
- [ ] Unit tests on `ColorResolver`: no flag / no dye → group default; single dyed member → that dye; multiple
      dyed members → earliest wins; winning member removed → next-earliest dyed survivor takes over; all
      undyed → group default
- [ ] Unit tests on `SignTransitionResolver`: a dye-only change on a `LINE`/`SHAPE`/`EXTRUDE` member now
      produces a non-null recompute action where today it would return `null`
