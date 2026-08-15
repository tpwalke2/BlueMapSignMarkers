# Adversarial Code Review: feature/tpwalke2/7-line-markers

**Scope:** branch `feature/tpwalke2/7-line-markers` vs `main` (merge-base `14a2f818701bff3862303d99e528c4f2d18e9684`)
**Date:** 2026-08-14
**Files changed:** 67

## Findings

### 1. [High] LINE-group signs redispatch to BlueMap on every no-op edit, incl. every chunk load
- **Category:** Performance
- **Location:** `src/main/java/com/tpwalke2/bluemapsignmarkers/core/signs/SignManager.java:164-165` (branch), `189-199` (`lineJoinAction`)
- **Issue:** The POI/POI branch (`SignManager.java:156-161`) skips dispatch when `oldRep.detail().equals(newRep.detail())` — a genuine no-op edit sends nothing. The LINE/LINE-same-group-and-label branch has no such check: `lineJoinAction` always builds and returns a `SetLineMarkerAction` once 2+ members exist, regardless of whether anything changed.
- **Impact:** `BLOCK_ENTITY_LOAD` fires `addOrUpdate` for every loaded `SignBlockEntity` on every chunk load (per AGENTS.md), with old and new representation identical for an untouched sign. For a LINE-group sign this now unconditionally rebuilds and redispatches its entire line marker (recomputing `joinLineDetail` over every member, building a `Line`, calling BlueMap's `LineMarker.builder()`) on every chunk load/unload cycle near that sign — continuous unnecessary BlueMap API churn and log spam ("Updating LINE type marker...") on any server actually using LINE groups near player-frequented areas.
- **Fix:** Short-circuit like the POI branch: compare the recomputed `joinLineDetail`/member set against what's already dispatched (or track last-dispatched state), and return `null` when nothing changed.

### 2. [High] `reloadConfig()` replay is effectively O(N²) for LINE-heavy configs, and refires finding #1 for every LINE sign on every reload
- **Category:** Performance
- **Location:** `SignManager.java:301-320` (`reloadConfig`), `102-104` (`getAllSigns`), `189-211` (`lineJoinAction`/`lineLeaveAction`)
- **Issue:** `reloadConfig()`'s outer loop already copies the whole sign cache once via `getAllSigns()` (line 311). For every LINE-type entry in that loop, `lineJoinAction`/`lineLeaveAction` each call `getAllSigns()` again (a fresh `ArrayList` copy of the whole cache) and run `LineGroupResolver.members()` (a full filter + sort over that copy). With N signs and a config that's LINE-heavy, that's an O(N²) scan on every `/bluemap reload` — and per finding #1, every LINE sign redispatches unconditionally even when the config for that group didn't change at all.
- **Impact:** On a large, LINE-heavy server, a single `/bluemap reload` (fired under `reloadConfig`'s own lock, blocking concurrent sign edits) does a quadratic scan plus a full unconditional re-render of every configured line — a real stall risk as sign counts grow, not just a cosmetic inefficiency.
- **Fix:** Cache `getAllSigns()` once per reload/edit and pass it down instead of re-fetching per LINE resolution; consider indexing line membership by (map, prefix, label) instead of a linear scan.

### 3. [Medium] POI label edits on an unchanged group now route through remove+add instead of a direct update
- **Category:** Correctness / Maintainability
- **Location:** `SignManager.java:156-161` (`sameGroupAndLabel` check), `125-127` (`sameGroupAndLabel`)
- **Issue:** `sameGroupAndLabel` requires both same prefix *and* same label. Previously (`existingPrefix.equals(newPrefix)` alone), any edit to a sign's label line while staying in the same group dispatched a single `createUpdatePOIAction`. Now that same edit — label changed, prefix unchanged, by far the most common sign-edit case — fails `sameGroupAndLabel` and falls through to `createChangeGroupPOIAction`, which does a remove-then-add `GroupTransitionMarkerAction` with `oldMarkerGroup == newMarkerGroup`.
- **Impact:** End state is equivalent (POI marker id is position-based, so remove+add lands on the same id), but every ordinary label edit now costs 2 BlueMap ops instead of 1, logs "Removing"/"Adding" instead of "Updating", and the marker is momentarily absent from the `MarkerSet` map inside that synchronized call. Silent, undocumented behavior change with no test catching it.
- **Fix:** Check group/prefix equality alone to pick update vs. change-group; use label+detail equality (as the old `isTextDifferent` did) only to decide whether to dispatch at all.

### 4. [Medium] Core transition table (`computeTransitionAction`) has zero automated test coverage
- **Category:** Maintainability
- **Location:** `SignManager.java:110-186` (`computeTransitionAction` and helpers)
- **Issue:** This branch adds a ~10-case transition table (POI↔POI, LINE↔LINE, POI↔LINE both directions, null↔either) that AGENTS.md calls "the decision point" for the whole mod. Every other new unit in this branch (`LineGroupResolver`, `ActionFactory`, `ColorUtils`, `ConfigProvider`) got a matching test file; `SignManager` did not, despite having no Minecraft/Fabric/BlueMap types in its own signature (only a `BlueMapAPIConnector` field, called through `.dispatch(MarkerAction)`).
- **Impact:** Finding #3 above is exactly the kind of regression a table-driven test over `computeTransitionAction` would catch before merge. Left uncovered, the next change to this table (and there's a lot of branches to keep straight) has no automated safety net.
- **Fix:** Add a `SignManagerTest` (or extract `computeTransitionAction` to a plain-Java class) with one case per transition-table row from `.scratch/line-markers/spec.md` §6.

### 5. [Low] `lineWidth`/`lineColor` accepted from config with no range/format validation before reaching BlueMap's API
- **Category:** Correctness
- **Location:** `src/main/java/com/tpwalke2/bluemapsignmarkers/config/ConfigProvider.java:160-181` (`warnOnTypeFieldMismatches`), `src/main/java/com/tpwalke2/bluemapsignmarkers/core/bluemap/BlueMapAPIConnector.java` (`setLineMarker`)
- **Issue:** `ConfigProvider` warns when `lineWidth`/`lineColor` are set on a POI group, but never validates the values themselves for a LINE group (e.g. a negative or zero `lineWidth`). `ColorUtils.parseHex` already falls back safely on a malformed color, but `lineWidth` goes straight to `LineMarker.builder().lineWidth(...)`.
- **Impact:** Low — `ReactiveQueue`'s `onError` callback catches exceptions thrown while processing a dispatched action, so a bad value would log an error rather than crash the server. Still worth a bounds check at load time for a clearer error message.
- **Fix:** Clamp or reject non-positive `lineWidth` in `ConfigProvider`'s conversion, same treatment as the existing hex-parsing fallback.

## Summary
- Critical: 0 · High: 2 · Medium: 2 · Low: 1
- Clean: Security (no new trust-boundary or injection surface — `HtmlUtils.toHtmlDetail` is still applied to line marker `detail`; `ColorUtils.parseHex` fails safe on malformed input)
