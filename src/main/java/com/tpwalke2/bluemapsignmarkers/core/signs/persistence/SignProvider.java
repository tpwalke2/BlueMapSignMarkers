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

import java.nio.file.Files;
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

            // The legacy file's continued presence (not yet renamed to its ".migrated" backup) is what
            // signals an incomplete migration - not just "no region files exist yet". A crash partway
            // through a first migration can leave some region files written and others missing; re-running
            // migrate() in that case re-derives every region file from the still-present legacy source
            // (the actual source of truth) instead of loading the partial region-sharded state as if it
            // were complete, which would permanently lose every sign not yet written at crash time.
            var signEntries = Files.exists(Path.of(legacyPath)) || !RegionShardedSignEntryLoader.hasSignData(storageRoot)
                    ? LegacySignFileMigrator.migrate(legacyPath, storageRoot, groups, GSON)
                    : RegionShardedSignEntryLoader.loadSignEntries(storageRoot, groups, GSON);

            for (SignEntry signEntry : signEntries) {
                try {
                    SignManager.addOrUpdate(signEntry);
                } catch (Exception entryException) {
                    LOGGER.error("Failed to load sign entry {}", signEntry.key(), entryException);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load markers", e);
        }
    }

    public static void saveSigns(Path storageRoot) {
        LOGGER.info("Saving markers to {}...", storageRoot);

        if (!RegionShardedSignEntryWriter.write(storageRoot, SignManager.getAll(), GSON)) {
            LOGGER.error("One or more region files failed to save under {}; some signs may not survive a restart", storageRoot);
        }
    }
}
