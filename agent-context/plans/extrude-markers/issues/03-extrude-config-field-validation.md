# 03 — EXTRUDE config field-mismatch warnings

**What to build:** `icon`/`offsetX`/`offsetY` set on an `EXTRUDE` group logs a warning and is ignored, matching the
existing `SHAPE` mismatch-warning pattern (`ConfigProvider`).

See `../spec.md` for full context.

**Blocked by:** 01-core-extrude-marker-happy-path.md

**Status:** ready-for-agent

- [ ] `icon`, `offsetX`, `offsetY` set on an `EXTRUDE` group produce a warning log and are ignored (not applied),
      mirroring the existing `SHAPE` behavior for the same fields.
- [ ] `ConfigProviderTest` covers all three new mismatch warnings for `EXTRUDE`.
- [ ] If `../../marker-polish` has landed by the time this is implemented, extend its `depthTest` field to
      `EXTRUDE` (`ExtrudeMarker.Builder.depthTestEnabled` exists identically to `LineMarker`/`ShapeMarker`); if not
      landed yet, skip this item — `marker-polish` ticket 01/02 picks it up instead.
