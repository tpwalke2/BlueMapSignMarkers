package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.SignFileVersions;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.VersionedSignFile;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

public class VersionedFileSignEntryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private VersionedFileSignEntryLoader() {
    }

    public static SignEntry[] loadSignEntries(
            String path,
            String content,
            MarkerGroup[] markerGroups,
            Gson gson) {
        try {
            var versionedSignFile = gson.fromJson(content, VersionedSignFile.class);

            // A structurally-valid JSON document missing "version"/"data" (e.g. "{}") parses without
            // error but isn't a versioned-envelope file at all - treat that as an explicit, intentional
            // signal to fall back to the version 1 loader rather than relying on version()/data() being
            // null to coincidentally route through the same fallback below.
            if (versionedSignFile == null || versionedSignFile.version() == null || versionedSignFile.data() == null) {
                LOGGER.info("Markers file {} has no version/data envelope, treating as legacy version 1 format...", path);
                return null;
            }

            if (versionedSignFile.version() == SignFileVersions.V2) {
                LOGGER.info("Loading version 2 markers file...");
                var signEntries = Arrays.stream(gson.fromJson(versionedSignFile.data(), SignEntryV2[].class))
                        .map(entry -> convertEntrySafely(entry, markerGroups))
                        .filter(Objects::nonNull)
                        .toArray(SignEntry[]::new);

                if (!FileUtils.createBackup(path, ".v2.bak", "markers file")) {
                    LOGGER.error(
                            "Failed to back up markers file {} before v2-to-v3 migration; aborting migration to "
                                    + "avoid overwriting the original with no recoverable backup", path);
                    return null;
                }

                return signEntries;
            } else {
                LOGGER.info("Loading version 3+ markers file...");
                return gson.fromJson(versionedSignFile.data(), SignEntry[].class);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load versioned sign file, falling back to version 1");
        }
        return null;
    }

    // Isolates one bad entry so it doesn't abort the whole file's v2-to-v3 conversion - the same
    // log-and-skip pattern SignProvider.loadSigns already applies per entry after loading.
    private static SignEntry convertEntrySafely(SignEntryV2 entry, MarkerGroup[] markerGroups) {
        try {
            return Version3Converter.convertToV3(entry, markerGroups);
        } catch (Exception e) {
            LOGGER.error("Failed to convert v2 sign entry, skipping: {}", entry.key(), e);
            return null;
        }
    }
}
