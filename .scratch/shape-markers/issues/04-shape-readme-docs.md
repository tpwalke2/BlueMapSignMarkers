# 04 — Docs: README config reference for SHAPE

**What to build:** A server admin reading `README.md` can find `SHAPE` documented as a valid marker group `type`,
understand the `fillColor` field and the widened `lineWidth`/`lineColor` scope, and copy a working example config
block for a `SHAPE` group — the same level of documentation `LINE` groups already have.

See `.scratch/shape-markers/spec.md` for full context.

**Blocked by:** 01, 02, 03 — docs should describe the feature's final, complete behavior.

**Status:** resolved

- [x] `README.md`'s config reference lists `SHAPE` as a valid `type` value alongside `POI`/`LINE`.
- [x] `README.md` documents `fillColor` (format, default, `SHAPE`-only scope) next to the existing
      `lineWidth`/`lineColor` documentation, and updates `lineWidth`/`lineColor`'s documented scope from "`LINE`
      only" to "`LINE`/`SHAPE` only".
- [x] `README.md` includes an example `SHAPE` group config block, alongside the existing `LINE` example.

## Comments

Completed as a side effect of ticket 01, since the underlying feature (and tickets 02/03's transition/warning
behavior it needed to describe accurately) was already done. Added `SHAPE` to the `type` list, a `fillColor` row,
widened `lineWidth`/`lineColor`'s documented scope to `LINE`/`SHAPE`, generalized the mismatch-warning paragraph
beyond just `POI`↔`LINE`, and added a `[region]`/`SHAPE` example group block plus a matching sentence in the
walkthrough paragraph. See ticket 01's Comments for the full implementation summary.
