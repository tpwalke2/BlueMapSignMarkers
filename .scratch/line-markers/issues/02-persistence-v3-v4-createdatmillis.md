# 02 — Persistence: `SignEntry.createdAtMillis`, V3→V4 migration

**Spec:** `.scratch/line-markers/spec.md` §2 ("Point order: `SignEntry.createdAtMillis`") and
"Persistence migration (V3 → V4)"

**Blocked by:** None.

**What to build:**
- `core/signs/SignEntry.java` — add `long createdAtMillis`, set once when a sign is first observed by
  `SignManager`, never recomputed after. Update `withKey`, `equals`/`hashCode`/`toString`.
- `core/signs/persistence/SignFileVersions.java` — add `V4`.
- New `core/signs/persistence/models/SignEntryV3.java` — frozen copy of today's `SignEntry` shape (no
  `createdAtMillis`), mirrors how `SignEntryV2` already exists alongside the current model.
- New `core/signs/persistence/loaders/Version4Converter.java`:
  ```java
  public static SignEntry convertToV4(SignEntryV3 entry, int indexInFile, long fileLastModifiedMillis) {
      return new SignEntry(entry.key(), entry.playerId(), entry.frontText(), entry.backText(),
              fileLastModifiedMillis + indexInFile);
  }
  ```
  Migrated entries get an arbitrary-but-stable `createdAtMillis` (file mtime + array index) — deliberate, not a
  bug: no real placement history exists for pre-existing signs.
- `core/signs/persistence/loaders/VersionedFileSignEntryLoader.java` — add a `V3` branch analogous to the existing
  `V2` branch: parse as `SignEntryV3[]`, convert each via `Version4Converter.convertToV4` (file mtime read once via
  `Files.getLastModifiedTime`, falls back to `0L` on `IOException` rather than throwing), back up the original as
  `.v3.bak` (`FileUtils.createBackup`, same convention as `.v2.bak`) before overwriting.
- `core/signs/persistence/RegionShardedSignEntryWriter.java` — write `SignFileVersions.V4` going forward.
- New test `src/test/java/.../persistence/loaders/Version4ConverterTest.java` — covers conversion including the
  arbitrary-timestamp behavior.

**Note on cross-file ties:** two old signs in the same eventual line group but different region files can get a
duplicate `createdAtMillis` after migration — accepted, not fixed here. Ticket 03 (`LineGroupResolver`) sorts on
`(createdAtMillis, key.x(), key.y(), key.z())` to break ties deterministically.

**Status:** done

- [x] `SignEntry` carries `createdAtMillis`; equals/hashCode/toString/withKey updated
- [x] `SignFileVersions.V4` added
- [x] `SignEntryV3` frozen model + `Version4Converter` implemented
- [x] `VersionedFileSignEntryLoader` migrates V3 files to V4, backs up as `.v3.bak`
- [x] `RegionShardedSignEntryWriter` writes V4 for new/updated files
- [x] A pre-V4 world save still loads (covered by `VersionedFileSignEntryLoaderTest`'s V3-migration test and
      `LegacySignFileMigratorTest`; not separately verified via `runServer`)
- [x] `Version4ConverterTest` passes; `./gradlew test` and `./gradlew build` pass
