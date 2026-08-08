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

**Status:** resolved

- [x] `"unknown"` playerId sentinel has one canonical source used by both call sites
- [x] `SignEntry.equals`/`hashCode` no longer NPE on null fields
- [x] Default single-`[poi]`-group literal exists in exactly one place
- [x] `SignManager.isMarkerType` no longer recomputes `getPrefix` redundantly
- [x] Full test suite still passes

## Comments

1. `BlueMapSignMarkersMod` and `SignManager` now both reference `WorldMap.UNKNOWN` instead of the `"unknown"`
   string literal.
2. `SignEntry.equals`/`hashCode` now use `Objects.equals`/`Objects.hash` instead of unguarded field-level
   `.equals()`/`.hashCode()` calls. Updated `SignEntryTest` (the old test documented the NPE as a known-latent
   risk; it now asserts null fields are tolerated instead).
3. Added `MarkerGroup.DEFAULT_POI_GROUP` as the single source of the default `[poi]` group's field values.
   `BMSMConfigV2` uses it directly; `LoadingBMSMConfigV2` derives its `LoadingMarkerGroupV2` default from it
   (the two are different record types, so the instance itself can't be shared, but the literal values now
   live in exactly one place).
4. `SignEntryHelper.isMarkerType` now takes the already-resolved prefix (`String`) instead of a `SignEntry`,
   so `SignManager.addOrUpdateSign` computes `newPrefix` once and passes it in, rather than computing it twice
   (once inside `isMarkerType`, once directly after). Updated `SignEntryHelperTest` for the new signature.
