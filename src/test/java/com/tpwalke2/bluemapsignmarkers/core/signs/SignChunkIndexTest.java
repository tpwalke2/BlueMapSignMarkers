package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    // Regression test for a race where removing the last key from a chunk could delete the chunk's map entry
    // after another thread had already re-added a different key to that same (still-live) Set instance, orphaning
    // the newly-added key. Runs many iterations with both operations started simultaneously (via a latch) to
    // exercise every possible interleaving between the remove-to-empty and the concurrent add.
    @Test
    void concurrentAddDuringRemovalOfTheLastKeyIsNeverLost() throws Exception {
        var chunkA = new SignEntryKey(1, 64, 1, "minecraft:overworld");
        var chunkB = new SignEntryKey(2, 65, 2, "minecraft:overworld");

        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 3000; i++) {
                var index = new SignChunkIndex();
                index.add(chunkA);

                var ready = new CountDownLatch(2);
                var go = new CountDownLatch(1);

                var removeTask = executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(go);
                    index.remove(chunkA);
                });
                var addTask = executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(go);
                    index.add(chunkB);
                });

                assertTrue(ready.await(5, TimeUnit.SECONDS), "iteration " + i + ": a task failed to start in time");
                go.countDown();
                removeTask.get(5, TimeUnit.SECONDS);
                addTask.get(5, TimeUnit.SECONDS);

                var keys = index.keysInChunk("minecraft:overworld", 0, 0);
                assertTrue(keys.contains(chunkB), "iteration " + i + ": concurrently-added key was lost");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
