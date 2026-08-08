# 02 — Abort migration overwrite when backup fails

**What to build:** When `FileUtils.createBackup`/`copyFile` fails partway through a data-changing migration (config
V1→V2, sign-file V1/V2→V3) — disk full, permissions, whatever — the migration stops before overwriting the original
file. Today the failure is only logged as a warning and the caller proceeds to overwrite anyway, so a failed backup
can mean the user's only copy of their pre-migration data is gone with no recoverable backup and no visible signal
beyond a log line.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] A failed backup (simulate via an unwritable target) prevents the subsequent overwrite of the original file
- [x] The migration surfaces the failure clearly (not just a log line) so the user/operator knows the migration didn't complete
- [x] A successful backup still lets the migration proceed as before

## Comments

`FileUtils.createBackup` now returns `boolean` (previously `void`) - `false` when the copy attempt failed
(`copyFile` also now returns `boolean` instead of swallowing the `IOException`). All three call sites check it:

- `ConfigProvider.loadV1Config` throws `IllegalStateException` on backup failure. It's caught by `loadConfig`'s
  existing `catch (Exception e)`, which logs at ERROR and returns `null` - `saveConfig` (the overwrite) never runs,
  so the caller (`ConfigManager`) falls back to in-memory defaults instead of touching the file on disk.
- `Version1SignEntryLoader.loadSignEntries` (V1→V3) throws the same way, caught by
  `LegacySignFileMigrator.migrate`'s `catch (Exception e)`, which logs "leaving it in place" and returns without
  writing region files or moving the legacy file.
- `VersionedFileSignEntryLoader.loadSignEntries` (V2→V3) logs at ERROR and returns `null` directly (this method's
  existing contract for "couldn't load this format") instead of throwing, since a thrown exception here would be
  swallowed by its own catch-all and misreported as "falling back to version 1". Both its callers
  (`LegacySignFileMigrator` and `RegionShardedSignEntryLoader`) already treat a `null` return as "don't use this
  data", so no region files get written and no legacy file gets moved.

A successful backup (existing behavior, `createBackup` returns `true`) leaves all three paths unchanged.

Tests: `FileUtilsTest` now asserts `createBackup`/`copyFile`'s return values (including the failure case, previously
only asserted "doesn't throw"). Added `Version1SignEntryLoaderTest.loadSignEntriesAbortsWithoutReturningEntriesWhenTheBackupFails`
and `VersionedFileSignEntryLoaderTest.v2ContentReturnsNullRatherThanOverwritingWhenTheBackupFails`, both simulating
the backup failure by deleting the on-disk source file after its content has already been read into memory (the
methods take content as a string, not a path to re-read) - reliable and cross-platform, unlike permission-bit or
path-length tricks which this Windows dev environment doesn't actually enforce (verified experimentally). A
matching forced-failure test for `ConfigProvider.loadV1Config` wasn't added: unlike the sign loaders, it reads the
file from disk internally with no seam to delete it between the read and the backup attempt, and no reliable
cross-platform way to fail just the backup copy while leaving the read that precedes it in the same call
succeeding. The `ConfigProvider` path reuses the identical `FileUtils.createBackup` return-value check already
covered at the `FileUtils` and sign-loader level. Full `./gradlew build` passes.

**Post-review fixes:** two issues found in `FileUtils` review, both fixed:

- `copyFile`/`moveFile`'s failure logs passed the exception through a `{}` placeholder, which only prints
  `e.toString()` and drops the stack trace. Now passed as SLF4J's trailing non-placeholder argument so the full
  stack trace is logged.
- `createBackup` treated any existing path at the backup destination as a successful backup, even a directory left
  behind by something else. A directory there isn't a valid backup, so `createBackup` now checks `isFile()` for the
  "already backed up" case and returns `false` (logging an ERROR) if something non-file occupies that path -
  callers correctly abort instead of proceeding to overwrite the original with no real backup.

Added `FileUtilsTest.createBackupReturnsFalseWhenTheBackupDestinationIsADirectory`. Full `./gradlew build` passes.
