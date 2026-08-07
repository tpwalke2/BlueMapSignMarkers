# 05 — Harden legacy sign-migration loaders

**What to build:** Three related robustness gaps in the legacy sign-data migration path get closed:

1. `Version1SignEntryLoader`'s dimension normalization recognizes legacy dimension strings beyond the three exact
   lowercase literals (`"nether"`/`"end"`/`"overworld"`) it currently matches — anything else falls through
   unchanged today, permanently mismatching the live dimension key post-migration and duplicating markers as "new"
   signs.
2. A structurally-valid-but-incomplete JSON file (missing `version`/`data`) is handled as an explicit, intentional
   fallback case in `VersionedFileSignEntryLoader`, rather than working only because Gson happens to return nulls
   that coincidentally funnel into the V1-fallback path.
3. `Version3Converter` and `Version1SignEntryLoader` isolate per-entry failures during conversion/loading instead of
   losing the whole file on one bad entry — the same all-or-nothing pattern `SignProvider`'s load loop already had
   fixed for it elsewhere.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] A legacy dimension string outside the three known lowercase literals normalizes correctly instead of silently mismatching post-migration
- [ ] A version-file JSON missing `version`/`data` is handled by explicit fallback logic, not incidental null-handling
- [ ] One malformed/failing entry during `Version3Converter`/`Version1SignEntryLoader` processing doesn't drop the rest of the file's entries
- [ ] Existing migration tests still pass; new tests cover each of the three cases above
