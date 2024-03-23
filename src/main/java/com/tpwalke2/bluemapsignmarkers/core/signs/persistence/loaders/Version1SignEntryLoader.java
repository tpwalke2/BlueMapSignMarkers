package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;

public class Version1SignEntryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private Version1SignEntryLoader() {}

    public static SignEntry[] loadSignEntries(String path, String signsContent, Gson gson) {
        LOGGER.info("Loading version 1 markers file...");
        var signEntries = Arrays.stream(gson.fromJson(signsContent, SignEntry[].class))
                .map(Version1SignEntryLoader::withNormalizedKey)
                .toArray(SignEntry[]::new);

        var preMigrationBackupPath = path + ".v1.bak";
        var preMigrationBackupFile = new File(preMigrationBackupPath);
        if (!preMigrationBackupFile.exists()) {
            LOGGER.info("Creating backup of markers file...");
            FileUtils.copyFile(path, preMigrationBackupPath);
        }

        return signEntries;
    }

    private static final String NETHER = "nether";
    private static final String END = "end";
    private static final String OVERWORLD = "overworld";

    private static SignEntry withNormalizedKey(SignEntry entry) {
        return entry.withKey(withNormalizedMapId(entry.key()));
    }

    private static SignEntryKey withNormalizedMapId(SignEntryKey key)
    {
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
