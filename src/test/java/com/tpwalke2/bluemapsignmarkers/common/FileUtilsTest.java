package com.tpwalke2.bluemapsignmarkers.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FileUtilsTest {

    @Test
    void createBackupCopiesTheOriginalFileWhenNoBackupExistsYet(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");

        var succeeded = FileUtils.createBackup(original.toString(), ".bak", "test file");

        var backup = tempDir.resolve("original.txt.bak");
        assertTrue(succeeded);
        assertTrue(Files.exists(backup));
        assertEquals("original content", Files.readString(backup));
        assertTrue(Files.exists(original), "the original should be untouched by a backup copy");
    }

    @Test
    void createBackupDoesNothingWhenABackupAlreadyExists(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");
        var backup = tempDir.resolve("original.txt.bak");
        Files.writeString(backup, "pre-existing backup content");

        var succeeded = FileUtils.createBackup(original.toString(), ".bak", "test file");

        assertTrue(succeeded, "an already-existing backup counts as success");
        assertEquals("pre-existing backup content", Files.readString(backup),
                "an existing backup should not be overwritten");
    }

    @Test
    void moveToBackupMovesTheOriginalFileWhenSourceExistsAndNoBackupExists(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");

        FileUtils.moveToBackup(original.toString(), ".bak", "test file");

        var backup = tempDir.resolve("original.txt.bak");
        assertTrue(Files.exists(backup));
        assertEquals("original content", Files.readString(backup));
        assertFalse(Files.exists(original), "the original should have been moved, not copied");
    }

    @Test
    void moveToBackupDoesNothingWhenTheOriginalFileDoesNotExist(@TempDir Path tempDir) {
        var original = tempDir.resolve("missing.txt");

        assertDoesNotThrow(() -> FileUtils.moveToBackup(original.toString(), ".bak", "test file"));

        assertFalse(Files.exists(tempDir.resolve("missing.txt.bak")));
    }

    // A caller relying on "the original is gone once moveToBackup returns" (LegacySignFileMigrator re-runs a
    // full migration on every boot for as long as the legacy file still exists) would otherwise be stuck
    // re-migrating forever if the original were left in place just because ".bak" was already taken.
    @Test
    void moveToBackupFallsBackToANumberedNameWhenTheDefaultBackupAlreadyExists(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");
        var backup = tempDir.resolve("original.txt.bak");
        Files.writeString(backup, "pre-existing backup content");

        FileUtils.moveToBackup(original.toString(), ".bak", "test file");

        var secondBackup = tempDir.resolve("original.txt.bak.2");
        assertFalse(Files.exists(original), "the original must always be retired, even if the default backup path is taken");
        assertEquals("pre-existing backup content", Files.readString(backup), "an existing backup should not be overwritten");
        assertTrue(Files.exists(secondBackup));
        assertEquals("original content", Files.readString(secondBackup));
    }

    @Test
    void moveToBackupKeepsNumberingWhenMultipleBackupsAlreadyExist(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");
        Files.writeString(tempDir.resolve("original.txt.bak"), "first backup");
        Files.writeString(tempDir.resolve("original.txt.bak.2"), "second backup");

        FileUtils.moveToBackup(original.toString(), ".bak", "test file");

        var thirdBackup = tempDir.resolve("original.txt.bak.3");
        assertFalse(Files.exists(original));
        assertTrue(Files.exists(thirdBackup));
        assertEquals("original content", Files.readString(thirdBackup));
    }

    // The backup destination is routed through the original file itself as a fake parent directory (a regular
    // file can't be traversed as one, on any OS), so Files.copy throws; createBackup now reports that failure
    // back to the caller via its return value instead of swallowing it.
    @Test
    void createBackupReturnsFalseWhenTheCopyFails(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");
        var unwritableSuffix = "/nested/backup.bak";

        var succeeded = assertDoesNotThrow(() -> FileUtils.createBackup(original.toString(), unwritableSuffix, "test file"));

        assertFalse(succeeded, "a failed copy must be reported back to the caller, not swallowed");
        assertFalse(Files.exists(Path.of(original + unwritableSuffix)), "the backup was never actually created");
    }

    // A directory sitting at the backup path isn't a valid backup - treating File.exists() alone as "already
    // backed up" would let a caller proceed to overwrite the original with no real backup in place.
    @Test
    void createBackupReturnsFalseWhenTheBackupDestinationIsADirectory(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");
        var backup = tempDir.resolve("original.txt.bak");
        Files.createDirectory(backup);

        var succeeded = FileUtils.createBackup(original.toString(), ".bak", "test file");

        assertFalse(succeeded, "a directory at the backup path is not a valid backup");
    }
}
