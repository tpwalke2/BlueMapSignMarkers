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

    // v1-vs-v2 detection is structural (presence of "markerGroups"), not a substring search on the raw JSON
    // text, so a well-formed v2 config whose JSON just happens to contain the literal "poiPrefix" - e.g. inside
    // a marker group's name or icon - is no longer misdetected as a v1 file and collapsed to defaults.
    @Test
    void aWellFormedV2ConfigContainingTheSubstringPoiPrefixIsNotMisdetectedAsV1(
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
                "[custom]", group.prefix(),
                "the actual custom config should have been preserved, not collapsed to the default");
        assertEquals("poiPrefix mentioned here", group.name());
    }

    @Test
    void loadConfigRejectsAGroupWithAnEmptyPrefix(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "", "name": "Empty Prefix Group" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertNull(config);
    }

    @Test
    void loadConfigRejectsANonCompilingRegexPrefix(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[unterminated(", "matchType": "REGEX", "name": "Bad Regex Group" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertNull(config);
    }

    @Test
    void loadConfigRejectsAPrefixDuplicatedAcrossGroups(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[dupe]", "name": "First" },
                    { "prefix": "[dupe]", "name": "Second" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertNull(config);
    }

    @Test
    void loadConfigDefaultsLineWidthAndLineColorForALineGroupWhenOmitted(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.LINE, group.type());
        assertEquals(2, group.lineWidth());
        assertEquals("#FF0000FF", group.lineColor());
    }

    @Test
    void loadConfigPreservesExplicitLineWidthAndLineColorForALineGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE", "lineWidth": 5, "lineColor": "#00FF00FF" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(5, group.lineWidth());
        assertEquals("#00FF00FF", group.lineColor());
    }

    @Test
    void loadConfigStillLoadsAPOIGroupWithLineWidthAndLineColorSet(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "type": "POI", "lineWidth": 5, "lineColor": "#00FF00FF" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.POI, group.type());
        assertEquals("[poi]", group.prefix());
    }

    @Test
    void saveAndLoadConfigRoundTripNonAsciiMarkerGroupNamesThroughUtf8(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        var original = new com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV2(
                new com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup(
                        "[poi]",
                        MarkerGroupMatchType.STARTS_WITH,
                        MarkerGroupType.POI,
                        "地図マーカー éèà",
                        null,
                        0,
                        0,
                        false,
                        0.0,
                        10000000.0,
                        2,
                        "#FF0000FF"));

        ConfigProvider.saveConfig(original, path);
        var reloaded = ConfigProvider.loadConfig(path);

        assertEquals("地図マーカー éèà", reloaded.getMarkerGroups()[0].name());
    }
}
