# 01 — Config load/save correctness

**What to build:** `ConfigProvider` reliably tells V1 configs from V2 configs by structure, not by a raw substring
search for `"poiPrefix"` in the file text (a V2 config whose group name happens to contain that text is currently
misclassified and gets its real marker groups silently overwritten with a single default group). Loading a config
also fails fast — rejecting or clearly flagging the offending group — when a marker group has an empty prefix, a
REGEX prefix that doesn't compile, or a prefix duplicated across groups, rather than only surfacing as a skip/NPE
much later downstream. Saving and loading the config file consistently use UTF-8 on both sides, so non-ASCII
marker-group names survive a restart regardless of the JVM's default platform charset.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] V2 configs are identified structurally (e.g. parse-and-check-shape), not by substring search on the raw file text
- [x] A config load with an empty group prefix, a non-compiling REGEX prefix, or a prefix duplicated across groups fails fast (rejected/flagged) instead of silently corrupting or deferring to a later NPE/skip
- [x] Config save and load both use UTF-8 explicitly
- [x] Existing valid configs (V1 and V2) still load unchanged

## Comments

Resolved in `ConfigProvider`:
- V1/V2 detection now checks JSON shape (`has("poiPrefix") && !has("markerGroups")`) instead of a substring search on the raw file text.
- `validateMarkerGroups` rejects (returns `null`, same as other load failures) a config with an empty prefix, a non-compiling REGEX prefix, or a prefix duplicated across groups.
- `saveConfig` now writes via `OutputStreamWriter(..., StandardCharsets.UTF_8)` instead of a platform-default-charset `FileWriter`; load already used UTF-8.
- Tests added/updated in `ConfigProviderTest`: structural V1/V2 detection (existing substring-bug test updated to assert the fix), empty prefix, bad regex, duplicate prefix, and a UTF-8 save/load round trip for non-ASCII group names. Full `./gradlew build` passes.
