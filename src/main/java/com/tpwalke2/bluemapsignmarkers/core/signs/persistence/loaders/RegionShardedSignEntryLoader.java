package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RegionShardedSignEntryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private RegionShardedSignEntryLoader() {
    }

    public static boolean hasSignData(Path storageRoot) {
        if (!Files.isDirectory(storageRoot)) return false;

        try (var files = Files.walk(storageRoot)) {
            return files.anyMatch(RegionShardedSignEntryLoader::isRegionFile);
} catch (IOException e) {
    LOGGER.error("Failed to inspect markers storage directory {}; assuming it contains sign data to avoid triggering legacy migration", storageRoot, e);
    return true;
}
    }

    public static List<SignEntry> loadSignEntries(Path storageRoot, MarkerGroup[] markerGroups, Gson gson) {
        var result = new ArrayList<SignEntry>();

        try (var files = Files.walk(storageRoot)) {
            var regionFiles = files.filter(RegionShardedSignEntryLoader::isRegionFile).sorted().toList();
            for (var regionFile : regionFiles) {
                loadRegionFile(regionFile, markerGroups, gson, result);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to walk markers storage directory {}", storageRoot, e);
        }

        return result;
    }

    private static void loadRegionFile(Path regionFile, MarkerGroup[] markerGroups, Gson gson, List<SignEntry> result) {
        try {
            var content = Files.readString(regionFile, StandardCharsets.UTF_8);
            var signEntries = VersionedFileSignEntryLoader.loadSignEntries(regionFile.toString(), content, markerGroups, gson);
            if (signEntries != null) {
                result.addAll(Arrays.asList(signEntries));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read region file {}", regionFile, e);
        }
    }

    private static boolean isRegionFile(Path path) {
        if (!Files.isRegularFile(path)) return false;
        var name = path.getFileName().toString();
        return name.startsWith("r.") && name.endsWith(".json");
    }
}
