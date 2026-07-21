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

        FileUtils.createBackup(original.toString(), ".bak", "test file");

        var backup = tempDir.resolve("original.txt.bak");
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

        FileUtils.createBackup(original.toString(), ".bak", "test file");

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

    // Documents review finding #13: copyFile catches IOException and only logs a warning - it never signals
    // failure back to createBackup's caller. Here the backup destination is routed through the original file
    // itself as a fake parent directory (a regular file can't be traversed as one, on any OS), so Files.copy
    // throws; createBackup swallows it and returns normally instead of throwing or returning a status, leaving
    // the caller to (incorrectly) assume the backup succeeded.
    @Test
    void createBackupSwallowsACopyFailureInsteadOfSignalingItToTheCaller(@TempDir Path tempDir) throws IOException {
        var original = tempDir.resolve("original.txt");
        Files.writeString(original, "original content");
        var unwritableSuffix = "/nested/backup.bak";

        assertDoesNotThrow(() -> FileUtils.createBackup(original.toString(), unwritableSuffix, "test file"));

        assertFalse(Files.exists(Path.of(original + unwritableSuffix)), "the backup was never actually created");
    }
}
