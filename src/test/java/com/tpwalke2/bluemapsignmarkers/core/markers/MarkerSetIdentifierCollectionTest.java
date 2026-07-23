package com.tpwalke2.bluemapsignmarkers.core.markers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerSetIdentifierCollectionTest {

    @Test
    void getIdentifierReturnsTheSameInstanceForARepeatedCombo() {
        var collection = new MarkerSetIdentifierCollection();
        var group = markerGroup("[poi]");

        var first = collection.getIdentifier("world", group);
        var second = collection.getIdentifier("world", group);

        assertSame(first, second);
    }

    @Test
    void getIdentifierReturnsDistinctIdentifiersForDifferentMapIds() {
        var collection = new MarkerSetIdentifierCollection();
        var group = markerGroup("[poi]");

        var overworld = collection.getIdentifier("world", group);
        var nether = collection.getIdentifier("world_nether", group);

        assertNotEquals(overworld, nether);
    }

    @Test
    void getIdentifierReturnsDistinctIdentifiersForDifferentMarkerGroups() {
        var collection = new MarkerSetIdentifierCollection();

        var poi = collection.getIdentifier("world", markerGroup("[poi]"));
        var town = collection.getIdentifier("world", markerGroup("[town]"));

        assertNotEquals(poi, town);
    }

    @Test
    void getIdentifierMapIdMatchingIsCaseInsensitive() {
        var collection = new MarkerSetIdentifierCollection();
        var group = markerGroup("[poi]");

        var lower = collection.getIdentifier("world", group);
        var mixedCase = collection.getIdentifier("World", group);

        assertSame(lower, mixedCase);
    }

    // Documents review finding #16 (plans/codebase-review-2026-07-11.md): getIdentifier()'s cache check
    // ("is there already an identifier for this combo?") and its cache write (addIdentifier) are now one
    // synchronized step, so many threads racing to look up the same brand-new (mapId, markerGroup) combo for
    // the first time converge on one canonical instance instead of each constructing their own.
    @Test
    void concurrentFirstTimeCallersForTheSameComboConvergeOnOneIdentifierInstance() throws Exception {
        final int threadCount = 8;
        final int iterations = 500;
        var group = markerGroup("[poi]");

        var executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < iterations; i++) {
                var collection = new MarkerSetIdentifierCollection();
                var ready = new CountDownLatch(threadCount);
                var go = new CountDownLatch(1);
                var results = Collections.synchronizedList(new ArrayList<MarkerSetIdentifier>());

                var futures = new ArrayList<Future<?>>();
                for (int t = 0; t < threadCount; t++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitUninterruptibly(go);
                        results.add(collection.getIdentifier("world", group));
                    }));
                }

                assertTrue(ready.await(5, TimeUnit.SECONDS), "iteration " + i + ": a task failed to start in time");
                go.countDown();
                for (var future : futures) {
                    future.get(5, TimeUnit.SECONDS);
                }

                var distinctInstances = Collections.newSetFromMap(new IdentityHashMap<MarkerSetIdentifier, Boolean>());
                distinctInstances.addAll(results);
                assertEquals(1, distinctInstances.size(),
                        "iteration " + i + ": concurrent first-time callers should converge on one identifier instance, got "
                                + distinctInstances.size());
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

    private static MarkerGroup markerGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, prefix, "icon.png", 0, 0, false, 0, 0);
    }
}
