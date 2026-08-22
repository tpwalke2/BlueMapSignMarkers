# Research: BlueMap render-mask types beyond box

Type: research
Status: resolved

## Question

Tickets 01/02/05 and the resulting plan (`../../map-bounds-filtering-plan.md`) only
ever researched and implemented the **box** shape of `render-mask` entries (`min-x`/`max-x`/
`min-y`/`max-y`/`min-z`/`max-z`). Does BlueMap's `render-mask` support other shape types, and if
so what are they, per the official docs
(https://bluemap.bluecolored.de/wiki/customization/Masks.html#available-mask-types)?

## Answer

Yes — box is one of four mask types, selected by a `type:` discriminator field the current
hand-rolled parser (`RenderMaskEvaluator.parseBox`/`FIELD_PATTERN`) never reads:

- **`type: box`** — `min-x`/`max-x`/`min-z`/`max-z`/`min-y`/`max-y` (all optional). This is the
  only shape ticket 05 implemented.
- **`type: circle`** — `center-x`, `center-z`, `radius`, plus optional `min-y`/`max-y`. Circular
  area in the XZ plane, optionally height-limited.
- **`type: ellipse`** — `center-x`, `center-z`, `radius-x`, `radius-z`, plus optional
  `min-y`/`max-y`. Same as circle but independent X/Z radii.
- **`type: polygon`** — `shape` (an array of `{x, z}` coordinate pairs), plus optional
  `min-y`/`max-y`. Arbitrary polygon, 3+ points.

All four support the same `subtract` modifier (default `false`), and per ticket 01's confirmed
last-matching-entry-wins algorithm, entries of different types can appear mixed in the same
`render-mask` list and are evaluated in the same list-order-matters way regardless of shape.

**Current-code impact:** `RenderMaskEvaluator.FIELD_PATTERN`/`parseBox` has no `type` handling —
it just scans every `{...}` chunk for known box-field names via regex. A `circle`/`ellipse`/
`polygon` entry has none of `min-x`/`max-x`/`min-z`/`max-z` (it uses `center-x`/`radius`/`shape`
etc. instead), so today it silently parses into a `RenderMaskBox` unbounded on X and Z (bounded
only by whatever `min-y`/`max-y` it happens to also carry) — i.e. it's mistaken for "everything
in this Y range," not the actual circular/elliptical/polygonal area, which is a correctness bug
for any server using a non-box mask entry anywhere in a map's `render-mask` list.

### Source

BlueMap wiki, "Available Mask Types" section:
https://bluemap.bluecolored.de/wiki/customization/Masks.html#available-mask-types
