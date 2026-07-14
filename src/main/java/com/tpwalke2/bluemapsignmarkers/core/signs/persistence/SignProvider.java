package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders.RegionShardedSignEntryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class SignProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .create();

    private SignProvider() {
    }

    public static void loadSigns(Path storageRoot, String legacyPath) {
        LOGGER.info("Loading markers from {}...", storageRoot);

        try {
            var groups = ConfigManager.get().getMarkerGroups();

            var signEntries = RegionShardedSignEntryLoader.hasSignData(storageRoot)
                    ? RegionShardedSignEntryLoader.loadSignEntries(storageRoot, groups, GSON)
                    : LegacySignFileMigrator.migrate(legacyPath, storageRoot, groups, GSON);

            for (SignEntry signEntry : signEntries) {
                SignManager.addOrUpdate(signEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load markers", e);
        }
    }

    public static void saveSigns(Path storageRoot) {
        LOGGER.info("Saving markers to {}...", storageRoot);

        RegionShardedSignEntryWriter.write(storageRoot, SignManager.getAll(), GSON);
    }
}
