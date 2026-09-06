Type: grilling
Status: resolved
Blocked by: 01

## Question

Given the hook point(s) found in ticket 01, design how a dye change on one member sign triggers the
earliest-dyed-wins recompute (map Notes) for its whole `LINE`/`SHAPE`/`EXTRUDE` marker:

- Does this need a new mixin, or does an existing Fabric event suffice?
- Does `SignEntry` need a new persisted field for the observed dye (bumping to a new `SignFileVersions`
  entry, per AGENTS.md's persistence-versioning rule), or is dye read live off the block at recompute time
  and never persisted?
- Where does the "earliest non-default-dyed member, falling back to next-earliest on removal/redye" lookup
  live — likely a new resolver alongside `LineGroupResolver`/`ShapeGroupResolver`/`ExtrudeGroupResolver`, or a
  shared helper all three call?
- Does this interact with `SignManager.reset()`'s existing reload-forced re-dispatch (AGENTS.md), or is it a
  fully separate trigger path?

## Answer

**Hook point** (from ticket 01): a new mixin, `@Inject` at `RETURN` of `SignBlockEntity.updateText`, gated on
a `true` return value — subsumes the existing `updateSignText`-only mixin, since dye/glow/ink and text edits
all funnel through `updateText`.

**Q1 — Dye lives in `Representation`, as each sign's own raw dye (not a resolved winner)**: add a `DyeColor`
(or equivalent raw value) field to `SignTransitionResolver.Representation`, populated per-`SignEntry` the same
way `label`/`detail` are. This is deliberate, not just "fold it in" — `computeRepresentation` only ever sees one
`SignEntry` at a time, so `Representation` can only carry *that sign's* dye, never a pre-resolved marker-wide
colour (computing the winner needs every member at once, which `Representation` construction doesn't have
access to). This is also what makes mismatched dyes across members work for free: *any* member's dye edit —
winning or not — changes that sign's own `Representation`, defeats the
`oldRep.detail().equals(newRep.detail()) && !isReload` guard in `computeTransitionAction`, and falls through to
`joinEffect`/the existing leave-recompute path (`lineLeaveAction`/`shapeLeaveAction`/`extrudeLeaveAction`),
which re-derives the marker's colour from the *full current membership* every time — so a losing member's dye
changing, or the current winner's sign breaking, both correctly hand off to whoever's earliest-and-dyed right
now, with no extra design needed beyond Q3's `ColorResolver` taking the whole members list.

**Q2 — Persistence**: confirmed. New persisted field(s) on `SignEntry` for the raw dye per side (front/back),
stored as the `DyeColor` enum name (e.g. `"RED"`), not a pre-resolved hex — bumps to `SignFileVersions.V6` +
`Version6Converter` (pre-V6 entries backfill to "no dye"/undyed, matching an unwaxed vanilla sign's actual
default state). Mirrors how `Version4Converter` added `createdAtMillis` for a need (line ordering) that only
surfaced after the original persistence shape shipped.

**Q3 — Resolver placement**: confirmed. New `ColorResolver` (plain Java, testable, same shape as
`LineGroupResolver`/`ShapeGroupResolver`/`ExtrudeGroupResolver`) implementing "earliest-placed member
(`createdAtMillis`) with a non-default dye wins, re-evaluated every call against the full current membership
list" (map Notes' conflict rule). Called from `SignTransitionResolver` at the same call sites `toPoints(members)`
already runs (both the join path and the leave-recompute path for all three multi-point types), returning the
effective `lineColor`/`fillColor` strings (dye-derived hue + the group's already-configured alpha byte, per map
Notes) or the group's configured `lineColor`/`fillColor` unchanged when `allowPlayerColors` is off or no member
is dyed. `ActionFactory.createSetLineAction`/`createSetShapeAction`/`createSetExtrudeAction` change to accept
these as explicit string parameters instead of reading `markerGroup.lineColor()`/`fillColor()` internally,
keeping `ActionFactory` a dumb builder.

**Reload interaction**: no special-casing needed. `computeTransitionAction`'s `isReload` flag already forces
`joinEffect` regardless of the detail-equality guard, so a reload that flips a group's `allowPlayerColors` (or
any other field — `Representation.group()` is compared by full-record equality) already re-dispatches every
affected sign through the same path, which re-runs `ColorResolver` under the new config.
