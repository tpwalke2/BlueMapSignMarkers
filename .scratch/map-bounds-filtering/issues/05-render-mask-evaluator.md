# 05 — Render-mask evaluator

**What to build:** A standalone, dependency-free Java class that answers "is `(x, y, z)` inside
this BlueMap map's render bounds?" given a real `BlueMapMap` id and the fixed
`config/bluemap/maps/` directory. It resolves the id to its `.conf` file by sanitizing each
candidate filename stem the same way BlueMap itself does, parses that file's `render-mask` block
with a hand-rolled parser scoped to its known shape (an array of objects with
`min-x`/`max-x`/`min-y`/`max-y`/`min-z`/`max-z` and `subtract`), and evaluates the box list with
BlueMap's own last-matching-box-wins algorithm (walked from the last entry to the first; an
implicit "include everything" layer if the first entry is `subtract: true`). Any missing file,
unreadable file, unparseable content, or unmatched map id fails open (bounds treated as
unbounded). No Minecraft/Fabric/BlueMap types anywhere in this class's signature, no caching
(caching per real map id belongs to ticket 06). This ticket delivers no user-visible behavior on
its own — it isn't wired into the mod yet — and is verified entirely through its own unit test
suite.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Given a `.conf` file whose `render-mask` matches `run/config/bluemap/maps/world_nether_roof.conf`'s
      two-entry include/subtract shape, points above the `min-y` cutoff evaluate as in-bounds and
      points at/below it evaluate as out-of-bounds, per the worked example in
      `.scratch/map-bounds-filtering/issues/01-render-mask-semantics-and-config-lookup.md`.
- [ ] A missing `render-mask` key, an empty `render-mask: []`, or a missing/unreadable `.conf` file
      all evaluate every point as in-bounds (fail open / unbounded).
- [ ] A `render-mask` whose first entry is `subtract: true` evaluates as "everything except that
      box," not "nothing except what's subtracted."
- [ ] An axis bound omitted from a box entry is treated as unbounded on that axis only, independent
      of the other five axes.
- [ ] Two overlapping boxes covering the same point, one `subtract` and one not, produce different
      verdicts depending on which is listed last in the config — list order determines the result,
      not a symmetric union/subtraction.
- [ ] Malformed/unparseable `render-mask` content fails open (unbounded) rather than throwing.
- [ ] A map id whose sanitized form doesn't match any `.conf` file's sanitized filename stem fails
      open (unbounded).
- [ ] Unit tests (JUnit 5, `src/test/java`) cover all of the above using fixtures copied from
      `run/config/bluemap/maps/*.conf` plus synthetic fixtures for the edge cases, following the
      `SignLinesParser`/`SignLinesParserTest` pattern (`AGENTS.md`'s "Testable vs. game-coupled
      code").
