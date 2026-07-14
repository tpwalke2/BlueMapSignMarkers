package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignRegionPartitionerTest {

    @Test
    void groupsEntriesByRegionAndDimension() {
        var overworldEntry = signEntry(0, 0, "minecraft:overworld");
        var netherEntry = signEntry(0, 0, "minecraft:the_nether");
        var farOverworldEntry = signEntry(600, 600, "minecraft:overworld");

        var partitions = SignRegionPartitioner.partition(List.of(overworldEntry, netherEntry, farOverworldEntry));

        assertEquals(3, partitions.size());
        assertTrue(partitions.get(new SignRegionKey("minecraft:overworld", 0, 0)).contains(overworldEntry));
        assertTrue(partitions.get(new SignRegionKey("minecraft:the_nether", 0, 0)).contains(netherEntry));
        assertTrue(partitions.get(new SignRegionKey("minecraft:overworld", 1, 1)).contains(farOverworldEntry));
    }

    @Test
    void groupsMultipleEntriesInSameRegionTogether() {
        var first = signEntry(1, 1, "minecraft:overworld");
        var second = signEntry(2, 2, "minecraft:overworld");

        var partitions = SignRegionPartitioner.partition(List.of(first, second));

        assertEquals(1, partitions.size());
        var region = partitions.get(new SignRegionKey("minecraft:overworld", 0, 0));
        assertEquals(2, region.size());
    }

    @Test
    void emptyInputProducesNoPartitions() {
        var partitions = SignRegionPartitioner.partition(List.of());

        assertEquals(0, partitions.size());
    }

    private static SignEntry signEntry(int x, int z, String dimension) {
        return new SignEntry(
                new SignEntryKey(x, 64, z, dimension),
                "unknown",
                new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""));
    }
}
