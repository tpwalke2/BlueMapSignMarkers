# Plan: Filter markers outside a map's render bounds

Source: [GitHub issue #67](https://github.com/tpwalke2/BlueMapSignMarkers/issues/67) — a nether-roof
map (`min-y: 127`) still had markers created for signs below that line.

Wayfinder map: `.scratch/map-bounds-filtering/map.md` (destination reached; this plan collapses
tickets 01-04 into a buildable spec; ticket 08 later expanded scope to all four `render-mask`
shape types, reflected below).

## Problem Statement

BlueMapSignMarkers dispatches every sign marker to every BlueMap map of a world, with no awareness
of that individual map's own render bounds. BlueMap maps can restrict what terrain they render via
a `render-mask` (e.g. a nether-roof map that only renders `y >= 127`), and a server admin
reasonably expects a sign's marker to only show up on maps that actually render the area the sign
is in. Today it shows up everywhere, including on maps whose render mask explicitly excludes that
position — on the 3D view (where it floats disconnected from any rendered terrain) and in that
map's marker sidebar/list.

## Solution

BlueMapSignMarkers reads each BlueMap map's own `config/bluemap/maps/<id>.conf` file (BlueMap's API
exposes no bounds accessor, so this is an interim stand-in, swappable later if BlueMap ever ships
something like `isInsideRenderBounds(Vec3)`) and evaluates its `render-mask` the same way BlueMap
itself does — the general list of additive/subtractive mask entries (box, circle, ellipse, and
polygon shapes, per BlueMap's documented mask types), not a simplified min-y/max-y-only
approximation or a box-only approximation. A sign's marker is only created/kept on a given map if its position (or,
for LINE/SHAPE, any one of its member points) is inside that map's render bounds; if not, the
marker is actively removed from that map, so a marker never lingers on a map whose bounds exclude
it — including markers that already existed before this feature shipped. Bounds are re-evaluated on
the existing `/bluemap reload` flow, same as every other config-reload-driven behavior — no new
server-restart requirement, though a plain server restart alone does not trigger this (see
Implementation Decisions).

## User Stories

1. As a server admin with a nether-roof map (`render-mask: min-y: 127`), I want a `[poi]` sign
   placed below y=127 to not appear as a marker on that map, so the sidebar/3D view for that map
   only lists markers relevant to what it actually renders.
2. As a server admin with a nether-roof map, I want a `[poi]` sign placed above y=127 to appear
   normally on that map, so in-bounds signs are unaffected.
3. As a server admin running a single default map with no `render-mask` configured, I want every
   sign's marker to behave exactly as it does today, so this feature is invisible unless I've
   actually configured render bounds somewhere.
4. As a server admin with multiple maps per world (e.g. a normal map and a nether-roof map for the
   same dimension), I want a sign's marker to be gated independently per map, so it can appear on
   one map and be excluded from another based on each map's own bounds.
5. As a server admin, I want a sign moved (removed and replaced) from inside a map's bounds to
   outside them to have its marker removed from that map, so a relocated sign never leaves a stale
   marker behind.
6. As a server admin, I want a sign moved from outside a map's bounds to inside them to have its
   marker appear on that map, so bounds gating works symmetrically in both directions.
7. As a server admin editing a `render-mask` in a map's `.conf` file, I want `/bluemap reload` to
   re-evaluate every existing sign against the new bounds, so a bounds edit takes effect without a
   server restart, consistent with how other config edits (marker group icon/offset/visibility)
   already behave on reload.
8. As a server admin upgrading from a version of the mod that predates this feature, I want any
   marker that was incorrectly created outside its map's render bounds (the exact bug in issue #67)
   to be swept away automatically the first time I run `/bluemap reload` after upgrading, so I don't
   need a manual cleanup step or a fresh world/marker wipe.
9. As a server admin, I want a plain server restart after upgrading (with no explicit
   `/bluemap reload`) to leave existing markers untouched rather than silently attempting (and
   failing) a sweep, so behavior is predictable and documented rather than a partial, unexplained
   fix.
10. As a server admin with a LINE-type marker group, I want the line marker to appear on a given map
    if at least one of its member signs is inside that map's bounds, so a line that only partially
    overlaps a bounded map isn't dropped entirely just because some members happen to sit outside
    those bounds.
11. As a server admin with a LINE-type marker group, I want the line marker removed from a map when
    none of its member signs are inside that map's bounds, so a fully-out-of-bounds line doesn't
    linger.
12. As a server admin with a SHAPE-type marker group, I want the same any-member-in-bounds gating
    LINE gets, so SHAPE and LINE behave consistently with respect to render bounds.
13. As a server admin, I want a map with no `render-mask` key (or an empty `render-mask: []`) to be
    treated as fully unbounded, matching BlueMap's own documented default ("renders the entire
    world"), so omitting the key is a safe no-op.
14. As a server admin whose map's `render-mask` starts with a `subtract` entry, I want that
    evaluated as "render everything except this," matching BlueMap's own semantics exactly (not a
    literal empty-set-minus-subtraction reading), so a mask copied from BlueMap's own documentation
    examples behaves identically in BSM's evaluation.
15. As a server admin, I want a corrupted, unreadable, or unparseable map `.conf` file to result in
    that map's bounds being treated as unbounded (fail open) rather than crashing the server or
    silently hiding every marker on that map, consistent with this mod's "never crash the server"
    principle.
16. As a server admin, I want a map id that doesn't correspond to any file under
    `config/bluemap/maps/` to also fail open (unbounded), so a misconfigured or unusual setup
    degrades gracefully instead of erroring.
17. As a mod maintainer, I want the render-mask evaluation logic to live in a plain-Java class with
    no Minecraft/Fabric/BlueMap types in its signature, so it can be unit-tested directly against
    fixture `.conf` files rather than only being verifiable manually.
18. As a mod maintainer, I want the per-map gating to require no changes to
    `MarkerSetIdentifier`, `ActionFactory`, `SignTransitionResolver`, `SignManager`, or persisted
    sign data, so this feature stays isolated to `BlueMapAPIConnector` and doesn't risk regressing
    the existing transition/dispatch logic those classes already implement.
19. As a mod maintainer, I want the parsed render-mask cached per real `BlueMapMap` id and
    invalidated at the same points `markerSetsCache` already is (config reload, BlueMap
    disable/enable), so a `.conf` file isn't re-read and re-parsed on every single marker dispatch.
20. As a mod maintainer, I want no new runtime dependency added for HOCON parsing, so the project's
    jar stays free of a first-ever bundled/shaded dependency for what is a narrow, well-bounded read
    (a handful of numeric fields and a boolean per mask entry).
21. As a server admin using `circle`, `ellipse`, or `polygon` render-mask entries (not just `box`),
    I want a sign's marker gated against the entry's actual shape, so a non-box mask on any of my
    maps isn't silently mistaken for an unbounded-on-XZ box — see
    `.scratch/map-bounds-filtering/issues/07-non-box-render-mask-types.md` and
    `.scratch/map-bounds-filtering/issues/08-non-box-mask-handling-decision.md`.

## Implementation Decisions

### New module: render-mask evaluator (plain Java, `core/`)

- A new dependency-free class (no Minecraft/Fabric/BlueMap types in its signature) that answers "is
  `(x, y, z)` inside this map's render bounds?" given a real `BlueMapMap` id and the fixed
  `config/bluemap/maps/` directory path.
- **Config file lookup**: scans `config/bluemap/maps/*.conf`, sanitizing each filename stem the same
  way BlueMap itself does (`\W` → `_`, i.e. every non-word character replaced with an underscore),
  and matches against the given map id. This mirrors `BlueMapConfigManager.sanitiseMapId` rather
  than assuming the map id is always a literal filename stem, so an oddly-named config file (spaces,
  dashes) still resolves to the same id BlueMap itself computed.
- **`render-mask` parsing**: a hand-rolled parser scoped strictly to this block's known shape (not a
  general HOCON library — see the map's HOCON-parsing-approach decision) — strip `#` line comments,
  locate the `render-mask: [ ... ]` array (matching nested `{`/`}` boundaries), split into
  `{ ... }` object chunks, and parse each chunk by its `type` discriminator
  (`box`/`circle`/`ellipse`/`polygon`; absent `type` defaults to `box`, matching BlueMap's own
  default) into one of four shape records:
  - `box`: `min-x`/`max-x`/`min-y`/`max-y`/`min-z`/`max-z` (bare integers), each absent/commented
    field defaulting to unbounded on that axis (`Integer.MIN_VALUE`/`MAX_VALUE`, matching
    BlueMap's own `BoxMaskConfig` defaults).
  - `circle`: `center-x`, `center-z`, `radius` (bare numbers), plus optional `min-y`/`max-y`
    (unbounded if absent); a point is inside when its XZ distance from the center is `<= radius`
    and its Y falls in range.
  - `ellipse`: `center-x`, `center-z`, `radius-x`, `radius-z`, plus optional `min-y`/`max-y`; a
    point is inside when `((x-cx)/rx)^2 + ((z-cz)/rz)^2 <= 1` and its Y falls in range.
  - `polygon`: `shape` (an array of `{x, z}` pairs, 3+ points), plus optional `min-y`/`max-y`; a
    point is inside via standard point-in-polygon (ray casting) on XZ and its Y falls in range.
  All four also read the shared `subtract` (bare boolean) key, comma-optional between
  entries/pairs. An unrecognized `type` value fails open for that single map (logged), consistent
  with the module's overall fail-open contract.
- **Combination algorithm** (matching BlueMap's `CombinedMask` exactly, not a symmetric
  union/subtract reading): walk the parsed entry list from **last** entry to **first**, regardless
  of each entry's shape type; the first entry in that reverse walk whose shape contains the point
  decides the verdict outright via its own `subtract` flag (`subtract: true` → excluded, otherwise
  → included). If no entry's shape contains the point, the verdict is "included" only when the
  list itself is empty; a non-empty list with no matching entry means excluded. Special case: if
  the very first entry in list order is `subtract: true`, an implicit "include everything" layer
  is inserted beneath it, so a mask that starts with a subtract entry means "render everything
  except this" rather than "render nothing except what's subtracted from nothing."
- **Fail-open behavior**: any missing file, unreadable file, or parse failure (malformed syntax, a
  chunk that doesn't parse) results in treating that map as fully unbounded (every point included),
  logged at a level appropriate for an unexpected-but-non-fatal condition. Same fail-open behavior
  applies when no config file's sanitized stem matches the given map id.
- No caching, no `BlueMapMap`/`BlueMapAPI` types anywhere in this class — caching per real map id is
  `BlueMapAPIConnector`'s responsibility (below), keeping this module a pure, stateless-per-call
  evaluator that's straightforward to unit test.

### `BlueMapAPIConnector` changes

- **Invocation point**: the render-mask evaluator is invoked from `BlueMapAPIConnector` only. It's
  the only layer that ever resolves real `BlueMapMap` ids (via `world.getMaps()`), matching this
  project's existing testable-core/game-coupled-glue split. `SignManager`, `ActionFactory`, and
  `SignTransitionResolver` are unchanged.
- **Retaining real map identity through to apply-time**: today, `markerSetsCache`/`getMarkerSets`
  flatten a `MarkerSetIdentifier` (keyed by *world* id) to a bare `List<MarkerSet>` covering every
  `BlueMapMap` of that world, and `applyToMarkerSets` applies the same mutation to all of them
  uniformly. This flattening loses which real map each `MarkerSet` came from — needed to gate
  per-map. The cache and the apply path are restructured to retain each `MarkerSet`'s originating
  real `BlueMapMap` id (e.g. keyed by, or paired with, that id) so gating can be applied
  independently per map inside the existing per-world loop, rather than by threading real map ids
  through `MarkerSetIdentifier`/`ActionFactory`/the rest of the pipeline.
- **Per-map render-mask cache**: the parsed render-mask evaluator is cached per real `BlueMapMap`
  id, invalidated at the same two points that already invalidate `markerSetsCache`:
  `clearMarkerSetsCache()` (fired on config reload) and `resetQueue()` (fired on a genuine BlueMap
  disable/enable cycle). This reuses existing invalidation plumbing rather than adding a new
  lifecycle hook, and picks up an admin's `render-mask` edit on the same `/bluemap reload` that
  already re-evaluates everything else.
- **Gating rule (all marker types)**: for each real map a dispatched action would otherwise apply
  to, evaluate whether the action's relevant point(s) are inside that map's render bounds — a single
  `(x, y, z)` for POI, or "any one of" the member point list for LINE/SHAPE (all-or-nothing per map,
  matching the single-point POI test's shape; **no per-point clipping** — a LINE/SHAPE marker either
  renders in full on a given map or not at all there, never a partial-points subset). If in bounds,
  apply the action's normal effect (add/update/set) to that map's `MarkerSet` only. If **not** in
  bounds, apply an active removal of that marker id from that map's `MarkerSet` instead of skipping
  the add/update/set — this is what makes a previously-created, now-out-of-bounds marker actually
  disappear rather than silently persisting, and is exactly what sweeps a stale marker left over
  from before this feature shipped. Explicit remove actions (a sign's representation genuinely
  leaving, independent of bounds) are unaffected — they already remove unconditionally on every real
  map, with no masking needed.
- **No core-model changes**: `SignEntryKey.parentMap`/`MarkerSetIdentifier.mapId` keep meaning
  "world id" exactly as today. `ActionFactory`, `SignTransitionResolver`, `SignManager`, and
  persisted sign data are all untouched — per-map masking is purely a filter applied inside
  `BlueMapAPIConnector`'s existing per-world-map loop.

### Sweep of pre-existing out-of-bounds markers on upgrade

- No separate migration step. `SignManager.reset()` (fired by `IResetHandler` on `/bluemap reload`)
  already walks the full sign cache with `isReload=true`, and
  `SignTransitionResolver.computeTransitionAction` already force-re-dispatches an Add/Set action for
  every sign under `isReload=true` even when its representation is unchanged (this existing behavior
  exists to pick up marker-group icon/offset/visibility edits on reload). Every sign therefore
  already re-runs through the new per-map gate on every `/bluemap reload`, config-changed or not —
  and per the gating rule above, a gate failure now actively removes the stale marker instead of
  no-op'ing, which is what turns this existing reload behavior into an automatic sweep.
- **Caveat — restart alone is not enough**: `BlueMapAPIConnector.onEnable`'s `fireReset()` only runs
  on a genuine BlueMap disable/enable cycle (tracked via `disabledSinceLastEnable`), not on the very
  first `onEnable()` a server ever sees after boot — a deliberate existing guard that avoids
  discarding startup-queued actions. Restarting the server after upgrading the mod jar does **not**,
  by itself, trigger the sweep. This is a documentation callout, not new engineering scope: the
  released changelog/README must tell admins to run `/bluemap reload` once after upgrading to sweep
  any markers that existed outside their map's render bounds.

### No new dependency

No HOCON parsing library (`com.typesafe:config`, `configurate-hocon`, etc.) is added. The syntax
surface needed is small and closed (a `render-mask` array of objects with six known numeric keys
and one boolean key, no substitutions/includes/nesting), and this project's `build.gradle` has no
shading/jar-in-jar mechanism today — adding either library would be this project's first bundled
runtime dependency for a narrow, well-bounded read. The hand-rolled parser above is the sole parsing
mechanism.

## Testing Decisions

- Good tests here exercise external behavior — the evaluator's `boolean`/bounds-test result for
  given input — not its internal parsing steps.
- **Render-mask evaluator**: unit-tested directly (JUnit 5, `src/test/java`), following the
  `SignLinesParser`/`SignLinesParserTest` precedent (`AGENTS.md`'s "Testable vs. game-coupled code").
  Fixtures: `run/config/bluemap/maps/world_nether_roof.conf` (the real two-entry
  include/subtract case worked through by hand in the render-mask-semantics research), plus
  synthetic fixtures for: missing file, empty file, empty/absent `render-mask` key, a mask whose
  first entry is `subtract: true`, malformed/unparseable content, an entry missing some axis bounds,
  and overlapping boxes where list order changes the verdict (two boxes covering the same point,
  one `subtract` and one not, in each list order).
- **`BlueMapAPIConnector`'s per-map gating**: no automated coverage, consistent with this class
  today (it has none — constructing it touches live `BlueMapAPI` static state). Verified manually
  via `runServer`: place a nether-roof-style map (`render-mask` with a `min-y` cutoff) alongside a
  normal map for the same world; place `[poi]` signs above and below the cutoff and confirm each
  only shows on the appropriate map(s); edit the `render-mask` and run `/bluemap reload`, confirming
  markers move in/out of view accordingly; construct a LINE/SHAPE group with members straddling the
  cutoff and confirm the marker appears on the nether-roof map (any member in bounds) and disappears
  once all members are moved out of bounds. The upgrade-sweep behavior is verified by manually
  placing a marker id into a map's `MarkerSet` (simulating a pre-feature marker) outside that map's
  configured bounds, then confirming `/bluemap reload` removes it.

## Out of Scope

- **Point-level clipping** for LINE/SHAPE markers (rendering only the in-bounds portion of a line or
  polygon that straddles a map's bounds) — deferred; today's all-or-nothing per-map gating was
  chosen over clipping specifically to avoid duplicating the 2-/3-point-minimum logic at a second
  layer (see ticket 03's decision 4). A future ticket can revisit true clipping if wanted.
- **Automatic sweep on a plain server restart** (without an explicit `/bluemap reload`) — the
  existing `disabledSinceLastEnable` startup-vs-reload guard in `BlueMapAPIConnector.onEnable` is
  left untouched; this is a documentation callout to admins, not a code change (see ticket 04's
  decision).
- **A BSM-side config field for render bounds** — the entire point of this feature is that BSM
  infers per-map visibility from BlueMap's own `render-mask` config; there is no new
  `BMSM-Core.json` field.
- **Adopting a BlueMap-native bounds accessor** — BlueMap API 2.8.0 exposes no such accessor;
  reading the map's own `.conf` file is an explicit interim stand-in, swappable later if BlueMap
  ever ships something like `isInsideRenderBounds(Vec3)`.
- **A general-purpose HOCON parser or library dependency** — explicitly rejected in favor of the
  hand-rolled, scoped parser (see Implementation Decisions).

## Further Notes

- Wayfinder tickets with full research/decision detail behind this plan:
  `.scratch/map-bounds-filtering/issues/01-render-mask-semantics-and-config-lookup.md` (combination
  algorithm, missing-axis defaults, map id ↔ config file, config directory location — all confirmed
  against BlueMap's own source, not just its wiki),
  `.scratch/map-bounds-filtering/issues/02-hocon-parsing-approach.md` (hand-rolled parser vs.
  library survey),
  `.scratch/map-bounds-filtering/issues/03-dispatch-restructuring-decomposition.md` (the two-ticket
  decomposition this plan's Implementation Decisions section draws from),
  `.scratch/map-bounds-filtering/issues/04-existing-marker-sweep-on-upgrade.md` (the upgrade-sweep
  decision and its restart-vs-reload caveat).
- Sized as two implementation PRs, sequential: the render-mask evaluator first (no dependency on the
  connector work), then the `BlueMapAPIConnector` restructuring/gating (depends on the evaluator).
