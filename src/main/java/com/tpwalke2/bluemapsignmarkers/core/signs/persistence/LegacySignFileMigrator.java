package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders.Version1SignEntryLoader;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders.VersionedFileSignEntryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class LegacySignFileMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private LegacySignFileMigrator() {
    }

    public static List<SignEntry> migrate(String legacyPath, Path storageRoot, MarkerGroup[] markerGroups, Gson gson) {
        var legacyFile = Path.of(legacyPath);
        if (!Files.exists(legacyFile)) {
            LOGGER.info("No legacy markers file found at {}, starting fresh", legacyPath);
            return List.of();
        }

        LOGGER.info("Migrating legacy markers file {} to region-sharded storage at {}...", legacyPath, storageRoot);

        String legacyContent;
        try {
            legacyContent = Files.readString(legacyFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to read legacy markers file {}", legacyPath, e);
            return List.of();
        }

        SignEntry[] signEntries;
        try {
            signEntries = VersionedFileSignEntryLoader.loadSignEntries(legacyPath, legacyContent, markerGroups, gson);
            if (signEntries == null) {
                signEntries = Version1SignEntryLoader.loadSignEntries(legacyPath, legacyContent, markerGroups, gson);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse legacy markers file {}, leaving it in place", legacyPath, e);
            return List.of();
        }

        if (signEntries == null) {
            LOGGER.error("Legacy markers file {} is not a recognized format; leaving it in place", legacyPath);
            return List.of();
        }

        var entryList = Arrays.asList(signEntries);
        RegionShardedSignEntryWriter.write(storageRoot, entryList, gson);

        // Back up the legacy file once migration is complete (or immediately if it contained zero entries).
        var expectedRegionFiles = SignRegionPartitioner.partition(entryList).keySet().stream()
                .map(key -> storageRoot.resolve(key.relativeFilePath()))
                .toList();
        var migrationWroteAllRegions = entryList.isEmpty() || expectedRegionFiles.stream().allMatch(Files::exists);

        if (migrationWroteAllRegions) {
            FileUtils.moveToBackup(legacyPath, ".migrated", "legacy markers file");
        } else {
            LOGGER.error("Migration failed to write one or more region files under {}; leaving legacy file in place at {}", storageRoot, legacyPath);
        }

        LOGGER.info("Migration complete, {} sign(s) now stored under {}", entryList.size(), storageRoot);

        return entryList;
    }
}
