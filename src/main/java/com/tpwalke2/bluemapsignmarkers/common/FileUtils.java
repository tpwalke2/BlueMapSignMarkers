package com.tpwalke2.bluemapsignmarkers.common;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class FileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private FileUtils() {}

    // Returns true if a backup already exists or was created successfully; false if backup creation was
    // attempted and failed, meaning callers must not proceed to overwrite the original file.
    public static boolean createBackup(String originalPath, String suffix, String fileDescription) {
        var backupPath = originalPath + suffix;
        var backupFile = new File(backupPath);
        if (backupFile.isFile()) return true;

        if (backupFile.exists()) {
            LOGGER.error(
                    "Backup destination {} already exists but isn't a regular file; refusing to treat it as a "
                            + "valid backup of {}", backupPath, fileDescription);
            return false;
        }

        LOGGER.info("Creating backup of {}...", fileDescription);
        return copyFile(originalPath, backupPath);
    }

    public static void moveToBackup(String originalPath, String suffix, String fileDescription) {
        var originalFile = new File(originalPath);
        if (!originalFile.exists()) return;

        var backupPath = uniqueBackupPath(originalPath, suffix);

        LOGGER.info("Backing up {}...", fileDescription);
        moveFile(originalPath, backupPath);
    }

    // Never leaves the original sitting at originalPath just because an earlier backup already occupies the
    // default backup path - a caller relying on "the original is gone once backed up" (e.g. LegacySignFileMigrator,
    // which re-runs a full migration on every boot for as long as the legacy file still exists) would otherwise
    // re-trigger forever, silently overwriting newer state with whatever that stale legacy file still holds.
    private static String uniqueBackupPath(String originalPath, String suffix) {
        var candidate = originalPath + suffix;
        var attempt = 2;
        while (new File(candidate).exists()) {
            candidate = originalPath + suffix + "." + attempt;
            attempt++;
        }
        return candidate;
    }

    // Copies via a temp file in the same directory, then an atomic move into place, so a failure partway through
    // (e.g. disk full mid-copy) never leaves a truncated/corrupt file sitting at destinationPath - one that a
    // later createBackup() call would otherwise mistake for a valid completed backup.
    private static boolean copyFile(String sourcePath, String destinationPath) {
        var destination = Paths.get(destinationPath);
        // UUID suffix: currently unreachable given single-threaded call sites, purely defensive against a
        // future concurrent caller writing the same destinationPath colliding on this temp file's name.
        var tempFile = destination.resolveSibling(destination.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.copy(Paths.get(sourcePath), tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to copy {} to {}", sourcePath, destinationPath, e);
            deleteQuietly(tempFile);
            return false;
        }
    }

    // Public so other writers doing their own temp-file + ATOMIC_MOVE (RegionShardedSignEntryWriter,
    // ConfigProvider) can clean up a leftover temp file the same way copyFile does here.
    public static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("Failed to clean up leftover temp file {}", path, e);
        }
    }

    private static void moveFile(String sourcePath, String destinationPath) {
        try {
            Files.move(Paths.get(sourcePath), Paths.get(destinationPath));
        } catch (IOException e) {
            LOGGER.warn("Failed to move {} to {}", sourcePath, destinationPath, e);
        }
    }
}
