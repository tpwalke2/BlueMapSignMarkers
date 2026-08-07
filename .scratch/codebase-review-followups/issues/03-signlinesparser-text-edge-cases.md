# 03 — Fix SignLinesParser text-handling edge cases

**What to build:** Two related sign-text parsing bugs in `SignLinesParser` get fixed, each with a regression test:

1. A REGEX-matched marker-group prefix that shares its line with label text no longer has that label stripped
   blank. Today, `getLabel`'s REGEX branch reapplies the same pattern `matches()` needed for a whole-line match, so
   any pattern loose enough to allow trailing label text greedily strips the entire line via `replaceAll`, leaving
   the label empty with no error — there's currently no configuration that makes "label sharing a line with a REGEX
   prefix" work at all.
2. `trim()` recognizes non-ASCII invisible whitespace (NBSP U+00A0, ideographic space U+3000, zero-width space
   U+200B). Today a sign line consisting solely of one of these characters (or led by one) is treated as
   non-blank, matches no marker group, and permanently transitions the parser to `INVALID` — so a genuine `[poi]`
   line immediately after it is never reached.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] A line containing only (or led/trailed by) NBSP/ideographic-space/zero-width-space is treated as blank, not as a non-matching content line that derails the parser
- [x] A valid `[poi]` line following an invisible-whitespace-only line still matches correctly
- [x] ~~A REGEX group prefix with trailing label text on the same line produces a non-blank label~~ — descoped: AGENTS.md documents whole-line REGEX matching (`line.matches(...)`) as intentional existing behavior, not a bug to fix. Changing it to allow trailing-label-sharing would mean switching to `lookingAt()`-style anchored-start matching, a much larger, backward-incompatible change to REGEX semantics for all configured groups — out of scope for this ticket. Added a regression test locking in the current (documented-limitation) blank-label behavior instead, per the review's own nitpick recommendation.

## Comments

Implemented 2026-08-06: `SignLinesParser.trimLine` now strips NBSP (U+00A0)/zero-width-space (U+200B)/ideographic-space
(U+3000) from line edges alongside standard whitespace, fixing the parser-derailment bug. Added
`nonAsciiInvisibleWhitespaceOnlyLineIsTreatedAsBlank`, `nonAsciiInvisibleWhitespaceSurroundingPrefixLineIsTrimmed`,
and `regexPrefixWithTrailingLabelTextOnTheSameLineResultsInBlankLabel` to `SignLinesParserTest`. Full unit test suite
passes (`./gradlew test`, requires `JAVA_HOME` pointed at a JDK 25 install — the wrapper failed under JDK 21 with
"release version 25 not supported").
