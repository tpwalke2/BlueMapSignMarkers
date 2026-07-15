package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders.RegionShardedSignEntryLoader;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.MarkerTypeV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignLinesParseResultV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySignFileMigratorTest {

    private static final Gson GSON = new Gson();
    private static final MarkerGroup[] NO_GROUPS = new MarkerGroup[0];

    @Test
    void returnsEmptyListAndWritesNothingWhenNoLegacyFileExists(@TempDir Path tempDir) {
        var legacyPath = tempDir.resolve("signs.json").toString();
        var storageRoot = tempDir.resolve("storage");

        var result = LegacySignFileMigrator.migrate(legacyPath, storageRoot, NO_GROUPS, GSON);

        assertEquals(List.of(), result);
        assertFalse(RegionShardedSignEntryLoader.hasSignData(storageRoot));
    }

    @Test
    void migratesAV3LegacyFileAndBacksItUpWithoutDeletingIt(@TempDir Path tempDir) throws IOException {
        var legacyPath = tempDir.resolve("signs.json").toString();
        var storageRoot = tempDir.resolve("storage");
        var entry = signEntry(0, 0, "minecraft:overworld", "Town Hall");
        writeLegacyV3File(legacyPath, entry);

        var result = LegacySignFileMigrator.migrate(legacyPath, storageRoot, NO_GROUPS, GSON);

        assertEquals(List.of(entry), result);
        assertTrue(RegionShardedSignEntryLoader.hasSignData(storageRoot));
        assertEquals(
                List.of(entry),
                RegionShardedSignEntryLoader.loadSignEntries(storageRoot, NO_GROUPS, GSON));

        assertFalse(Files.exists(Path.of(legacyPath)), "legacy file should be moved, not left in place");
        assertTrue(Files.exists(Path.of(legacyPath + ".migrated")), "legacy file should be backed up, not deleted");
    }

    @Test
    void migratesALegacyV1FileFabricatingPrefixFromThePOIGroup(@TempDir Path tempDir) throws IOException {
        var legacyPath = tempDir.resolve("signs.json").toString();
        var storageRoot = tempDir.resolve("storage");
        var poiGroup = new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI,
                "Points of Interest", null, 0, 0, false, 0.0, 10000000.0);

        // Already-namespaced dimension string: Version1SignEntryLoader's legacy-shorthand ("overworld"/"nether"/
        // "end") normalization branch reaches into net.minecraft.world.level.Level's static constants, which
        // require a running Minecraft bootstrap and aren't available under plain JUnit - not exercised here.
        writeLegacyV1File(legacyPath, "minecraft:overworld", "Town Hall");

        var result = LegacySignFileMigrator.migrate(legacyPath, storageRoot, new MarkerGroup[]{poiGroup}, GSON);

        assertEquals(1, result.size());
        var migrated = result.get(0);
        assertEquals("minecraft:overworld", migrated.key().parentMap());
        assertEquals("[poi]", migrated.frontText().prefix());
        assertEquals("Town Hall", migrated.frontText().label());
        assertTrue(Files.exists(Path.of(legacyPath + ".migrated")));
    }

    private static void writeLegacyV3File(String path, SignEntry... entries) throws IOException {
        var data = GSON.toJson(entries);
        var content = GSON.toJson(new VersionedSignFile(SignFileVersions.V3, data));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
    }

    private static void writeLegacyV1File(String path, String legacyDimension, String label) throws IOException {
        var entry = new SignEntryV2(
                new SignEntryKey(0, 64, 0, legacyDimension),
                "unknown",
                new SignLinesParseResultV2(MarkerTypeV2.POI, label, label),
                new SignLinesParseResultV2(null, "", ""));
        var content = GSON.toJson(new SignEntryV2[]{entry});
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
    }

    private static SignEntry signEntry(int x, int z, String dimension, String label) {
        return new SignEntry(
                new SignEntryKey(x, 64, z, dimension),
                "unknown",
                new SignLinesParseResult("[poi]", label, label),
                new SignLinesParseResult(null, "", ""));
    }
}
