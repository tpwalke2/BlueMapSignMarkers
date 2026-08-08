# 06 — Harden BlueMapAPIConnector internals

**What to build:** Two internal robustness/hygiene fixes in `BlueMapAPIConnector`:

1. `logProcessingMessage` sanitizes control characters beyond `\n` (notably `\r` and ANSI escape sequences) before
   logging raw sign text at INFO — today only `\n` is sanitized, leaving a minor log-injection/log-noise vector open
   for player-controlled sign text.
2. `getMarkerSets`'s marker-map population is rewritten so its correctness doesn't depend on an implicit,
   undocumented invariant (a shared mutable list plus a `putIfAbsent` that's a no-op on every iteration after the
   first) — the current pattern works today but is fragile to a future refactor breaking it silently.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Sign text containing `\r` or ANSI escape sequences is sanitized before being logged at INFO
- [x] `getMarkerSets`'s population logic no longer relies on the shared-mutable-list/repeated-putIfAbsent invariant, with the same observable behavior
- [x] Existing tests still pass (this class is game-coupled/no automated coverage per AGENTS.md — verify via clean compile + full suite pass)

## Comments

1. Added `LogUtils.sanitizeForLog` (testable, plain-Java, `common` package) that strips ANSI CSI escape
   sequences and escapes `\r`/`\n`/`\r\n` as literal `\r`/`\n`. The ESC byte is built from its char code
   (`(char) 27`) rather than embedded as a literal control character in source, so the source file itself
   stays free of raw control bytes. `BlueMapAPIConnector.logProcessingMessage` now calls this instead of
   the old inline `.replace("\n", "\\n")`.
2. `getMarkerSets` now builds the full `markerSetsToReturn` list first, then does one
   `markerSetsCache.putIfAbsent` after the `forEach` loop, instead of calling `putIfAbsent` with the same
   (still-growing) list reference on every iteration. Same observable behavior, no longer depends on an
   implicit "later puts are no-ops" invariant.
3. Added `LogUtilsTest` (newline, carriage-return, CRLF, ANSI-stripping, and bracketed-text-passthrough
   cases). `BlueMapAPIConnector` itself stays without direct tests per AGENTS.md (game-coupled); verified
   with `JAVA_HOME` pointed at the JDK 25 toolchain via `./gradlew test` and `./gradlew build`, both green.
4. Review fixes: the INFO log line said "label" while logging the marker *detail* (`getDetail()`/
   `getNewDetails()`) - reworded to "detail" so it matches what's actually logged. `LogUtils`'s CSI regex
   only matched `[0-9;]*` before a letter, missing private-mode sequences (e.g. `ESC[?25l`, parameter byte
   `?`) - widened to the full ECMA-48 CSI grammar (parameter bytes `0-9:;<=>?`, intermediate bytes space
   through `/`, final byte `@` through `~`). Added `ansiPrivateModeCsiSequencesAreStripped` to
   `LogUtilsTest` covering the gap. Re-verified with `./gradlew test`.
