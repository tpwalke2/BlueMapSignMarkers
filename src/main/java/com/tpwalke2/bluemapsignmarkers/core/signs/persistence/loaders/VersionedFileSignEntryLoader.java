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
            if (versionedSignFile.version() == SignFileVersions.V2) {
                LOGGER.info("Loading version 2 markers file...");
                var signEntries = Arrays.stream(gson.fromJson(versionedSignFile.data(), SignEntryV2[].class))
                        .map(entry -> Version3Converter.convertToV3(entry, markerGroups))
                        .toArray(SignEntry[]::new);

                FileUtils.createBackup(path, ".v2.bak", "markers file");

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
}
