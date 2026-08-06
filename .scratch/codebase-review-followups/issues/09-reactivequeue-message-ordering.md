# 09 — Investigate & fix ReactiveQueue message-ordering guarantee

**What to build:** Confirm whether `ReactiveQueue.processMessages` submits each queued message as an independent
task to the same executor, which would imply no ordering guarantee between sequential dispatches submitted close
together. This matters concretely for `SignManager`'s prefix-change path, which dispatches a remove and then an add
as two separate `MarkerAction`s when a sign's matched marker group changes — if ordering isn't guaranteed under
load, the add could run before the remove, leaving the wrong end-state on the map. If the investigation confirms
the risk, fix it (e.g. sequencing same-sign actions, or a stronger ordering guarantee in `ReactiveQueue` itself).

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Document (in code comments or a test) whether `ReactiveQueue` guarantees in-order execution of messages submitted close together
- [ ] If no such guarantee exists, fix the prefix-change remove-then-add path so the two actions can't apply out of order under concurrent load
- [ ] A regression test reproduces the ordering concern (or confirms it doesn't apply) using the existing test-double pattern in `ReactiveQueueTest`
