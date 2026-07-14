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

        var signEntries = VersionedFileSignEntryLoader.loadSignEntries(legacyPath, legacyContent, markerGroups, gson);
        if (signEntries == null) {
            signEntries = Version1SignEntryLoader.loadSignEntries(legacyPath, legacyContent, markerGroups, gson);
        }

        var entryList = Arrays.asList(signEntries);
        RegionShardedSignEntryWriter.write(storageRoot, entryList, gson);
        FileUtils.moveToBackup(legacyPath, ".migrated", "legacy markers file");

        LOGGER.info("Migration complete, {} sign(s) now stored under {}", entryList.size(), storageRoot);

        return entryList;
    }
}
