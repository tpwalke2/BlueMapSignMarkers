package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignChunkIndexTest {

    @Test
    void keysInChunkReturnsEmptyWhenNothingTracked() {
        var index = new SignChunkIndex();

        assertEquals(0, index.keysInChunk("minecraft:overworld", 0, 0).size());
    }

    @Test
    void addMakesKeyFindableByItsChunk() {
        var index = new SignChunkIndex();
        var key = new SignEntryKey(1, 64, 1, "minecraft:overworld");

        index.add(key);

        assertEquals(1, index.keysInChunk("minecraft:overworld", 0, 0).size());
        assertTrue(index.keysInChunk("minecraft:overworld", 0, 0).contains(key));
    }

    @Test
    void addTracksMultipleKeysInTheSameChunk() {
        var index = new SignChunkIndex();
        var first = new SignEntryKey(1, 64, 1, "minecraft:overworld");
        var second = new SignEntryKey(2, 65, 2, "minecraft:overworld");

        index.add(first);
        index.add(second);

        var keys = index.keysInChunk("minecraft:overworld", 0, 0);
        assertEquals(2, keys.size());
        assertTrue(keys.contains(first));
        assertTrue(keys.contains(second));
    }

    @Test
    void keysInDifferentChunksStayIsolated() {
        var index = new SignChunkIndex();
        var nearby = new SignEntryKey(1, 64, 1, "minecraft:overworld");
        var farAway = new SignEntryKey(600, 64, 600, "minecraft:overworld");

        index.add(nearby);
        index.add(farAway);

        var nearbyChunk = index.keysInChunk("minecraft:overworld", 0, 0);
        var farAwayChunk = index.keysInChunk("minecraft:overworld", 37, 37);
        assertEquals(1, nearbyChunk.size());
        assertTrue(nearbyChunk.contains(nearby));
        assertEquals(1, farAwayChunk.size());
        assertTrue(farAwayChunk.contains(farAway));
    }

    @Test
    void keysInDifferentDimensionsAtTheSameCoordinatesStayIsolated() {
        var index = new SignChunkIndex();
        var overworldKey = new SignEntryKey(1, 64, 1, "minecraft:overworld");
        var netherKey = new SignEntryKey(1, 64, 1, "minecraft:the_nether");

        index.add(overworldKey);
        index.add(netherKey);

        assertEquals(1, index.keysInChunk("minecraft:overworld", 0, 0).size());
        assertEquals(1, index.keysInChunk("minecraft:the_nether", 0, 0).size());
    }

    @Test
    void removeDropsKeyFromItsChunk() {
        var index = new SignChunkIndex();
        var key = new SignEntryKey(1, 64, 1, "minecraft:overworld");
        index.add(key);

        index.remove(key);

        assertEquals(0, index.keysInChunk("minecraft:overworld", 0, 0).size());
    }

    @Test
    void removeLeavesOtherKeysInTheSameChunkUntouched() {
        var index = new SignChunkIndex();
        var first = new SignEntryKey(1, 64, 1, "minecraft:overworld");
        var second = new SignEntryKey(2, 65, 2, "minecraft:overworld");
        index.add(first);
        index.add(second);

        index.remove(first);

        var keys = index.keysInChunk("minecraft:overworld", 0, 0);
        assertEquals(1, keys.size());
        assertTrue(keys.contains(second));
    }

    @Test
    void removeOfUntrackedKeyIsANoOp() {
        var index = new SignChunkIndex();
        var key = new SignEntryKey(1, 64, 1, "minecraft:overworld");

        index.remove(key);

        assertEquals(0, index.keysInChunk("minecraft:overworld", 0, 0).size());
    }

    @Test
    void clearRemovesEverything() {
        var index = new SignChunkIndex();
        index.add(new SignEntryKey(1, 64, 1, "minecraft:overworld"));
        index.add(new SignEntryKey(600, 64, 600, "minecraft:overworld"));

        index.clear();

        assertEquals(0, index.keysInChunk("minecraft:overworld", 0, 0).size());
        assertEquals(0, index.keysInChunk("minecraft:overworld", 37, 37).size());
    }
}
