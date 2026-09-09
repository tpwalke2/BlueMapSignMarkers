package com.tpwalke2.bluemapsignmarkers.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @Test
    void getReturnsTheConfigLoadedByTheMostRecentReload(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[custom]", "name": "Custom Group" }
                  ]
                }
                """);

        ConfigManager.reload(path);

        var group = ConfigManager.get().getMarkerGroups()[0];
        assertEquals("[custom]", group.prefix());
        assertEquals("Custom Group", group.name());
    }

    @Test
    void getFallsBackToDefaultsWhenTheConfiguredPathFailsToLoad(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, "{ this is not valid json");

        ConfigManager.reload(path);

        var config = ConfigManager.get();
        assertEquals(1, config.getMarkerGroups().length);
        assertEquals("[poi]", config.getMarkerGroups()[0].prefix());
    }

    @Test
    void reloadSwapsInAFreshlyLoadedConfig(@TempDir Path tempDir) throws IOException {
        var pathA = tempDir.resolve("a.json");
        Files.writeString(pathA, """
                { "markerGroups": [ { "prefix": "[a]", "name": "A" } ] }
                """);
        var pathB = tempDir.resolve("b.json");
        Files.writeString(pathB, """
                { "markerGroups": [ { "prefix": "[b]", "name": "B" } ] }
                """);

        ConfigManager.reload(pathA);
        assertEquals("[a]", ConfigManager.get().getMarkerGroups()[0].prefix());

        ConfigManager.reload(pathB);
        assertEquals("[b]", ConfigManager.get().getMarkerGroups()[0].prefix());
    }

    // Documents review finding #72 (agent-context/reviews/full-codebase-review_2026-09-07_0900.md): get()'s
    // "is coreConfig loaded yet?" check and its first load are now one synchronized step, so many threads
    // racing to call get() before anything has loaded converge on one canonical config instance instead of
    // each redundantly loading and racing over which instance wins. Same pattern as
    // MarkerSetIdentifierCollectionTest's concurrentFirstTimeCallersForTheSameComboConvergeOnOneIdentifierInstance.
    @Test
    void concurrentFirstTimeCallersConvergeOnOneConfigInstance(@TempDir Path tempDir) throws Exception {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                { "markerGroups": [ { "prefix": "[custom]", "name": "Custom Group" } ] }
                """);

        final int threadCount = 8;
        final int iterations = 500;

        var executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < iterations; i++) {
                ConfigManager.resetForTesting();
                var ready = new CountDownLatch(threadCount);
                var go = new CountDownLatch(1);
                var results = Collections.synchronizedList(new ArrayList<Object>());

                var futures = new ArrayList<Future<?>>();
                for (int t = 0; t < threadCount; t++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitUninterruptibly(go);
                        results.add(ConfigManager.get(path));
                    }));
                }

                assertTrue(ready.await(5, TimeUnit.SECONDS), "iteration " + i + ": a task failed to start in time");
                go.countDown();
                for (var future : futures) {
                    future.get(5, TimeUnit.SECONDS);
                }

                var distinctInstances = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
                distinctInstances.addAll(results);
                assertEquals(1, distinctInstances.size(),
                        "iteration " + i + ": concurrent first-time callers should converge on one config instance, got "
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
}
