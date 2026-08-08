# 09 — Investigate & fix ReactiveQueue message-ordering guarantee

**What to build:** Confirm whether `ReactiveQueue.processMessages` submits each queued message as an independent
task to the same executor, which would imply no ordering guarantee between sequential dispatches submitted close
together. This matters concretely for `SignManager`'s prefix-change path, which dispatches a remove and then an add
as two separate `MarkerAction`s when a sign's matched marker group changes — if ordering isn't guaranteed under
load, the add could run before the remove, leaving the wrong end-state on the map. If the investigation confirms
the risk, fix it (e.g. sequencing same-sign actions, or a stronger ordering guarantee in `ReactiveQueue` itself).

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Document (in code comments or a test) whether `ReactiveQueue` guarantees in-order execution of messages submitted close together
- [x] If no such guarantee exists, fix the prefix-change remove-then-add path so the two actions can't apply out of order under concurrent load
- [x] A regression test reproduces the ordering concern (or confirms it doesn't apply) using the existing test-double pattern in `ReactiveQueueTest`

## Comments

Confirmed the risk: `ReactiveQueue.processMessages` submits each queued message as its own independent
task to the executor (a fixed thread pool sized to `availableProcessors()`), so once more than one
worker thread is available there is no guarantee a message finishes - or even starts - before a message
enqueued after it. Added `reactiveQueueGivesNoOrderingGuaranteeBetweenIndependentlySubmittedMessages` in
`ReactiveQueueTest` to reproduce and document this.

Fixed the concrete risk (`SignManager`'s prefix-change path dispatching a remove then an add as two
separate messages) by bundling the pair into one message instead of relying on `ReactiveQueue`'s
(nonexistent) cross-message ordering:

- New `ChangeGroupMarkerAction` (`core/bluemap/actions`) carries both the old group's `MarkerIdentifier`
  (to remove from) and the new group's (to add to), plus label/detail.
- `ActionFactory.createChangeGroupPOIAction(...)` builds one.
- `BlueMapAPIConnector.processMarkerAction` gained a case for it (`processChangeGroupAction`), which runs
  the remove and the add back-to-back inside the same `synchronized` call that dispatched the message -
  so the pair can never be observed half-applied. `logProcessingMessage` also got a case arm (`AGENTS.md`'s
  documented `MarkerAction`-subtype checklist).
- `SignManager.addOrUpdateSign`'s prefix-change branch now dispatches a single
  `createChangeGroupPOIAction` when both the old and new prefix resolve to a configured group, instead of
  a separate remove dispatch followed by a separate add dispatch. The already-existing "old/new prefix no
  longer configured" warn-and-skip fallbacks are unchanged for the case where only one side resolves to a
  group.

Did not change `ReactiveQueue`'s general submission behavior (independent per-message tasks) - it's a
generic reusable building block per `AGENTS.md`, and other current/future consumers may have no ordering
requirement between their messages; only this specific remove-then-add dependency needed sequencing.

Test coverage: `ReactiveQueueTest` (ordering regression, above), `ActionFactoryTest` (new
`createChangeGroupPOIActionBuildsBothMarkerIdentifiersAndActionFields` test). `BlueMapAPIConnector`/
`SignManager` remain game-coupled/singleton with no automated coverage per `AGENTS.md` - the dispatch
change was verified via a clean compile and full test-suite pass.
