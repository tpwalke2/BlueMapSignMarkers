# 05 — Config flag + persisted sign dye

**What to build:** Admins can set `allowPlayerColors` on a `LINE`/`SHAPE`/`EXTRUDE` marker group in
`BMSM-Core.json` (setting it on a `POI` group logs a warning, same treatment `lineColor`/`fillColor`/`depthTest`
already get). A sign's dye is read (per front/back side) and persisted alongside its other data, surviving a
server restart — new `SignFileVersions.V6` + `Version6Converter`, backfilling every pre-V6 entry as undyed
(matching an unwaxed vanilla sign's actual default state). No marker-visible behavior changes in this ticket —
see `agent-context/plans/player-marker-colors/spec.md` for the full design (Config, Persistence sections).

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [ ] `MarkerGroup` (and `LoadingMarkerGroupV2`) gain `allowPlayerColors`; `ConfigProvider` resolves it following
      `resolveDepthTest`'s exact pattern (LINE/SHAPE/EXTRUDE-only, default `false`); `DEFAULT_POI_GROUP` passes
      `false`
- [ ] Setting `allowPlayerColors` on a `POI` group logs a warning via `warnOnTypeFieldMismatches`, doesn't error
- [ ] README documents `allowPlayerColors` alongside the other LINE/SHAPE/EXTRUDE-only fields, including the
      "ignored with a warning on the wrong type" note
- [ ] `SignEntry` gains a persisted dye field per side; `SignHelper.createSignEntry` reads it off
      `SignBlockEntity.getFrontText()`/`getBackText()`'s `DyeColor` at the same point it reads raw lines
- [ ] New `SignFileVersions.V6` + `Version6Converter`; pre-V6 entries backfill to undyed
- [ ] Unit tests: config resolution (flag set/unset, wrong-type warning), V6 migration round-trip
- [ ] Manual: place/dye a sign, restart the dev server, confirm the dye survives and no existing marker
      regresses
