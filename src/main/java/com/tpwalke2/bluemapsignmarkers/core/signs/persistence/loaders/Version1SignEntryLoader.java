package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

public class Version1SignEntryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private Version1SignEntryLoader() {
    }

    public static SignEntry[] loadSignEntries(
            String path,
            String signsContent,
            MarkerGroup[] markerGroups,
            Gson gson) {
        LOGGER.info("Loading version 1 markers file...");
        var signEntries = Arrays.stream(gson.fromJson(signsContent, SignEntryV2[].class))
                .map(entry -> loadEntry(entry, markerGroups))
                .filter(Objects::nonNull)
                .toArray(SignEntry[]::new);

        if (!FileUtils.createBackup(path, ".v1.bak", "markers file")) {
            throw new IllegalStateException(
                    "Failed to back up markers file " + path + " before v1-to-v3 migration; aborting migration to "
                            + "avoid overwriting the original with no recoverable backup");
        }

        return signEntries;
    }

    private static final String NAMESPACE_PREFIX = "minecraft:";
    private static final String NETHER = "nether";
    private static final String THE_NETHER = "the_nether";
    private static final String END = "end";
    private static final String THE_END = "the_end";
    private static final String OVERWORLD = "overworld";

    // Isolates one bad entry (malformed key, unexpected null field, etc.) so it doesn't abort the
    // whole file's migration - the same log-and-skip pattern SignProvider.loadSigns already applies
    // per entry after loading.
    private static SignEntry loadEntry(SignEntryV2 entry, MarkerGroup[] markerGroups) {
        try {
            return Version3Converter.convertToV3(withNormalizedKey(entry), markerGroups);
        } catch (Exception e) {
            LOGGER.error("Failed to load v1 sign entry, skipping: {}", entry.key(), e);
            return null;
        }
    }

    private static SignEntryV2 withNormalizedKey(SignEntryV2 entry) {
        return entry.withKey(withNormalizedMapId(entry.key()));
    }

    private static SignEntryKey withNormalizedMapId(SignEntryKey key) {
        return key.withParentMap(getNormalizedMapId(key.parentMap()));
    }

    private static String getNormalizedMapId(String mapId) {
        var lower = mapId.toLowerCase();
        var path = lower.startsWith(NAMESPACE_PREFIX) ? lower.substring(NAMESPACE_PREFIX.length()) : lower;

        // Recognizes both the short vanilla dimension names ("nether"/"end"/"overworld") this mod's
        // earliest versions stored and the canonical-but-unnamespaced resource path
        // ("the_nether"/"the_end"), with or without a "minecraft:" namespace already attached - any of
        // these previously fell through unchanged (or only partly normalized) and would permanently
        // mismatch the live "minecraft:the_nether"/"minecraft:the_end"/"minecraft:overworld" dimension
        // key post-migration, duplicating markers as "new" signs.
        return switch (path) {
            case NETHER, THE_NETHER -> NAMESPACE_PREFIX + THE_NETHER;
            case END, THE_END -> NAMESPACE_PREFIX + THE_END;
            case OVERWORLD -> NAMESPACE_PREFIX + OVERWORLD;
            default -> lower;
        };
    }
}
