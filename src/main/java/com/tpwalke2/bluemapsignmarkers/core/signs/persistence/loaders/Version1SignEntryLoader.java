package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

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
                .map(Version1SignEntryLoader::withNormalizedKey)
                .map(entry -> Version3Converter.convertToV3(entry, markerGroups))
                .toArray(SignEntry[]::new);

        FileUtils.createBackup(path, ".v1.bak", "markers file");

        return signEntries;
    }

    private static final String NETHER = "nether";
    private static final String END = "end";
    private static final String OVERWORLD = "overworld";

    private static SignEntryV2 withNormalizedKey(SignEntryV2 entry) {
        return entry.withKey(withNormalizedMapId(entry.key()));
    }

    private static SignEntryKey withNormalizedMapId(SignEntryKey key) {
        return key.withParentMap(getNormalizedMapId(key.parentMap()));
    }

    private static String getNormalizedMapId(String mapId) {
        var result = mapId.toLowerCase();

        if (result.equals(NETHER)) return World.NETHER.getValue().toString();
        if (result.equals(END)) return World.END.getValue().toString();
        if (result.equals(OVERWORLD)) return World.OVERWORLD.getValue().toString();

        return result;
    }
}
