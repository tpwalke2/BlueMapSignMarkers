# 01 — Config load/save correctness

**What to build:** `ConfigProvider` reliably tells V1 configs from V2 configs by structure, not by a raw substring
search for `"poiPrefix"` in the file text (a V2 config whose group name happens to contain that text is currently
misclassified and gets its real marker groups silently overwritten with a single default group). Loading a config
also fails fast — rejecting or clearly flagging the offending group — when a marker group has an empty prefix, a
REGEX prefix that doesn't compile, or a prefix duplicated across groups, rather than only surfacing as a skip/NPE
much later downstream. Saving and loading the config file consistently use UTF-8 on both sides, so non-ASCII
marker-group names survive a restart regardless of the JVM's default platform charset.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] V2 configs are identified structurally (e.g. parse-and-check-shape), not by substring search on the raw file text
- [ ] A config load with an empty group prefix, a non-compiling REGEX prefix, or a prefix duplicated across groups fails fast (rejected/flagged) instead of silently corrupting or deferring to a later NPE/skip
- [ ] Config save and load both use UTF-8 explicitly
- [ ] Existing valid configs (V1 and V2) still load unchanged
