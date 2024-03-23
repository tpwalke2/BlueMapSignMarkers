package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.SignFileVersions;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.VersionedSignFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionedFileSignEntryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private VersionedFileSignEntryLoader() {}

    public static SignEntry[] loadSignEntries(String content, Gson gson) {
        try {
            var versionedSignFile = gson.fromJson(content, VersionedSignFile.class);
            // TODO address loading prefix vs type in each sign entry
            if (versionedSignFile.version() == SignFileVersions.V2) {
                LOGGER.info("Loading version 2 markers file...");
                return gson.fromJson(versionedSignFile.data(), SignEntry[].class);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load versioned sign file, falling back to version 1");
        }
        return null;
    }
}
