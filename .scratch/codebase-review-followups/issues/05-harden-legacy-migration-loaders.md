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

**Status:** resolved

- [x] A legacy dimension string outside the three known lowercase literals normalizes correctly instead of silently mismatching post-migration
- [x] A version-file JSON missing `version`/`data` is handled by explicit fallback logic, not incidental null-handling
- [x] One malformed/failing entry during `Version3Converter`/`Version1SignEntryLoader` processing doesn't drop the rest of the file's entries
- [x] Existing migration tests still pass; new tests cover each of the three cases above

## Comments

1. `Version1SignEntryLoader.getNormalizedMapId` now strips an optional `minecraft:` namespace before matching,
   and recognizes `the_nether`/`the_end` as well as the original `nether`/`end`/`overworld` shorthand — so
   `the_nether`, `minecraft:nether`, `minecraft:the_nether`, etc. all normalize to the same canonical
   `minecraft:the_nether` the live dimension key uses. Anything still unrecognized falls through lowercased only,
   same as before.
2. `VersionedFileSignEntryLoader.loadSignEntries` now checks `versionedSignFile == null || version() == null ||
   data() == null` explicitly and logs+falls back, instead of relying on `gson.fromJson(null, ...)` happening to
   return `null`.
3. Both `Version1SignEntryLoader` and `VersionedFileSignEntryLoader`'s V2 branch now convert entries one at a time
   inside a try/catch (`loadEntry`/`convertEntrySafely`), logging and skipping a bad entry instead of letting one
   `NullPointerException` (or similar) from `Version3Converter.convertToV3` abort the whole file — mirroring
   `SignProvider.loadSigns`'s existing per-entry try/catch.
4. Added tests for all three: `Version1SignEntryLoaderTest` (unnamespaced canonical paths, namespaced shorthand,
   skip-a-malformed-entry) and `VersionedFileSignEntryLoaderTest` (missing version/data, skip-a-malformed-entry).
   Verified with `JAVA_HOME` pointed at the JDK 25 toolchain; full `./gradlew build` (unit tests + jar) passes.
