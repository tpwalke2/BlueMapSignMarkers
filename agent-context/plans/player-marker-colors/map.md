Type: wayfinder:map
Source: https://github.com/tpwalke2/BlueMapSignMarkers/issues/198

## Destination — reached

Spec written: [`spec.md`](spec.md) (this folder), implementation-ready — see AGENTS.md's "Planning documents"
convention. All tickets resolved; no frontier remains.

Describes how players set a `LINE`/`SHAPE`/`EXTRUDE` marker's colour via sign dye, without an admin config
edit.

## Notes

Standing decisions locked during charting (grilling session on 2026-09-06), not tickets — future tickets build
from these rather than re-opening them:

- Input channel: sign dye colour (16 vanilla dyes → hex). Inline hex override and a `/bmsm color` command are
  later, additive tickets if ever needed — not this destination.
- Gating: opt-in per marker group (`LINE`/`SHAPE`/`EXTRUDE` only), default off. No behavior change on upgrade.
- Colour is styling, not identity: `(prefix, label)` stays the sole membership key (`LineGroupResolver`/
  `ShapeGroupResolver`/`ExtrudeGroupResolver`). Members disagreeing on dye never splits a marker.
- Conflict rule: earliest-placed member (`createdAtMillis`) with a non-default dye wins; re-evaluated on every
  membership/dye change so a removed/redyed source hands off to the next-earliest dyed survivor. Same
  recompute-on-every-change shape as `SignTransitionResolver` already uses.
- Undyed fallback: a marker with zero dyed members uses the group's configured `lineColor`/`fillColor` as today
  — no wood-type-derived colour.
- Dye→hex source: Mojang's own `DyeColor` RGB table, fixed in code — no per-dye admin override in this cut.
- Alpha: reuse the alpha byte already in the group's configured `lineColor`/`fillColor`; only hue is
  dye-derived. No new alpha config.

Consult `AGENTS.md` (architecture) and `CONTEXT.md` (glossary) before working a ticket. Use `/domain-modeling` if
a ticket's resolution introduces new vocabulary or an ADR-worthy decision (e.g. how colour precedence is computed).

## Decisions so far

- [Dye/glow event hooks research](issues/01-dye-glow-event-hooks-research.md) — recompute hook is a new mixin
  on `SignBlockEntity.updateText` (RETURN, gated on `true`), not `updateSignText`; `SignText.getColor()`/
  `hasGlowingText()` are readable any time; RGB source is `DyeColor.getTextureDiffuseColor()`.
- [Config schema for opt-in flag](issues/02-config-schema-for-opt-in-flag.md) — `allowPlayerColors` (Boolean,
  LINE/SHAPE/EXTRUDE-only, default false), one flag covers both lineColor and fillColor, follows the
  resolveDepthTest pattern.
- [Recompute-trigger design](issues/03-recompute-trigger-design.md) — new mixin on `SignBlockEntity.updateText`;
  each sign's raw dye joins `SignTransitionResolver.Representation` (persisted on `SignEntry` as a new V6
  field); a new `ColorResolver` re-derives the marker's colour from the full current membership at every
  join/leave-recompute call site, handling mismatched member dyes and reload-driven flag flips with no
  special-casing.
- [Write spec document](issues/04-write-spec-document.md) — spec written, consolidating all of the above into
  an implementation-ready plan plus a checklist. Destination reached.

## Not yet specified

(none — destination reached, no fog remains)

## Out of scope

- POI marker colour/icon control — BlueMap's POI API has no colour field; this would be an icon/CSS-selection
  feature wearing the same word, not this destination.
- Glowing ink (`hasGlowingText`) as a second player-controlled channel — issue only asked for colour; doubles
  the conflict-resolution design work for a feature nobody requested.
- Per-dye admin override of the dye→hex table — flagged as a possible future ticket, not this destination.
