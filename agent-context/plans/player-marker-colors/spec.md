# Player-controlled marker colors

Source: [GitHub issue #198](https://github.com/tpwalke2/BlueMapSignMarkers/issues/198)
Wayfinder map (decision history/rationale): `map.md` (this folder)

## Summary

Lets players set a `LINE`/`SHAPE`/`EXTRUDE` marker's colour by dyeing one of its member signs, instead of an
admin having to edit `lineColor`/`fillColor` in `BMSM-Core.json`. Opt-in per marker group, default off — no
behavior change for an admin who doesn't turn it on.

## Config: `allowPlayerColors`

New `MarkerGroup` field, `boolean allowPlayerColors`:

- `LINE`/`SHAPE`/`EXTRUDE` only; optional; default `false`.
- Setting it on a `POI` group is a warning, not an error — same treatment `lineColor`/`fillColor`/`depthTest`
  already get on the wrong type (`ConfigProvider.warnOnTypeFieldMismatches`, README's "Setting a field on a
  group type it doesn't apply to" note).
- One flag covers both `lineColor` and `fillColor` together — no separate control per channel.
- `LoadingMarkerGroupV2` gets a new nullable `Boolean allowPlayerColors` field; `ConfigProvider` gets a new
  `resolveAllowPlayerColors(LoadingMarkerGroupV2)`, called from `convertToLoadedMarkerGroup`, following the
  exact shape of `resolveDepthTest`: type-restricted, `null` → `false`.
- `MarkerGroup` record (and its `withType` method) gains the new field; `DEFAULT_POI_GROUP` passes `false`.

README: add `allowPlayerColors` to the field list alongside `lineColor`/`fillColor`/`depthTest`, and to the
"setting a field on a group type it doesn't apply to" warning sentence.

## Player input: sign dye

A player dyes a member sign (right-clicking it with a dye item — vanilla mechanic, no new UI). The sign's dye
is read from `SignText.getColor()` (a `DyeColor`, default `BLACK` for an undyed sign) off whichever side
(`getFrontText()`/`getBackText()`) produced the sign's matching representation.

Ink sacs and glow ink sacs only toggle a sign's glowing text (`SignText.hasGlowingText()`); they don't alter
`getColor()`, so they have no effect on the resolved marker colour. Glowing text itself is **not** consumed by
this feature — out of scope (map's Out of
scope section); it's read nowhere in this design.

Dye→hex mapping: `DyeColor.getTextureDiffuseColor()`, a server-safe opaque ARGB `int`, fixed in code — no
per-dye admin override in this cut. Alpha is **not** taken from the dye: the effective colour reuses the alpha
byte already present in the group's configured `lineColor`/`fillColor`, replacing only the RGB portion with
the dye-derived hue.

## Persistence: `SignEntry` dye field (V6)

`SignEntry` gains a new field for the sign's raw dye (front/back, mirroring how `frontRawLines`/`backRawLines`
already split by side) — store the `DyeColor` enum name as a string (e.g. `"RED"`), not a pre-resolved hex.
Reasoning: the RGB mapping stays a pure function (`ColorUtils`/`DyeColor`) that can change without another
migration, and other members' dye (needed to resolve a marker's colour) must come from the persisted cache,
not a live block read — a member may be in an unloaded chunk.

- New `SignFileVersions.V6`.
- New `Version6Converter`: pre-V6 entries backfill to "no dye" (undyed/default), matching an unwaxed vanilla
  sign's actual default state — same backfill pattern `Version4Converter` used for `createdAtMillis`.
- `SignHelper.createSignEntry` reads the dye off `SignBlockEntity.getFrontText()`/`getBackText()` at the same
  point it already reads raw lines.

## Detecting a dye change: mixin

Dyeing an already-placed sign does not call `updateSignText`, isn't a block removal, and doesn't reload the
chunk — none of the mod's existing hooks fire. Research (issue 01) found the actual funnel: dye, ink sac, and
glow ink sac interactions all reach the sign through `SignBlock.useItemOn` → the item's
`SignApplicator.tryApplyToSign` → `SignBlockEntity.updateText(UnaryOperator<SignText>, boolean)` — the same
method `updateSignText` itself calls internally.

- Replace/extend the existing `SignBlockEntityInject` mixin: inject at `RETURN` of
  `SignBlockEntity.updateText`, gated on a `true` return value, instead of (or in addition to)
  `updateSignText`. This one hook now covers every sign-content mutation: text edits, dye, glow, un-glow.
- No Fabric API event covers this cleanly — `UseBlockCallback`/`UseItemCallback` fire pre-outcome and would
  require re-implementing vanilla's own "did this actually change" check, so a mixin is the right call.

## Representation: carrying dye through the diff

`SignTransitionResolver.Representation` gains the sign's own raw dye (`(group, label, detail, dye)`) —
**not** a resolved marker-wide colour. This is deliberate: `computeRepresentation` only ever sees one
`SignEntry` at a time, so `Representation` can only carry that sign's own state.

Why this matters: for `LINE`/`SHAPE`/`EXTRUDE`, `computeTransitionAction` has

```java
if (oldRep.detail().equals(newRep.detail()) && !isReload) return null;
```

Without dye in `Representation`, a dye-only edit (detail unchanged) would silently dispatch nothing even
though the mixin fires. Adding dye to `Representation` means a dye change makes `oldRep != newRep`, defeats
this guard, and falls through to the existing recompute path (`joinEffect`, or the leave-recompute path in
`lineLeaveAction`/`shapeLeaveAction`/`extrudeLeaveAction`) — the same mechanism a detail change already uses.

This also resolves mismatched dyes across a marker's members for free: *any* member's dye edit — whether it
currently "wins" the marker's colour or not — changes that sign's own `Representation` and triggers a
recompute. The recompute always re-derives the colour from the *full current membership*
(`allSignsSupplier.get()`), so it doesn't matter which member changed: a losing member's dye changing, or the
current winning member's sign breaking (routed through the leave-recompute path), both correctly hand off to
whoever's earliest-placed-and-dyed right now.

## Conflict resolution: `ColorResolver`

New plain-Java class, `ColorResolver`, same shape as `LineGroupResolver`/`ShapeGroupResolver`/
`ExtrudeGroupResolver` (directly unit-testable, no Minecraft/Fabric/BlueMap types).

Rule: among a marker's current members, the **earliest-placed** (`createdAtMillis`) member with a
**non-default** dye wins. Re-evaluated fresh on every call against the current membership snapshot — not
cached — so removing or redyeing the current winner hands off to the next-earliest dyed survivor
automatically, with no extra bookkeeping.

- Input: the full members list (same list `toPoints(members)` already receives) plus the `MarkerGroup`.
- If `allowPlayerColors` is off, or no member is dyed: return the group's configured `lineColor`/`fillColor`
  unchanged (today's behavior — zero change for an upgraded server or a group that hasn't opted in).
- Otherwise: take the winning member's `DyeColor`, get its RGB via `DyeColor.getTextureDiffuseColor()`,
  combine with the alpha byte already present in the group's configured `lineColor`/`fillColor` (`ColorUtils`
  already parses hex into `int[]{r,g,b,a}` — reuse that shape).
- Called from `SignTransitionResolver` at the same call sites `toPoints(members)` already runs, for both the
  join path and the leave-recompute path, across all three multi-point types.

`ActionFactory.createSetLineAction`/`createSetShapeAction`/`createSetExtrudeAction` change to accept the
resolved `lineColor`/`fillColor` strings as explicit parameters, instead of reading
`markerGroup.lineColor()`/`fillColor()` internally — keeps `ActionFactory` a dumb builder; `SignTransitionResolver`
(via `ColorResolver`) owns the resolution logic.

## Reload interaction

No special-casing needed. `computeTransitionAction`'s `isReload` flag already forces the recompute path
regardless of the detail-equality guard, and `Representation.group()` is compared by full-record equality — so
a `/bluemap reload` that flips a group's `allowPlayerColors` (or edits any other field) already re-dispatches
every affected sign through the existing reload diff, which re-runs `ColorResolver` under the new config with
no additional wiring.

## Out of scope (unchanged from map)

- POI marker colour/icon control.
- Glowing ink (`hasGlowingText`) as a player-controlled channel.
- Per-dye admin override of the dye→hex table.
- Inline hex override on a sign line, and a `/bmsm color` command — later, additive tickets if ever needed.
- Dyeing a sign black as a deliberate colour choice: `SignText.getColor()` returns `DyeColor.BLACK` both for an
  undyed sign and one a player dyed black (see `SignEntryHelper.UNDYED_DYE`), so `ColorResolver` can't tell the
  two apart and always treats black as "not dyed." A player who dyes a member sign black sees no colour change
  and no error — documented as a known limitation (README `allowPlayerColors` note) rather than fixed, since
  fixing it needs a new persisted "explicitly dyed" flag distinct from the raw dye value.

## Implementation checklist

- [ ] `MarkerGroup` / `LoadingMarkerGroupV2`: add `allowPlayerColors`; `ConfigProvider.resolveAllowPlayerColors`
      + type-mismatch warning; README field docs.
- [ ] `SignFileVersions.V6` + `Version6Converter` (backfill undyed); `SignEntry` gains front/back dye field(s);
      `SignHelper.createSignEntry` reads dye off `SignText.getColor()`.
- [ ] Mixin: extend/replace `SignBlockEntityInject` to inject at `RETURN` of `SignBlockEntity.updateText`,
      gated on `true`, covering text/dye/glow together.
- [ ] `SignTransitionResolver.Representation`: add the sign's raw dye field; update
      `computeRepresentation`/equality-sensitive call sites accordingly.
- [ ] New `ColorResolver` (plain Java, unit-tested): earliest-placed-dyed-member-wins over current membership,
      alpha preserved from group config, no-op when `allowPlayerColors` is off or nothing's dyed.
- [ ] `ActionFactory.createSetLineAction`/`createSetShapeAction`/`createSetExtrudeAction`: accept resolved
      `lineColor`/`fillColor` params instead of reading them off `MarkerGroup` internally.
- [ ] `SignTransitionResolver`: call `ColorResolver` at both the join and leave-recompute call sites for
      `LINE`/`SHAPE`/`EXTRUDE`.
- [ ] Manual verification via `runServer` (dye/glow interaction has no automated coverage per AGENTS.md):
      place a multi-sign line, dye one member, confirm colour updates; break the dyed member, confirm handoff
      to the next-earliest dyed survivor; confirm an undyed marker with `allowPlayerColors` off is unchanged.
