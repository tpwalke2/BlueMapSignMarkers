package com.tpwalke2.bluemapsignmarkers.config;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigProviderTest {

    @Test
    void loadConfigCreatesAndPersistsDefaultsWhenFileIsAbsent(@TempDir Path tempDir) {
        var path = tempDir.resolve("BMSM-Core.json");

        var config = ConfigProvider.loadConfig(path);

        assertTrue(Files.exists(path), "defaults should have been persisted to disk");
        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals("[poi]", group.prefix());
        assertEquals(MarkerGroupMatchType.STARTS_WITH, group.matchType());
        assertEquals(MarkerGroupType.POI, group.type());
    }

    @Test
    void loadConfigNullCoalescesMissingOptionalFieldsInAWellFormedV2File(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[test]", "name": "Test Group" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals("[test]", group.prefix());
        assertEquals("Test Group", group.name());
        assertEquals(MarkerGroupMatchType.STARTS_WITH, group.matchType());
        assertEquals(MarkerGroupType.POI, group.type());
        assertEquals(0, group.offsetX());
        assertEquals(0, group.offsetY());
        assertFalse(group.defaultHidden());
        assertEquals(0.0, group.minDistance());
        assertEquals(10000000.0, group.maxDistance());
    }

    @Test
    void loadConfigReturnsNullForMalformedJsonRatherThanThrowing(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, "{ this is not valid json");

        var config = ConfigProvider.loadConfig(path);

        assertNull(config);
    }

    @Test
    void loadConfigMigratesAV1FileToASingleV2POIGroupAndBacksUpTheOriginal(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                { "poiPrefix": "[legacy]" }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals("[legacy]", group.prefix());
        assertEquals(MarkerGroupMatchType.STARTS_WITH, group.matchType());
        assertEquals(MarkerGroupType.POI, group.type());
        assertEquals("Points of Interest", group.name());
        assertTrue(
                Files.exists(tempDir.resolve("BMSM-Core.json.v1.bak")),
                "original v1 file should have been backed up");
    }

    // Documents review finding #9: v1-vs-v2 detection is a substring check on the raw JSON text, not a
    // structural one. A well-formed v2 config whose JSON just happens to contain the literal "poiPrefix" -
    // e.g. inside a marker group's name or icon - is misdetected as a v1 file, parsed against BMSMConfigV1
    // (which has no field matching that content), and silently collapsed to the single default POI group,
    // discarding whatever the user had actually configured.
    @Test
    void aWellFormedV2ConfigContainingTheSubstringPoiPrefixIsMisdetectedAsV1AndCollapsedToDefaults(
            @TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[custom]", "name": "poiPrefix mentioned here" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(
                "[poi]", group.prefix(),
                "the actual custom config should have been preserved, not collapsed to the default");
        assertEquals("Points of Interest", group.name());
    }
}
