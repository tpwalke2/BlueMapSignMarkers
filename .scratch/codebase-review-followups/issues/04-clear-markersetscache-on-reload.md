# 04 — Clear markerSetsCache on live config reload

**What to build:** Editing a marker group's icon, offsets, or distance settings and reloading config (`/bluemap
reload`, via `SignManager.reloadConfig()`) actually applies the change on the map. Today, `reloadConfig()` rebuilds
the prefix map but never calls `blueMapAPIConnector.resetQueue()`, so `markerSetsCache` — keyed on value-equality of
the *entire* `MarkerGroup` record — is never cleared on a live reload. Every config field change produces a new,
never-evicted cache entry rather than updating markers already on the map, and old entries accumulate indefinitely.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Changing a marker group's icon/offset/distance and reloading config updates existing markers on the map instead of leaving them on stale cached settings
- [x] `markerSetsCache` doesn't grow unboundedly across repeated reloads of the same groups
- [x] A live reload with no config changes still works as before (no regression to the happy path)

## Comments

Added `BlueMapAPIConnector.clearMarkerSetsCache()` — a narrow method that just replaces `markerSetsCache` with a
fresh `ConcurrentHashMap`, called from `SignManager.reloadConfig()`. Did not call the existing `resetQueue()` as
the ticket suggested: that method also replaces `markerActionQueue`, abandoning its executor (never shut down —
thread leak) and dropping any messages still queued on it. `resetQueue()` stays reserved for the
shutdown-queue-recovery path in `onEnable()`. Verified with `JAVA_HOME` pointed at the JDK 25 toolchain — Gradle's
launcher JVM defaults to JDK 21 in this environment and can't compile with `--release 25` on its own; full
`./gradlew build` (unit tests + jar) passes.
