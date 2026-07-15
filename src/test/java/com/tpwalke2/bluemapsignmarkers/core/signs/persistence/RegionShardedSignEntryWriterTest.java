package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
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

class RegionShardedSignEntryWriterTest {

    private static final Gson GSON = new Gson();

    @Test
    void writesEachRegionToItsOwnFile(@TempDir Path storageRoot) throws IOException {
        var overworldEntry = signEntry(0, 0, "minecraft:overworld", "Town Hall");
        var netherEntry = signEntry(0, 0, "minecraft:the_nether", "Portal Hub");

        RegionShardedSignEntryWriter.write(storageRoot, List.of(overworldEntry, netherEntry), GSON);

        var overworldFile = storageRoot.resolve("minecraft").resolve("overworld").resolve("r.0.0.json");
        var netherFile = storageRoot.resolve("minecraft").resolve("the_nether").resolve("r.0.0.json");
        assertTrue(Files.exists(overworldFile));
        assertTrue(Files.exists(netherFile));

        var versionedFile = GSON.fromJson(Files.readString(overworldFile, StandardCharsets.UTF_8), VersionedSignFile.class);
        assertEquals(SignFileVersions.V3, versionedFile.version());

        var entries = GSON.fromJson(versionedFile.data(), SignEntry[].class);
        assertEquals(1, entries.length);
        assertEquals("Town Hall", entries[0].frontText().label());
    }

    @Test
    void quarantinesStaleRegionFilesNotInCurrentSave(@TempDir Path storageRoot) throws IOException {
        RegionShardedSignEntryWriter.write(
                storageRoot, List.of(signEntry(0, 0, "minecraft:overworld", "Town Hall")), GSON);
        var staleFile = storageRoot.resolve("minecraft").resolve("overworld").resolve("r.0.0.json");
        assertTrue(Files.exists(staleFile));

        // Re-save with that sign gone (e.g. removed) - the region file should move aside, not
        // vanish outright: it may be gone because it was removed, or because it failed to load.
        RegionShardedSignEntryWriter.write(storageRoot, List.of(), GSON);

        assertFalse(Files.exists(staleFile));
        assertTrue(Files.exists(staleFile.resolveSibling(staleFile.getFileName() + ".stale")));
    }

    @Test
    void movingASignToAnotherRegionQuarantinesTheOldRegionFile(@TempDir Path storageRoot) throws IOException {
        RegionShardedSignEntryWriter.write(
                storageRoot, List.of(signEntry(0, 0, "minecraft:overworld", "Town Hall")), GSON);

        RegionShardedSignEntryWriter.write(
                storageRoot, List.of(signEntry(600, 600, "minecraft:overworld", "Town Hall")), GSON);

        var oldRegionFile = storageRoot.resolve("minecraft").resolve("overworld").resolve("r.0.0.json");
        var newRegionFile = storageRoot.resolve("minecraft").resolve("overworld").resolve("r.1.1.json");
        assertFalse(Files.exists(oldRegionFile));
        assertTrue(Files.exists(oldRegionFile.resolveSibling(oldRegionFile.getFileName() + ".stale")));
        assertTrue(Files.exists(newRegionFile));
    }

    private static SignEntry signEntry(int x, int z, String dimension, String label) {
        return new SignEntry(
                new SignEntryKey(x, 64, z, dimension),
                "unknown",
                new SignLinesParseResult("[poi]", label, label),
                new SignLinesParseResult(null, "", ""));
    }
}
