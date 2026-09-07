# 08 — End-to-end: player-dyed markers render

**What to build:** The feature, working in-game: a player dyes a sign belonging to a `LINE`/`SHAPE`/`EXTRUDE`
group with `allowPlayerColors` on, and that marker's color actually updates in BlueMap. Breaking or redyeing
the currently-winning member's sign hands off correctly to the next-earliest dyed survivor. A group without
`allowPlayerColors` renders exactly as it does today. This ticket wires everything built in 05-07 together at
the actual dispatch call sites. See `agent-context/plans/player-marker-colors/spec.md`'s "Conflict resolution"
and "Reload interaction" sections.

**Blocked by:** 06, 07

**Status:** ready-for-agent

- [ ] `ActionFactory.createSetLineAction`/`createSetShapeAction`/`createSetExtrudeAction` accept explicit
      resolved `lineColor`/`fillColor` string parameters instead of reading `markerGroup.lineColor()`/
      `fillColor()` internally
- [ ] `SignTransitionResolver` calls `ColorResolver` at the same call sites `toPoints(members)` already runs,
      for both the join path and the leave-recompute path (`lineLeaveAction`/`shapeLeaveAction`/
      `extrudeLeaveAction`), across all three multi-point types
- [ ] Confirm `/bluemap reload` picks up an `allowPlayerColors` flip with no extra wiring (the existing
      `isReload` force-recompute + full-record `Representation.group()` equality already covers it)
- [ ] Manual verification via `runServer` (this mod's game-coupled code has no automated coverage, per
      AGENTS.md):
  - [ ] Place a multi-sign `LINE` (or `SHAPE`/`EXTRUDE`) in a group with `allowPlayerColors` on; dye one member;
        confirm the marker's color updates in BlueMap
  - [ ] Dye a second member with a different color; confirm the earlier-placed member's dye still wins
  - [ ] Break the currently-winning dyed member's sign; confirm the marker hands off to the next-earliest dyed
        survivor
  - [ ] Confirm a group with `allowPlayerColors` off is completely unaffected by member dye
