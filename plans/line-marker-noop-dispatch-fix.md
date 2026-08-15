# Plan: fix findings #1 and #2 from adversarial review (2026-08-14)

Source: `agent-context/plans/adversarial-review-feature-tpwalke2-7-line-markers-2026-08-14.md`

## Finding #1 — LINE/LINE no-op edits always redispatch

`lineJoinAction` (`SignManager.java:189-195`) has no equivalent to the POI branch's
`oldRep.detail().equals(newRep.detail())` no-op check. Every `BLOCK_ENTITY_LOAD` chunk-load event for
an untouched LINE sign rebuilds `joinLineDetail` + points over every member and redispatches
`SetLineMarkerAction`, even when nothing changed.

### Fix

In `computeTransitionAction`'s LINE/LINE-same-group-and-label branch (`SignManager.java:164-166`), skip
the call to `lineJoinAction` when the edited sign's own detail didn't change:

```java
if (oldType == MarkerGroupType.LINE && newType == MarkerGroupType.LINE && sameGroupAndLabel(oldRep, newRep)) {
    return oldRep.detail().equals(newRep.detail())
            ? null
            : lineJoinAction(key.parentMap(), newRep, actionFactory, true);
}
```

This mirrors the POI branch exactly. `sameGroupAndLabel` already requires same prefix + label, so
`oldRep.detail()`/`newRep.detail()` is the only thing that can differ for the edited sign itself.

Note: this only skips redispatch when the *edited* sign's own detail is unchanged. It does not
detect the case where some other member's detail changed independently — that's already reached
through a separate `addOrUpdateSign` call for that other sign's own key, which runs its own
detail-equality check. No change needed there.

### Test

Add a case to `SignManagerTest` (or wherever transition-table cases will live per finding #4):
same LINE group/label/detail old vs. new → `computeTransitionAction` returns `null`.

## Finding #2 — O(N²) `reloadConfig` replay for LINE-heavy configs

`lineJoinAction`/`lineLeaveAction` each call `getAllSigns()` (fresh `ArrayList` copy of the whole
cache) independently. `reloadConfig` already holds its own `getAllSigns()` copy in its outer loop
(`SignManager.java:311`), so every LINE entry in that loop triggers a second full cache copy +
`LineGroupResolver.members()` scan — O(N²) total for N signs.

Same duplication happens on every live edit too (`addOrUpdateSign`/`removeByKey`), just cheaper
there since it's one entry, not a full-cache loop.

### Fix

Thread the already-fetched sign list down instead of re-fetching inside `lineJoinAction`/`lineLeaveAction`:

1. Change `lineJoinAction`/`lineLeaveAction` signatures to accept `List<SignEntry> allSigns` instead of
   calling `getAllSigns()` internally:

```java
private MarkerAction lineJoinAction(List<SignEntry> allSigns, String parentMap, Representation rep, ActionFactory actionFactory, boolean sameGroupRecompute) {
    var members = LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(), rep.label());
    ...
}

private MarkerAction lineLeaveAction(List<SignEntry> allSigns, String parentMap, Representation rep, ActionFactory actionFactory) {
    var members = LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(), rep.label());
    ...
}
```

2. `computeTransitionAction` takes the same `allSigns` list as a parameter and passes it through to
   every `lineJoinAction`/`lineLeaveAction` call site (4 call sites: null→LINE, LINE→null, LINE↔LINE
   same-group, and the two branches inside the POI↔LINE `effects` block).

3. Each of the three callers of `computeTransitionAction` fetches `getAllSigns()` once and passes it in:
   - `addOrUpdateSign` (`SignManager.java:223-260`): one entry changed, but the LINE resolver still
     needs the full cache — fetch once, pass to `computeTransitionAction`.
   - `removeByKey` (`SignManager.java:262-279`): same.
   - `reloadConfig` (`SignManager.java:301-320`): already has `getAllSigns()` in the outer loop
     (line 311) — reuse that same list for every `computeTransitionAction` call in the loop instead of
     letting `lineJoinAction`/`lineLeaveAction` re-fetch it. This turns the reload's LINE-heavy path
     from O(N²) into O(N) copies + O(N × members-scan) — the remaining per-entry
     `LineGroupResolver.members()` linear scan is inherent to the current data structure and out of
     scope for this fix (the review's "consider indexing line membership" suggestion is a larger
     change, not required to close this finding).

### Test

Add a `SignManagerTest` case (or extend an existing one) asserting `getAllSigns()`/cache access count
doesn't scale with the number of LINE entries during `reloadConfig` — e.g. wrap `signCache` access
behind a countable seam, or simply assert correctness of output while reviewing the diff shows only
one `getAllSigns()` call per `reloadConfig`/`addOrUpdateSign`/`removeByKey` invocation. If a countable
seam is impractical, this can be verified by code review of the call graph rather than a runtime
assertion — note this explicitly in the PR description.

## Order of work

1. Fix #1 first (small, isolated, one-line change to the LINE/LINE branch).
2. Fix #2 second (signature change touches every call site of `lineJoinAction`/`lineLeaveAction`/
   `computeTransitionAction` — do this after #1 so the diff for #1 stays reviewable on its own).
3. Run `./gradlew test` after each fix.
