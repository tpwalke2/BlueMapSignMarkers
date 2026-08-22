# 05 — Render-mask evaluator

**What to build:** A standalone, dependency-free Java class that answers "is `(x, y, z)` inside
this BlueMap map's render bounds?" given a real `BlueMapMap` id and the fixed
`config/bluemap/maps/` directory. It resolves the id to its `.conf` file by sanitizing each
candidate filename stem the same way BlueMap itself does, parses that file's `render-mask` block
with a hand-rolled parser scoped to its known shape (an array of objects, each carrying a `type`
discriminator — `box`/`circle`/`ellipse`/`polygon`, defaulting to `box` when absent — plus that
type's own fields and a shared `subtract`), and evaluates the resulting entry list with BlueMap's
own last-matching-entry-wins algorithm (walked from the last entry to the first, regardless of
shape; an implicit "include everything" layer if the first entry is `subtract: true`). Any missing
file, unreadable file, unparseable content, unmatched map id, or unrecognized `type` value fails
open (bounds treated as unbounded). No Minecraft/Fabric/BlueMap types anywhere in this class's
signature, no caching (caching per real map id belongs to ticket 06). This ticket delivers no
user-visible behavior on its own — it isn't wired into the mod yet — and is verified entirely
through its own unit test suite.

**Shape scope (expanded per
[`08 — How should RenderMaskEvaluator handle circle/ellipse/polygon mask entries?`](08-non-box-mask-handling-decision.md)):**
all four of BlueMap's documented mask types, not box only —
- `box`: `min-x`/`max-x`/`min-y`/`max-y`/`min-z`/`max-z` (each independently unbounded if absent).
- `circle`: `center-x`, `center-z`, `radius`, optional `min-y`/`max-y`.
- `ellipse`: `center-x`, `center-z`, `radius-x`, `radius-z`, optional `min-y`/`max-y`.
- `polygon`: `shape` (array of `{x, z}` pairs, 3+ points), optional `min-y`/`max-y`, point-in-polygon
  via ray casting on the XZ plane.

See `.scratch/map-bounds-filtering/issues/07-non-box-render-mask-types.md` for the field-shape
citations and `agent-context/plans/map-bounds-filtering-plan.md`'s "New module: render-mask
evaluator" section for the full write-up. `RenderMaskBox`/`RenderMaskEvaluator` as they exist
today (box-only) need reworking into a `type`-dispatching parser plus one evaluatable shape record
per type (e.g. a small `RenderMaskShape` interface with `contains(x, y, z)`, implemented by
`RenderMaskBox`, `RenderMaskCircle`, `RenderMaskEllipse`, `RenderMaskPolygon`), not a from-scratch
rewrite of the file-lookup/comment-stripping/bracket-matching/combination-algorithm parts, which
are shape-agnostic and already correct.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Given a `.conf` file whose `render-mask` matches `run/config/bluemap/maps/world_nether_roof.conf`'s
      two-entry include/subtract shape, points above the `min-y` cutoff evaluate as in-bounds and
      points at/below it evaluate as out-of-bounds, per the worked example in
      `.scratch/map-bounds-filtering/issues/01-render-mask-semantics-and-config-lookup.md`.
- [x] A missing `render-mask` key, an empty `render-mask: []`, or a missing/unreadable `.conf` file
      all evaluate every point as in-bounds (fail open / unbounded).
- [x] A `render-mask` whose first entry is `subtract: true` evaluates as "everything except that
      box," not "nothing except what's subtracted."
- [x] An axis bound omitted from a box entry is treated as unbounded on that axis only, independent
      of the other five axes.
- [x] Two overlapping boxes covering the same point, one `subtract` and one not, produce different
      verdicts depending on which is listed last in the config — list order determines the result,
      not a symmetric union/subtraction.
- [x] Malformed/unparseable `render-mask` content fails open (unbounded) rather than throwing.
- [x] A map id whose sanitized form doesn't match any `.conf` file's sanitized filename stem fails
      open (unbounded).
- [x] A `circle` entry (`center-x`/`center-z`/`radius`, optional `min-y`/`max-y`) evaluates points
      inside its XZ radius (and Y range, if set) as matching that entry, and points outside as not.
- [x] An `ellipse` entry (`center-x`/`center-z`/`radius-x`/`radius-z`, optional `min-y`/`max-y`)
      evaluates independently-radiused X/Z containment correctly, not just circular containment.
- [x] A `polygon` entry (`shape: [{x, z}, ...]`, optional `min-y`/`max-y`) evaluates XZ containment
      via point-in-polygon, including a non-convex polygon fixture.
- [x] An entry with no `type` key defaults to `box` (matching today's implicit behavior).
- [x] An entry with an unrecognized `type` value fails open for that map (unbounded), logged,
      rather than being silently mis-parsed as a box.
- [x] A `render-mask` list mixing shape types (e.g. a `box` include and a `circle` subtract)
      evaluates last-matching-entry-wins across shapes, not just within one shape type.
- [x] Unit tests (JUnit 5, `src/test/java`) cover all of the above using fixtures copied from
      `run/config/bluemap/maps/*.conf` plus synthetic fixtures for the edge cases, following the
      `SignLinesParser`/`SignLinesParserTest` pattern (`AGENTS.md`'s "Testable vs. game-coupled
      code").
