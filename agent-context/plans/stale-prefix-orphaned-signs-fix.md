# Fix: stale-prefix orphaned signs (Bug A)

Source: `../handoffs/2026-08-13-stale-prefix-orphaned-signs.md`. Settled via `/grilling` session
2026-08-14 (transcript not preserved separately; decisions below are final).

## Problem

`SignLinesParseResult.prefix` stores the literal matched config-group prefix text at parse time (for a
`REGEX` group, the regex pattern text itself). `SignEntry` persists this. `SignManager.computeRepresentation`
looks it up against the **current** config's `prefixGroupMap`. Editing a `REGEX` group's pattern text orphans
every sign parsed under the old pattern text — lookup fails against old and new config alike, the sign is
silently dropped ("No marker group configured for prefix ..., skipping"), and it never recovers because:

- `SignManager.reloadConfig()` (fires on `/bluemap reload`) only diffs the already-cached `SignEntry`, never
  re-reads the sign's actual block text.
- `SignHelper.createSignEntry` **does** reparse from live block text with the current parser, but only runs on
  `BLOCK_ENTITY_LOAD` (chunk load) or a live edit — a chunk that stays loaded continuously never gets this.

Confirmed live in the dev world: two `[line]` signs on the `shopping` map, stuck since the group's regex was
edited twice.

## Decision

Persist raw sign line text and let `reloadConfig()` re-derive representations from source, instead of trusting
a previously-parsed value as an identity key. Rejected alternative: a stable per-group `id` in config — it only
prevents *future* drift, can't repair already-orphaned signs (no way to know which current group a stale entry
should map to without re-reading the sign), and doesn't remove the underlying fragility (a derived value used
as identity), just makes it change less often.

**Scope**: fixes the bug going forward only. Signs orphaned before this ships have no raw text to backfill from
(reconstructing it from already-lossy `prefix`/`label`/`detail` risks a *wrong* reparse, worse than today's
honest drop) — they keep today's behavior until re-edited or the server restarts. This is a documented,
accepted limitation, not a gap to close later.

## Design

### `SignEntry`

Add `frontRawLines: String[]`, `backRawLines: String[]` — mirrors whatever `SignText.getMessages(false)`
returns today (4 lines/side currently; no hardcoded length assumption baked into the new fields themselves).
`null` on either side means "raw text not available" (pre-migration entry) — sentinel, not empty array. Update
`equals`/`hashCode`/`toString`/`withKey`.

### `SignHelper.createSignEntry`

Capture the raw `String[]` for front/back (from `signText.getMessages(false)`) alongside the existing parse,
and pass both into the new `SignEntry` fields. No behavior change to the parse itself.

### Persistence: `SignFileVersions.V5`

Follows the `V3→V4` pattern already established for `createdAtMillis`:

- Add `V5` to `SignFileVersions`.
- Freeze current shape as `SignEntryV4` (`models/SignEntryV4.java`) — same fields `SignEntry` has today, no raw
  lines.
- `Version5Converter.convertToV5(SignEntryV4 entry)` → `SignEntry` with `frontRawLines = null`,
  `backRawLines = null`.
- `VersionedFileSignEntryLoader`: add an explicit `V4` branch (parse as `SignEntryV4[]`, back up as `.v4.bak`,
  convert via `Version5Converter`) ahead of the current catch-all `else`; catch-all becomes "V5+, load directly
  as `SignEntry[]`".
- `RegionShardedSignEntryWriter`: tag new writes `SignFileVersions.V5`.

### `SignManager`

`RuntimeConfig` gains a third field: `SignLinesParser parser`, built once in `buildRuntimeConfig()` from the
same `ConfigManager.get().getMarkerGroups()` list already used for `prefixGroupMap` (no new game-type coupling
— `SignLinesParser` is pure).

`SignEntry` gains a helper (`withParsedText(frontText, backText)`, same pattern as `withKey`) so a reparsed
result can be swapped in without touching key/playerId/createdAtMillis/raw lines.

`reloadConfig()` changes only the *new*-representation half of its per-entry diff:

```
for entry in signCache.values():
    oldRep = computeRepresentation(entry, oldPrefixGroupMap)          // unchanged: cached value is correct as-is

    if entry.frontRawLines() != null && entry.backRawLines() != null:
        freshFront = newConfig.parser().parse(entry.frontRawLines())
        freshBack  = newConfig.parser().parse(entry.backRawLines())
        entry = entry.withParsedText(freshFront, freshBack)
        signCache.put(entry.key(), entry)                              // Q3: keep cache authoritative going forward
    // else: raw text unavailable (pre-migration entry) — fall back to today's behavior, i.e. do nothing here

    newRep = computeRepresentation(entry, newConfig.prefixGroupMap())
    action = computeTransitionAction(entry.key(), oldRep, newRep, newConfig.actionFactory())
    dispatch(action) if action != null
```

Both raw-line arrays are checked together (not independently) — a partial reparse (fresh front, stale back)
could produce a representation `getDetail()`/`getLabel()` never actually see from a real live sign, since those
always parse both sides together.

`addOrUpdateSign` / `removeByKey` are untouched — signs arriving from `SignHelper` already carry a fresh parse
of live text on every call, so they never had this staleness problem.

## Testing

- `SignEntry`/`SignEntryHelper` — unaffected (raw lines don't change label/detail/prefix derivation), but the
  new fields need `equals`/`hashCode` coverage.
- `SignManager` has no existing test file; this is the first test coverage for it. Given the `reloadConfig`
  change is the entire point of this fix, add tests there: a sign with raw lines present self-heals across a
  simulated prefix-text-only config edit; a sign with `null` raw lines does not (falls back, matching today);
  cache is updated after a successful reparse.
- `VersionedFileSignEntryLoader` — add a `V4`-file fixture (no raw lines) and assert it loads via
  `Version5Converter` with `frontRawLines`/`backRawLines` both `null`.
- Manual: `runServer`, edit a `REGEX` group's prefix text, `/bluemap reload`, confirm a sign whose chunk stayed
  loaded throughout self-heals without a restart.

## Docs

- `../../README.md`: short note (troubleshooting/notes section) — changing a marker group's prefix (especially a
  `REGEX` prefix) can orphan signs parsed under the old prefix on servers running a version before this fix;
  re-edit the sign or restart the server to refresh it; signs created/re-edited after upgrading past the
  version that ships this fix self-heal automatically on `/bluemap reload`.
- Release notes: flag this fix explicitly when notes are pulled for the release that ships it — it's a
  user-visible behavior change worth calling out, not just a changelog line.

## Not in scope

- No retroactive repair tool for already-orphaned signs (accepted per Q2 — one-time manual re-edit is fine).
- No stable per-group `id` config field (rejected direction, see Decision above).
- No change to live-edit paths (`addOrUpdateSign`/`removeByKey`) — already correct.
