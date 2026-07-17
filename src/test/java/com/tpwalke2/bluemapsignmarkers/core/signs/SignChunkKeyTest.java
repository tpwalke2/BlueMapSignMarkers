package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignChunkKeyTest {

    @Test
    void forEntryKeyAssignsChunkZeroAtOrigin() {
        var key = SignChunkKey.forEntryKey(new SignEntryKey(0, 64, 0, "minecraft:overworld"));

        assertEquals(new SignChunkKey("minecraft:overworld", 0, 0), key);
    }

    @Test
    void forEntryKeyUsesFloorDivisionForNegativeCoordinates() {
        var key = SignChunkKey.forEntryKey(new SignEntryKey(-1, 64, -1, "minecraft:overworld"));

        // Truncating division would put x=-1 in the same chunk as x=0 (both round toward zero); floorDiv
        // correctly puts it in the chunk to the west/north instead.
        assertEquals(-1, key.chunkX());
        assertEquals(-1, key.chunkZ());
    }

    @Test
    void forEntryKeySplitsAtChunkBoundary() {
        var lastBlockOfChunkZero = SignChunkKey.forEntryKey(new SignEntryKey(15, 64, 15, "minecraft:overworld"));
        var firstBlockOfChunkOne = SignChunkKey.forEntryKey(new SignEntryKey(16, 64, 16, "minecraft:overworld"));

        assertEquals(0, lastBlockOfChunkZero.chunkX());
        assertEquals(0, lastBlockOfChunkZero.chunkZ());
        assertEquals(1, firstBlockOfChunkOne.chunkX());
        assertEquals(1, firstBlockOfChunkOne.chunkZ());
    }

    @Test
    void forEntryKeyKeepsParentMapUnchanged() {
        var key = SignChunkKey.forEntryKey(new SignEntryKey(0, 64, 0, "minecraft:the_nether"));

        assertEquals("minecraft:the_nether", key.parentMap());
    }
}
