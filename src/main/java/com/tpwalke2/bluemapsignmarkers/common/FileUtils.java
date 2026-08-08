package com.tpwalke2.bluemapsignmarkers.common;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private FileUtils() {}

    // Returns true if a backup already exists or was created successfully; false if backup creation was
    // attempted and failed, meaning callers must not proceed to overwrite the original file.
    public static boolean createBackup(String originalPath, String suffix, String fileDescription) {
        var backupPath = originalPath + suffix;
        var backupFile = new File(backupPath);
        if (backupFile.exists()) return true;

        LOGGER.info("Creating backup of {}...", fileDescription);
        return copyFile(originalPath, backupPath);
    }

    public static void moveToBackup(String originalPath, String suffix, String fileDescription) {
        var originalFile = new File(originalPath);
        if (!originalFile.exists()) return;

        var backupPath = originalPath + suffix;
        var backupFile = new File(backupPath);
        if (backupFile.exists()) return;

        LOGGER.info("Backing up {}...", fileDescription);
        moveFile(originalPath, backupPath);
    }

    private static boolean copyFile(String sourcePath, String destinationPath) {
        try {
            Files.copy(Paths.get(sourcePath), Paths.get(destinationPath));
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to copy {} to {}: {}", sourcePath, destinationPath, e);
            return false;
        }
    }

    private static void moveFile(String sourcePath, String destinationPath) {
        try {
            Files.move(Paths.get(sourcePath), Paths.get(destinationPath));
        } catch (IOException e) {
            LOGGER.warn("Failed to move {} to {}: {}", sourcePath, destinationPath, e);
        }
    }
}
