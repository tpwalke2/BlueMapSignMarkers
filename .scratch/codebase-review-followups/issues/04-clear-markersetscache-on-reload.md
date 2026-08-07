# 04 — Clear markerSetsCache on live config reload

**What to build:** Editing a marker group's icon, offsets, or distance settings and reloading config (`/bluemap
reload`, via `SignManager.reloadConfig()`) actually applies the change on the map. Today, `reloadConfig()` rebuilds
the prefix map but never calls `blueMapAPIConnector.resetQueue()`, so `markerSetsCache` — keyed on value-equality of
the *entire* `MarkerGroup` record — is never cleared on a live reload. Every config field change produces a new,
never-evicted cache entry rather than updating markers already on the map, and old entries accumulate indefinitely.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Changing a marker group's icon/offset/distance and reloading config updates existing markers on the map instead of leaving them on stale cached settings
- [ ] `markerSetsCache` doesn't grow unboundedly across repeated reloads of the same groups
- [ ] A live reload with no config changes still works as before (no regression to the happy path)
