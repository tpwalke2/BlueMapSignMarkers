package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.SignFileVersions;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.VersionedSignFile;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
                var signEntriesV3 = Arrays.stream(gson.fromJson(versionedSignFile.data(), SignEntryV2[].class))
                        .map(entry -> convertV2EntrySafely(entry, markerGroups))
                        .filter(Objects::nonNull)
                        .toList();

                if (!FileUtils.createBackup(path, ".v2.bak", "markers file")) {
                    LOGGER.error(
                            "Failed to back up markers file {} before v2-to-v4 migration; continuing to load the "
                                    + "entries in-memory without a backup", path);
                }

                return convertV3EntriesToV4(signEntriesV3, path);
            } else if (versionedSignFile.version() == SignFileVersions.V3) {
                LOGGER.info("Loading version 3 markers file...");
                var signEntriesV3 = Arrays.asList(gson.fromJson(versionedSignFile.data(), SignEntryV3[].class));

                if (!FileUtils.createBackup(path, ".v3.bak", "markers file")) {
                    LOGGER.error(
                            "Failed to back up markers file {} before v3-to-v4 migration; continuing to load the "
                                    + "entries in-memory without a backup", path);
                }

                return convertV3EntriesToV4(signEntriesV3, path);
            } else {
                LOGGER.info("Loading version 4+ markers file...");
                return gson.fromJson(versionedSignFile.data(), SignEntry[].class);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load versioned sign file, falling back to version 1");
        }
        return null;
    }

    private static SignEntry[] convertV3EntriesToV4(List<SignEntryV3> signEntriesV3, String path) {
        var fileLastModifiedMillis = getLastModifiedMillis(path);
        var result = new SignEntry[signEntriesV3.size()];
        for (var i = 0; i < signEntriesV3.size(); i++) {
            result[i] = Version4Converter.convertToV4(signEntriesV3.get(i), i, fileLastModifiedMillis);
        }
        return result;
    }

    private static long getLastModifiedMillis(String path) {
        try {
            return Files.getLastModifiedTime(Path.of(path)).toMillis();
        } catch (IOException e) {
            LOGGER.warn("Failed to read last-modified time of {}, defaulting to 0 for createdAtMillis", path, e);
            return 0L;
        }
    }

    // Isolates one bad entry so it doesn't abort the whole file's v2-to-v3-to-v4 conversion - the same
    // log-and-skip pattern SignProvider.loadSigns already applies per entry after loading.
    private static SignEntryV3 convertV2EntrySafely(SignEntryV2 entry, MarkerGroup[] markerGroups) {
        try {
            return Version3Converter.convertToV3(entry, markerGroups);
        } catch (Exception e) {
            LOGGER.error("Failed to convert v2 sign entry, skipping: {}", entry.key(), e);
            return null;
        }
    }
}
