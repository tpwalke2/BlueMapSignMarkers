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
import java.util.HashSet;
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
        var writeSucceeded = RegionShardedSignEntryWriter.write(storageRoot, entryList, gson);

        // Back up the legacy file once migration is complete (or immediately if it contained zero entries).
        // Content-verifies rather than just checking file existence: a region file that exists but is
        // truncated/corrupt must still block finalizing the migration, since otherwise the legacy backup
        // finalizes over genuinely lost data.
        var migrationWroteAllRegions = entryList.isEmpty()
                || (writeSucceeded && regionFilesRoundTripCleanly(storageRoot, entryList, markerGroups, gson));

        if (migrationWroteAllRegions) {
            FileUtils.moveToBackup(legacyPath, ".migrated", "legacy markers file");
        } else {
            LOGGER.error("Migration failed to write one or more region files under {}; leaving legacy file in place at {}", storageRoot, legacyPath);
        }

        LOGGER.info("Migration complete, {} sign(s) now stored under {}", entryList.size(), storageRoot);

        return entryList;
    }

    // Round-trip parses each region file the write pass produced (rather than just checking it exists) so
    // a truncated/corrupt region file - which loads as "no entries" - can't slip past verification and
    // let the legacy file get backed up over genuinely lost data. Compares the full parsed entry set
    // against what was expected to be written, not just its size - matching lengths with different/altered
    // content (e.g. valid-but-corrupted JSON) would otherwise pass and retire the legacy source over
    // altered data. SignEntry is a record, so set equality is a structural content comparison; a Set
    // (rather than a positional list compare) is used since the writer's on-disk ordering isn't guaranteed
    // to match partition()'s insertion order.
    // Visible for testing: lets tests exercise this against a region file corrupted after being written,
    // which migrate() itself has no seam to do (it writes and verifies in the same call).
    static boolean regionFilesRoundTripCleanly(
            Path storageRoot, List<SignEntry> entryList, MarkerGroup[] markerGroups, Gson gson) {
        var partitions = SignRegionPartitioner.partition(entryList);

        for (var partition : partitions.entrySet()) {
            Path filePath;
            try {
                filePath = storageRoot.resolve(partition.getKey().relativeFilePath());
            } catch (IllegalArgumentException e) {
                LOGGER.error("Failed to resolve storage path for region key {} while verifying migration", partition.getKey(), e);
                return false;
            }

            String content;
            try {
                content = Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error("Region file {} is missing or unreadable after migration write", filePath, e);
                return false;
            }

            var parsed = VersionedFileSignEntryLoader.loadSignEntries(filePath.toString(), content, markerGroups, gson);
            var expected = partition.getValue();
            if (parsed == null || parsed.length != expected.size() || !new HashSet<>(Arrays.asList(parsed)).equals(new HashSet<>(expected))) {
                LOGGER.error(
                        "Region file {} failed to round-trip parse after migration write (expected {} entries matching {}, got {})",
                        filePath, expected.size(), expected, parsed == null ? "none" : Arrays.toString(parsed));
                return false;
            }
        }

        return true;
    }
}
