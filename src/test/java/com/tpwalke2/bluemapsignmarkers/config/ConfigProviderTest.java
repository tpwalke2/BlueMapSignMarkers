package com.tpwalke2.bluemapsignmarkers.config;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV2;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigProviderTest {

    // Captures the log messages the mod's logger emits while running action (and stashes action's result
    // into result[0]), so tests can assert that warnOnTypeFieldMismatches actually logs a warning rather
    // than only checking the config still loads.
    private static List<String> captureWarnMessages(Supplier<BMSMConfigV2> action, BMSMConfigV2[] result) {
        var logger = (Logger) LogManager.getLogger(Constants.MOD_ID);
        var messages = new ArrayList<String>();
        var appender = new AbstractAppender("test-capture", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            result[0] = action.get();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        return messages;
    }

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
    void loadConfigFallsBackToDefaultLineWidthWhenNonPositive(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE", "lineWidth": 0 }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(2, group.lineWidth());
    }

    @Test
    void loadConfigFallsBackToDefaultLineWidthWhenNegative(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE", "lineWidth": -5 }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(2, group.lineWidth());
    }

    @Test
    void loadConfigFallsBackToDefaultLineColorWhenMalformed(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE", "lineColor": "notacolor" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals("#FF0000FF", group.lineColor());
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

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.POI, group.type());
        assertEquals("[poi]", group.prefix());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("lineWidth")));
        assertTrue(warnings.stream().anyMatch(m -> m.contains("lineColor")));
    }

    @Test
    void loadConfigDoesNotValidateLineWidthOrLineColorForAPOIGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "type": "POI", "lineWidth": -5, "lineColor": "notacolor" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.POI, group.type());
        assertEquals(-5, group.lineWidth());
        assertEquals("notacolor", group.lineColor());
    }

    @Test
    void loadConfigDefaultsFillColorForAShapeGroupWhenOmitted(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[shape]", "name": "Shape Group", "type": "SHAPE" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.SHAPE, group.type());
        assertEquals("#FF000033", group.fillColor());
    }

    @Test
    void loadConfigPreservesExplicitFillColorForAShapeGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[shape]", "name": "Shape Group", "type": "SHAPE", "fillColor": "#00FF0080" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals("#00FF0080", group.fillColor());
    }

    @Test
    void loadConfigFallsBackToDefaultFillColorWhenMalformed(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[shape]", "name": "Shape Group", "type": "SHAPE", "fillColor": "notacolor" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals("#FF000033", group.fillColor());
    }

    @Test
    void loadConfigAllowsExplicitLineWidthAndLineColorOnAShapeGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[shape]", "name": "Shape Group", "type": "SHAPE", "lineWidth": 5, "lineColor": "#00FF00FF" }
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
    void loadConfigFallsBackToDefaultLineWidthAndLineColorForAShapeGroupWhenMalformed(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[shape]", "name": "Shape Group", "type": "SHAPE", "lineWidth": -5, "lineColor": "notacolor" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(2, group.lineWidth());
        assertEquals("#FF0000FF", group.lineColor());
    }

    @Test
    void loadConfigStillLoadsAPOIGroupWithFillColorSet(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "type": "POI", "fillColor": "#00FF00FF" }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.POI, group.type());
        assertEquals("#00FF00FF", group.fillColor());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("fillColor")));
    }

    @Test
    void loadConfigStillLoadsALineGroupWithFillColorSet(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE", "fillColor": "#00FF00FF" }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.LINE, group.type());
        assertEquals("#00FF00FF", group.fillColor());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("fillColor")));
    }

    @Test
    void loadConfigStillLoadsAShapeGroupWithIconAndOffsetsSet(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[shape]", "name": "Shape Group", "type": "SHAPE", "icon": "icon.png", "offsetX": 1, "offsetY": 2 }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(MarkerGroupType.SHAPE, group.type());
        assertEquals("icon.png", group.icon());
        assertEquals(1, group.offsetX());
        assertEquals(2, group.offsetY());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("icon")));
        assertTrue(warnings.stream().anyMatch(m -> m.contains("offsetX")));
        assertTrue(warnings.stream().anyMatch(m -> m.contains("offsetY")));
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
                        "#FF0000FF",
                        "#FF000033",
                        0,
                        true,
                        true,
                        List.of()));

        ConfigProvider.saveConfig(original, path);
        var reloaded = ConfigProvider.loadConfig(path);

        assertEquals("地図マーカー éèà", reloaded.getMarkerGroups()[0].name());
    }

    @Test
    void loadConfigDefaultsSortingToggleableDepthTestAndCssClassesWhenOmitted(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group" }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(0, group.sorting());
        assertTrue(group.toggleable());
        assertTrue(group.depthTest());
        assertTrue(group.cssClasses().isEmpty());
    }

    @Test
    void loadConfigPreservesExplicitSortingToggleableDepthTestAndCssClasses(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE", "sorting": 5, "toggleable": false, "depthTest": false }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(5, group.sorting());
        assertFalse(group.toggleable());
        assertFalse(group.depthTest());
    }

    @Test
    void loadConfigPreservesExplicitCssClassesOnAPOIGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "cssClasses": ["custom-poi", "highlight"] }
                  ]
                }
                """);

        var config = ConfigProvider.loadConfig(path);

        assertEquals(1, config.getMarkerGroups().length);
        var group = config.getMarkerGroups()[0];
        assertEquals(List.of("custom-poi", "highlight"), group.cssClasses());
    }

    @Test
    void loadConfigDropsNullEntriesInCssClasses(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "cssClasses": ["custom-poi", null] }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        assertEquals(List.of("custom-poi"), config.getMarkerGroups()[0].cssClasses());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("cssClasses")));
    }

    @Test
    void loadConfigFallsBackToDefaultSortingWhenMalformed(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "sorting": "notanumber" }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        assertEquals(0, config.getMarkerGroups()[0].sorting());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("sorting")));
    }

    @Test
    void loadConfigFallsBackToDefaultSortingWhenAnArray(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "sorting": [1, 2] }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        assertEquals(0, config.getMarkerGroups()[0].sorting());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("sorting")));
    }

    @Test
    void loadConfigFallsBackToDefaultSortingWhenOutOfIntRange(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "sorting": 99999999999999999999999999 }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        assertEquals(0, config.getMarkerGroups()[0].sorting());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("sorting")));
    }

    @Test
    void loadConfigWarnsWhenDepthTestIsSetOnAPOIGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[poi]", "name": "POI Group", "type": "POI", "depthTest": false }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        assertTrue(warnings.stream().anyMatch(m -> m.contains("depthTest")));
    }

    @Test
    void loadConfigWarnsWhenCssClassesIsSetOnALineGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[line]", "name": "Line Group", "type": "LINE", "cssClasses": ["ignored"] }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        assertTrue(warnings.stream().anyMatch(m -> m.contains("cssClasses")));
    }

    @Test
    void loadConfigWarnsWhenCssClassesIsSetOnAShapeGroup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("BMSM-Core.json");
        Files.writeString(path, """
                {
                  "markerGroups": [
                    { "prefix": "[shape]", "name": "Shape Group", "type": "SHAPE", "cssClasses": ["ignored"] }
                  ]
                }
                """);

        var result = new BMSMConfigV2[1];
        var warnings = captureWarnMessages(() -> ConfigProvider.loadConfig(path), result);
        var config = result[0];

        assertEquals(1, config.getMarkerGroups().length);
        assertTrue(warnings.stream().anyMatch(m -> m.contains("cssClasses")));
    }
}
