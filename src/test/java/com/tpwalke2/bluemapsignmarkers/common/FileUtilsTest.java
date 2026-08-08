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

    @Test
    void moveToBackupDoesNothingWhenABackupAlreadyExists(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");
        var backup = tempDir.resolve("original.txt.bak");
        Files.writeString(backup, "pre-existing backup content");

        FileUtils.moveToBackup(original.toString(), ".bak", "test file");

        assertTrue(Files.exists(original), "the original should not be moved when a backup already exists");
        assertEquals("pre-existing backup content", Files.readString(backup));
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
