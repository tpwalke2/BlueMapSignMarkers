package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders.RegionShardedSignEntryLoader;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionShardedSignEntryLoaderTest {

    private static final Gson GSON = new Gson();
    private static final MarkerGroup[] NO_GROUPS = new MarkerGroup[0];

    @Test
    void hasSignDataIsFalseWhenStorageRootDoesNotExist(@TempDir Path tempDir) {
        var missingRoot = tempDir.resolve("does-not-exist");

        assertFalse(RegionShardedSignEntryLoader.hasSignData(missingRoot));
    }

    @Test
    void hasSignDataIsFalseWhenStorageRootIsEmpty(@TempDir Path storageRoot) {
        assertFalse(RegionShardedSignEntryLoader.hasSignData(storageRoot));
    }

    @Test
    void hasSignDataIsTrueOnceARegionFileExists(@TempDir Path storageRoot) {
        RegionShardedSignEntryWriter.write(
                storageRoot, List.of(signEntry(0, 0, "minecraft:overworld", "Town Hall")), GSON);

        assertTrue(RegionShardedSignEntryLoader.hasSignData(storageRoot));
    }

    @Test
    void loadSignEntriesRoundTripsAcrossRegionsAndDimensions(@TempDir Path storageRoot) {
        var overworldEntry = signEntry(0, 0, "minecraft:overworld", "Town Hall");
        var farOverworldEntry = signEntry(600, 600, "minecraft:overworld", "Outpost");
        var netherEntry = signEntry(0, 0, "minecraft:the_nether", "Portal Hub");

        RegionShardedSignEntryWriter.write(
                storageRoot, List.of(overworldEntry, farOverworldEntry, netherEntry), GSON);

        var loaded = RegionShardedSignEntryLoader.loadSignEntries(storageRoot, NO_GROUPS, GSON);

        assertEquals(3, loaded.size());
        assertTrue(loaded.contains(overworldEntry));
        assertTrue(loaded.contains(farOverworldEntry));
        assertTrue(loaded.contains(netherEntry));
    }

    private static SignEntry signEntry(int x, int z, String dimension, String label) {
        return new SignEntry(
                new SignEntryKey(x, 64, z, dimension),
                "unknown",
                new SignLinesParseResult("[poi]", label, label),
                new SignLinesParseResult(null, "", ""));
    }
}
