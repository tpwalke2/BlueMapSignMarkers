package com.tpwalke2.bluemapsignmarkers.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
