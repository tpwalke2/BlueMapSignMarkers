package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SignProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final Gson GSON = new GsonBuilder()
            .setLenient()
            .create();

    private SignProvider() {}

    public static void loadSigns(String path) {
        LOGGER.info("Loading markers from file: {}...", path);

        var file = new File(path);
        if (!file.exists()) {
            LOGGER.info("Markers file does not yet exist, skipping...");
            return;
        }

        String signsContent;
        try {
            signsContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to read markers file", e);
            return;
        }

        try {
            // versioned file attempt
            var signEntries = loadVersionedSignEntries(signsContent);

            // version 1 attempt
            if (signEntries == null) {

                signEntries = GSON.fromJson(signsContent, SignEntry[].class);

                var preMigrationBackupPath = path + ".v1.bak";
                var preMigrationBackupFile = new File(preMigrationBackupPath);
                if (!preMigrationBackupFile.exists()) {
                    LOGGER.info("Creating backup of markers file...");
                    copyFile(path, preMigrationBackupPath);
                }
            }

            for (SignEntry signEntry : signEntries) {
                SignManager.addOrUpdate(signEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load markers from file", e);
        }
    }

    private static SignEntry[] loadVersionedSignEntries(String content) {
        try {
            var versionedSignFile = GSON.fromJson(content, VersionedSignFile.class);
            if (versionedSignFile.version() == SignFileVersions.V2) {
                return GSON.fromJson(versionedSignFile.data(), SignEntry[].class);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load versioned sign file, falling back to version 1", e);
        }
        return null;
    }

    private static void copyFile(String sourcePath, String destinationPath) {
        try {
            Files.copy(Paths.get(sourcePath), Paths.get(destinationPath));
        } catch (IOException e) {
            LOGGER.warn("Failed to copy {} to {}: {}", sourcePath, destinationPath, e);
        }
    }

    public static void saveSigns(String path) {
        LOGGER.info("Saving markers to file: {}...", path);

        var file = new File(path);
        var parent = file.getParentFile();
        if (!parent.exists()) {
            try {
                Files.createDirectories(Paths.get(parent.getAbsolutePath()));
            } catch (IOException e) {
                LOGGER.error("Failed to create parent directories for markers file", e);
                return;
            }
        }

        var signEntries = SignManager.getAll();
        var signEntryData = GSON.toJson(signEntries);

        try (FileWriter writer = new FileWriter(path)) {
            GSON.toJson(new VersionedSignFile(SignFileVersions.V2, signEntryData), writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save markers to file", e);
        }
    }
}
