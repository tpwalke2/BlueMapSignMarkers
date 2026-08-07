# 06 — Harden BlueMapAPIConnector internals

**What to build:** Two internal robustness/hygiene fixes in `BlueMapAPIConnector`:

1. `logProcessingMessage` sanitizes control characters beyond `\n` (notably `\r` and ANSI escape sequences) before
   logging raw sign text at INFO — today only `\n` is sanitized, leaving a minor log-injection/log-noise vector open
   for player-controlled sign text.
2. `getMarkerSets`'s marker-map population is rewritten so its correctness doesn't depend on an implicit,
   undocumented invariant (a shared mutable list plus a `putIfAbsent` that's a no-op on every iteration after the
   first) — the current pattern works today but is fragile to a future refactor breaking it silently.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Sign text containing `\r` or ANSI escape sequences is sanitized before being logged at INFO
- [ ] `getMarkerSets`'s population logic no longer relies on the shared-mutable-list/repeated-putIfAbsent invariant, with the same observable behavior
- [ ] Existing tests still pass (this class is game-coupled/no automated coverage per AGENTS.md — verify via clean compile + full suite pass)
