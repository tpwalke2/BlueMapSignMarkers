# 04 — Document EXTRUDE in README

**What to build:** Add `EXTRUDE` as a valid `type` in `../../../../README.md`'s Marker Groups config reference, noting it
shares `SHAPE`'s `lineWidth`/`lineColor`/`fillColor` fields and its floor/ceiling (lowest/highest member Y)
behavior, plus an example `EXTRUDE` group block.

See `../spec.md` for full context.

**Blocked by:** 01-core-extrude-marker-happy-path.md, 02-extrude-type-flip-transitions.md,
03-extrude-config-field-validation.md

**Status:** ready-for-agent

- [ ] `../../../../README.md`'s `type` field list documents `EXTRUDE`, its 3+ member threshold, and its floor/ceiling behavior.
- [ ] `../../../../README.md`'s example config gains an `EXTRUDE` group block alongside the existing `POI`/`LINE`/`SHAPE`
      examples, with matching example prose in the paragraph below the config block.
- [ ] `../../../../CONTEXT.md`'s glossary gains an `EXTRUDE`/volume entry alongside `Shape`, and `../../../../AGENTS.md`'s architecture
      notes mention `EXTRUDE` wherever `SHAPE` is currently described as one of the group types.
