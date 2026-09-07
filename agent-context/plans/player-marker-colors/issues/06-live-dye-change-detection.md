# 06 — Live dye-change detection

**What to build:** Dyeing, glowing, or un-glowing an already-placed sign now drives the same processing
pipeline a text edit already does — `SignManager.addOrUpdate` fires with the sign's fresh dye state. Today,
none of the mod's hooks fire on this (dyeing doesn't call `updateSignText`, isn't a block removal, doesn't
reload the chunk). No marker-visible color change lands in this ticket — it's purely the trigger; see
`agent-context/plans/player-marker-colors/spec.md`'s "Detecting a dye change: mixin" section for the exact hook
point research already found.

**Blocked by:** 05 (needs the persisted dye field on `SignEntry` to populate)

**Status:** ready-for-agent

- [ ] Extend/replace the `SignBlockEntityInject` mixin to inject at `RETURN` of `SignBlockEntity.updateText`,
      gated on a `true` return value — this one hook now covers every sign-content mutation (text edit, dye,
      glow, un-glow), superseding the narrower `updateSignText`-only injection
- [ ] Confirm no double-dispatch: `updateSignText` itself calls `updateText` internally, so the old and new
      injection points must not both fire for a plain text edit
- [ ] Manual verification via `runServer`: dye/glow/un-glow an already-placed sign and confirm (via debug log
      or cache inspection) that `SignManager.addOrUpdate` runs with the sign's updated dye — no color change is
      expected yet, since resolution/wiring lands in later tickets
