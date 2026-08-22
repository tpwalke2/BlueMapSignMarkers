# Grilling: Decompose per-map dispatch restructuring into implementation tickets

Type: grilling
Status: resolved

## Question

Today's dispatch (`MarkerSetIdentifier`/`ActionFactory` and friends) fans out to every map of a
*world*, with no per-map distinction. This feature needs dispatch keyed by actual `BlueMapMap` id
instead, so each map's own `render-mask` (per
`.scratch/map-bounds-filtering/issues/01-render-mask-semantics-and-config-lookup.md`'s resolved
semantics) can gate whether a given sign's marker exists on that map.

Break this restructuring into concrete implementation tickets. Needs to cover at minimum:

- Where the render-mask evaluator (parsing `config/bluemap/maps/<id>.conf` per the hand-rolled
  parser from `.scratch/map-bounds-filtering/issues/02-hocon-parsing-approach.md`) lives, and when
  it's invoked relative to today's per-world fanout.
- How `MarkerSetIdentifier`/`ActionFactory` change shape to carry a map id instead of (or in
  addition to) a world id.
- How `LINE` markers clip to their in-bounds points per map (per the map's Destination) rather
  than being dropped wholesale when only some member signs are out of bounds on a given map.
- Fail-open behavior (missing/unreadable/unparseable map config → treat as unbounded) at whichever
  layer ends up owning the per-map check.
- Re-evaluation on `/bluemap reload` → `SignManager.reset()`, consistent with existing config-reload
  handling.

Output: a small ordered list of implementation tickets (or a decision that one ticket suffices),
each sized to land as its own PR.

## Answer

Grilled to five decisions (see `.scratch/map-bounds-filtering/map.md` for the one-line gists);
full detail below. Net result: **two** implementation tickets, sequential.

1. **Invocation point**: the mask evaluator is invoked from `BlueMapAPIConnector`, not pushed into
   `SignManager`/`ActionFactory`/`SignTransitionResolver`. It's the only layer that ever sees real
   `BlueMapMap` ids (via `world.getMaps()`), and keeping the mask check there matches the existing
   testable-core / game-coupled-glue split in `AGENTS.md`.
2. **No core model change**: `SignEntryKey.parentMap`/`MarkerSetIdentifier.mapId` keep meaning
   "world id" exactly as today. `ActionFactory`/`SignTransitionResolver` are untouched. Per-map
   masking is purely a filter `BlueMapAPIConnector` applies while it already loops over
   `world.getMaps()` — the Destination's "keyed by actual `BlueMapMap` id" happens inside that
   existing loop, not by threading real map ids through the whole pipeline.
3. **Cache the parsed mask** per real `BlueMapMap` id, invalidated at the same two points that
   already invalidate `markerSetsCache`: `clearMarkerSetsCache()` (config reload) and
   `resetQueue()` (BlueMap disable/enable cycle). Reuses existing invalidation plumbing instead of
   adding a new lifecycle hook, and picks up an admin's `render-mask` edit on the same
   `/bluemap reload` that already re-evaluates everything else.
4. **No point-level clipping** (this revises the map's Destination text, which described clipping
   — see the map's Decisions-so-far and updated Destination). Per-map gating for LINE/SHAPE is an
   all-or-nothing "is any member point inside this map's bounds?" test, same as POI's single-point
   test — not a per-point subset render. Simpler, no duplicated 2-/3-point-minimum logic needed on
   a second, per-map layer. Deferred: true clipping, if wanted later, is new scope requiring its
   own future ticket.
5. **One integration ticket, not two**: POI-in-bounds (one point) and LINE/SHAPE-in-bounds (any of
   N points) collapse to the same predicate, and both need the same plumbing change — today
   `BlueMapAPIConnector.getMarkerSets`/`markerSetsCache` flatten to a bare `List<MarkerSet>`,
   losing which real `BlueMapMap` each one came from; gating per real map means retaining that
   identity through to `applyToMarkerSets`. That shared plumbing change is the substantial part of
   the work, so splitting POI and LINE/SHAPE into separate PRs would just spread one change across
   two PRs with no isolation benefit.

### Resulting implementation tickets (for the eventual plan doc, not further wayfinder tickets —
see Note below)

**Ticket A — Render-mask evaluator.** A plain-Java, dependency-free class in `core/` answering
"is `(x, y, z)` inside this map's render bounds?", given a sanitized map id and the fixed
`config/bluemap/maps/` directory. Implements the combination algorithm, per-axis defaults, and map
id sanitization from
`.scratch/map-bounds-filtering/issues/01-render-mask-semantics-and-config-lookup.md`, using the
hand-rolled parser approach from
`.scratch/map-bounds-filtering/issues/02-hocon-parsing-approach.md`. Fails open (treats as
unbounded) on any missing/unreadable/unparseable config, per the map's Destination and
`AGENTS.md`'s "mod must never crash the server" principle. Unit-tested with fixtures copied from
`run/config/bluemap/maps/*.conf` plus synthetic missing/empty/malformed cases. No BlueMap types, no
caching, no clipping. Sized as its own PR; no dependency on Ticket B.

**Ticket B — Per-map render-bounds gating in `BlueMapAPIConnector`.** Depends on Ticket A.
Restructures `markerSetsCache`/`getMarkerSets`/`applyToMarkerSets` to retain each cached
`MarkerSet`'s originating `BlueMapMap` id through to apply-time (decision 5). Adds the mask-gate
check (decision 1) using Ticket A's evaluator, cached per real map id and invalidated alongside
`markerSetsCache` (decision 3), with no core-model changes (decision 2). POI add/update and
LINE/SHAPE set/remove are all-or-nothing per real map based on "any relevant point in bounds"
(decision 4) — POI's single point, or the LINE/SHAPE member point list. Sized as its own PR.

Note: per this map's "Plan, don't do" default, these two are recorded here as the decomposition
this ticket was asked to produce, not spun up as separate wayfinder decision tickets — there's
nothing left to decide about them, only to build, once the map reaches its destination (the
`agent-context/plans/` spec/plan doc).
