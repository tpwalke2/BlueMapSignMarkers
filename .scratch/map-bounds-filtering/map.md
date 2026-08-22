# Map: Filter markers outside a map's render bounds

Labels: wayfinder:map

## Destination

A spec/plan (`agent-context/plans/`) for how BlueMapSignMarkers fully suppresses a marker — not
just off the 3D render, off that map's sidebar/list too — on any BlueMap map whose `render-mask`
excludes the sign's position. BlueMap API 2.8.0 exposes no bounds accessor, so BSM reads the
target map's own `config/bluemap/maps/<id>.conf` file as an interim stand-in, evaluating the
general list of additive/subtractive mask entries — box, circle, ellipse, and polygon shapes — the
same way BlueMap itself does (not a simplified min-y/max-y or box-only approximation) — swappable
later if BlueMap ever ships something like
`isInsideRenderBounds(Vec3)`. This requires restructuring today's dispatch (which fans out to
every map of a *world* with no per-map distinction) to be keyed by actual `BlueMapMap` id. Fails
open (treat as unbounded) on any missing/unreadable/unparseable map config. Bounds are
(re-)evaluated on the existing `/bluemap reload` → `SignManager.reset()` flow, same as other
config-reload behavior — no new server restart requirement. `LINE`/`SHAPE` markers are
all-or-nothing per map: a marker renders on a given map if *any* of its member signs is inside
that map's bounds there, rather than being clipped to just the in-bounds points (revised from an
earlier plan to clip — see
`.scratch/map-bounds-filtering/issues/03-dispatch-restructuring-decomposition.md`; true clipping
is future scope if wanted later).

Source: [GitHub issue #67](https://github.com/tpwalke2/BlueMapSignMarkers/issues/67) — a
nether-roof map (`min-y: 127`) still had markers created for signs below that line.

## Notes

- Domain: BlueMap map/marker terminology — consult `mattpocock-skills:domain-modeling` if terms
  need pinning down.
- Plan doc convention: `agent-context/plans/` (see `AGENTS.md`).
- Sample config with the exact `render-mask` shape this feature must parse:
  `run/config/bluemap/maps/world_nether_roof.conf` (lines ~74-90) — a list of boxes, each
  optionally `subtract: true`.
- No BSM-side config field for this — the whole point is the player never has to know which
  marker group is visible on which map; BSM infers it from BlueMap's own per-map config.

## Decisions so far

- HOCON parsing for `render-mask`: hand-roll a minimal scoped parser instead of adding a HOCON
  library dependency — see `.scratch/map-bounds-filtering/issues/02-hocon-parsing-approach.md`.
- Render-mask evaluation is last-matching-box-wins (scanned bottom-of-config-list first, not a
  symmetric union/subtract), missing axis bounds are unbounded on all 6 axes, map id is the
  sanitized config filename stem, and `config/bluemap/maps/` is fixed on Fabric — see
  `.scratch/map-bounds-filtering/issues/01-render-mask-semantics-and-config-lookup.md`.
- Dispatch restructuring decomposes into two sequential implementation tickets (mask evaluator,
  then per-map gating in `BlueMapAPIConnector`), with masking applied as a connector-side filter
  (no change to `MarkerSetIdentifier`/`ActionFactory`/core), cached per real map id, and
  LINE/SHAPE gated all-or-nothing per map rather than clipped — see
  `.scratch/map-bounds-filtering/issues/03-dispatch-restructuring-decomposition.md`.
- Pre-existing out-of-bounds markers sweep automatically on the next `/bluemap reload` after
  upgrading: the mask gate must actively remove (not just skip-add) a marker found out of bounds,
  reusing `SignManager.reset()`'s existing reload-forced re-dispatch of every sign — no separate
  migration step. One documentation-only caveat: a server restart alone doesn't trigger this (only
  a genuine BlueMap disable/enable does), so the plan/changelog must tell admins to run
  `/bluemap reload` once after upgrading — see
  `.scratch/map-bounds-filtering/issues/04-existing-marker-sweep-on-upgrade.md`.
- `render-mask` entries aren't box-only: BlueMap also supports `circle`, `ellipse`, and `polygon`
  shapes (a `type` discriminator field ticket 01/05 never accounted for) — see
  `.scratch/map-bounds-filtering/issues/07-non-box-render-mask-types.md`. The evaluator implements
  all four shapes properly rather than failing open on non-box entries; ticket 05's scope is
  updated in place — see
  `.scratch/map-bounds-filtering/issues/08-non-box-mask-handling-decision.md`.

## Not yet specified

(none — all tickets resolved or scoped; destination reached, see below)

## Out of scope

(none yet)
