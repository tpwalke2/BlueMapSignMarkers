# 08 — Misc small hardening/cleanup

**What to build:** A batch of small, low-risk hardening and cleanup items with no functional interdependency:

1. Consolidate the hardcoded `"unknown"` playerId sentinel (currently duplicated independently in
   `BlueMapSignMarkersMod` and `SignManager`) to one canonical source (e.g. `WorldMap.UNKNOWN`), so the two can't
   drift out of sync.
2. Add null-guards to `SignEntry`'s hand-written `equals`/`hashCode` for `key`/`playerId`/`frontText`/`backText` —
   currently latent (no call site invokes them today, since the sign cache is keyed by `SignEntryKey`), but worth
   closing before something starts relying on them.
3. Pull the duplicated default single-`[poi]`-group literal (currently copy-pasted across `BMSMConfigV2` and
   `LoadingBMSMConfigV2`) into one shared constant.
4. Remove `SignManager.isMarkerType`'s redundant recomputation of `getPrefix(signEntry)`, which is already computed
   a few lines later.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `"unknown"` playerId sentinel has one canonical source used by both call sites
- [ ] `SignEntry.equals`/`hashCode` no longer NPE on null fields
- [ ] Default single-`[poi]`-group literal exists in exactly one place
- [ ] `SignManager.isMarkerType` no longer recomputes `getPrefix` redundantly
- [ ] Full test suite still passes
