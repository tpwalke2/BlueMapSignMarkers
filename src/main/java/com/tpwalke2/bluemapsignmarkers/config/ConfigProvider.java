package com.tpwalke2.bluemapsignmarkers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.ColorUtils;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV1;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV2;
import com.tpwalke2.bluemapsignmarkers.config.persistence.LoadingBMSMConfigV2;
import com.tpwalke2.bluemapsignmarkers.config.persistence.LoadingMarkerGroupV2;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ConfigProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .setPrettyPrinting()
            .create();

    private ConfigProvider() {}

    private static Path getConfigPath() {
        return Path.of("config", Constants.MOD_ID, "BMSM-Core.json");
    }

    public static void saveConfig(BMSMConfigV2 config) {
        saveConfig(config, getConfigPath());
    }

    // Visible for testing: lets tests point saveConfig at a temp-directory path instead of the hardcoded
    // config/<mod-id>/BMSM-Core.json path resolved relative to the process's working directory.
    static void saveConfig(BMSMConfigV2 config, Path path) {
        LOGGER.info("Saving config to file: {}...", path);

        var file = path.toFile();
        var parent = file.getParentFile();
        if (!parent.exists()) {
            try {
                Files.createDirectories(Paths.get(parent.getAbsolutePath()));
            } catch (IOException e) {
                LOGGER.error("Failed to create parent directories for config", e);
                return;
            }
        }

        try (var writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public static BMSMConfigV2 loadConfig() {
        return loadConfig(getConfigPath());
    }

    // Visible for testing: lets tests point loadConfig at a temp-directory path instead of the hardcoded
    // config/<mod-id>/BMSM-Core.json path resolved relative to the process's working directory.
    static BMSMConfigV2 loadConfig(Path configPath) {
        var file = configPath.toFile();

        LOGGER.info("Loading config from file: {}...", file);

        if (!file.exists()) {
            LOGGER.info("Config file does not yet exist, creating defaults...");
            var result = new BMSMConfigV2();
            saveConfig(result, configPath);
            return result;
        }

        String configContent;
        try {
            configContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to read config file", e);
            return null;
        }

        try {
            var root = GSON.fromJson(configContent, JsonObject.class);

            // A v1 config's shape is a bare { "poiPrefix": "..." } object - it never has a "markerGroups"
            // field. Detecting v1 by that shape (rather than a substring search on the raw file text) means a
            // v2 config whose group name/icon happens to contain the literal text "poiPrefix" is no longer
            // misdetected and doesn't get its real marker groups silently overwritten with a single default.
            if (root != null && root.has("poiPrefix") && !root.has("markerGroups")) {
                var v1Config = GSON.fromJson(configContent, BMSMConfigV1.class);
                var migratedConfig = loadV1Config(file, v1Config);
                saveConfig(migratedConfig, configPath);
                return migratedConfig;
            }

            // v2 attempt
            var result = GSON.fromJson(configContent, LoadingBMSMConfigV2.class);

            var loadingMarkerGroups = result.getMarkerGroups();
            var markerGroups = Arrays
                    .stream(loadingMarkerGroups)
                    .map(ConfigProvider::convertToLoadedMarkerGroup)
                    .toArray(MarkerGroup[]::new);

            validateMarkerGroups(markerGroups);
            warnOnTypeFieldMismatches(loadingMarkerGroups);

            return new BMSMConfigV2(markerGroups);

        } catch (Exception e) {
            LOGGER.error("Failed to load config:", e);
            return null;
        }
    }

    private static void validateMarkerGroups(MarkerGroup[] markerGroups) {
        var seenPrefixes = new HashSet<String>();

        for (var markerGroup : markerGroups) {
            var prefix = markerGroup.prefix();

            if (prefix == null || prefix.isEmpty()) {
                throw new IllegalArgumentException(
                        "Marker group '" + markerGroup.name() + "' has an empty prefix");
            }

            if (markerGroup.matchType() == MarkerGroupMatchType.REGEX) {
                try {
                    Pattern.compile(prefix);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException(
                            "Marker group '" + markerGroup.name() + "' has a REGEX prefix that doesn't compile: "
                                    + prefix, e);
                }
            }

            if (!seenPrefixes.add(prefix)) {
                throw new IllegalArgumentException(
                        "Marker group '" + markerGroup.name() + "' has a prefix duplicated across groups: "
                                + prefix);
            }
        }
    }

    // Centralizes the "null type defaults to POI" rule so the three call sites that need the effective
    // type (warnOnTypeFieldMismatches, resolveLineWidth, resolveLineColor) can't drift out of sync.
    private static MarkerGroupType effectiveType(LoadingMarkerGroupV2 markerGroup) {
        return markerGroup.type() == null ? MarkerGroupType.POI : markerGroup.type();
    }

    private static void warnOnTypeFieldMismatches(LoadingMarkerGroupV2[] markerGroups) {
        for (var markerGroup : markerGroups) {
            var type = effectiveType(markerGroup);
            var name = markerGroup.name();

            if (type == MarkerGroupType.POI) {
                if (markerGroup.lineWidth() != null) {
                    LOGGER.warn("Marker group '{}' is type POI but has 'lineWidth' set; this field is ignored for POI groups", name);
                }
                if (markerGroup.lineColor() != null) {
                    LOGGER.warn("Marker group '{}' is type POI but has 'lineColor' set; this field is ignored for POI groups", name);
                }
                if (markerGroup.fillColor() != null) {
                    LOGGER.warn("Marker group '{}' is type POI but has 'fillColor' set; this field is ignored for POI groups", name);
                }
                if (markerGroup.depthTest() != null) {
                    LOGGER.warn("Marker group '{}' is type POI but has 'depthTest' set; this field is ignored for POI groups", name);
                }
            } else if (type == MarkerGroupType.LINE) {
                if (markerGroup.icon() != null) {
                    LOGGER.warn("Marker group '{}' is type LINE but has 'icon' set; this field is ignored for LINE groups", name);
                }
                if (markerGroup.offsetX() != null) {
                    LOGGER.warn("Marker group '{}' is type LINE but has 'offsetX' set; this field is ignored for LINE groups", name);
                }
                if (markerGroup.offsetY() != null) {
                    LOGGER.warn("Marker group '{}' is type LINE but has 'offsetY' set; this field is ignored for LINE groups", name);
                }
                if (markerGroup.fillColor() != null) {
                    LOGGER.warn("Marker group '{}' is type LINE but has 'fillColor' set; this field is ignored for LINE groups", name);
                }
                if (markerGroup.cssClasses() != null && !markerGroup.cssClasses().isEmpty()) {
                    LOGGER.warn("Marker group '{}' is type LINE but has 'cssClasses' set; this field is ignored for LINE groups", name);
                }
            } else if (type == MarkerGroupType.SHAPE) {
                if (markerGroup.icon() != null) {
                    LOGGER.warn("Marker group '{}' is type SHAPE but has 'icon' set; this field is ignored for SHAPE groups", name);
                }
                if (markerGroup.offsetX() != null) {
                    LOGGER.warn("Marker group '{}' is type SHAPE but has 'offsetX' set; this field is ignored for SHAPE groups", name);
                }
                if (markerGroup.offsetY() != null) {
                    LOGGER.warn("Marker group '{}' is type SHAPE but has 'offsetY' set; this field is ignored for SHAPE groups", name);
                }
                if (markerGroup.cssClasses() != null && !markerGroup.cssClasses().isEmpty()) {
                    LOGGER.warn("Marker group '{}' is type SHAPE but has 'cssClasses' set; this field is ignored for SHAPE groups", name);
                }
            }
        }
    }

    private static final int DEFAULT_LINE_WIDTH = 2;

    private static MarkerGroup convertToLoadedMarkerGroup(LoadingMarkerGroupV2 markerGroup) {
        return new MarkerGroup(
                markerGroup.prefix(),
                markerGroup.matchType() == null ? MarkerGroupMatchType.STARTS_WITH : markerGroup.matchType(),
                effectiveType(markerGroup),
                markerGroup.name(),
                markerGroup.icon(),
                markerGroup.offsetX() == null ? 0 : markerGroup.offsetX(),
                markerGroup.offsetY() == null ? 0 : markerGroup.offsetY(),
                markerGroup.defaultHidden() != null && markerGroup.defaultHidden(),
                markerGroup.minDistance() == null ? 0.0 : markerGroup.minDistance(),
                markerGroup.maxDistance() == null ? 10000000.0 : markerGroup.maxDistance(),
                resolveLineWidth(markerGroup),
                resolveLineColor(markerGroup),
                resolveFillColor(markerGroup),
                resolveSorting(markerGroup),
                resolveToggleable(markerGroup),
                resolveDepthTest(markerGroup),
                resolveCssClasses(markerGroup)
        );
    }

    private static final int DEFAULT_SORTING = 0;

    // sorting is read as a raw JsonElement (see LoadingMarkerGroupV2) specifically so a non-integer value
    // (e.g. a string) falls back here with a warning instead of failing Gson's parse for the entire config.
    private static int resolveSorting(LoadingMarkerGroupV2 markerGroup) {
        var sorting = markerGroup.sorting();
        if (sorting == null || sorting.isJsonNull()) return DEFAULT_SORTING;

        try {
            return sorting.getAsBigDecimal().intValueExact();
        } catch (RuntimeException e) {
            LOGGER.warn("Marker group '{}' has a malformed 'sorting' ({}); falling back to default {}",
                    markerGroup.name(), sorting, DEFAULT_SORTING);
            return DEFAULT_SORTING;
        }
    }

    private static boolean resolveToggleable(LoadingMarkerGroupV2 markerGroup) {
        var toggleable = markerGroup.toggleable();
        return toggleable == null || toggleable;
    }

    // depthTest is LINE/SHAPE-only (POI markers are always billboarded on top); unset or set on a POI group
    // both resolve to the BlueMap default of true - warnOnTypeFieldMismatches already warns on the POI case.
    private static boolean resolveDepthTest(LoadingMarkerGroupV2 markerGroup) {
        var depthTest = markerGroup.depthTest();
        if (depthTest == null) return true;

        var type = effectiveType(markerGroup);
        if (type != MarkerGroupType.LINE && type != MarkerGroupType.SHAPE) return true;

        return depthTest;
    }

    // cssClasses is POI-only; unset or set on a LINE/SHAPE group both resolve to an empty list -
    // warnOnTypeFieldMismatches already warns on the LINE/SHAPE case.
    private static List<String> resolveCssClasses(LoadingMarkerGroupV2 markerGroup) {
        var cssClasses = markerGroup.cssClasses();
        if (cssClasses == null) return List.of();

        if (effectiveType(markerGroup) != MarkerGroupType.POI) return List.of();

        var filtered = cssClasses.stream().filter(Objects::nonNull).toList();
        if (filtered.size() != cssClasses.size()) {
            LOGGER.warn("Marker group '{}' has null entries in 'cssClasses'; dropping them", markerGroup.name());
        }
        return filtered;
    }

    // Falls back to the default width on a non-positive value rather than throwing - a bad config value
    // must not crash the server (same treatment as ColorUtils.parseHex's fallback on a malformed color).
    // Only validated for LINE/SHAPE groups - lineWidth is ignored for POI groups (warnOnTypeFieldMismatches
    // already warns about it being set), so validating it here too would just be a second, confusing warning.
    private static int resolveLineWidth(LoadingMarkerGroupV2 markerGroup) {
        var lineWidth = markerGroup.lineWidth();
        if (lineWidth == null) return DEFAULT_LINE_WIDTH;

        var type = effectiveType(markerGroup);
        if (type != MarkerGroupType.LINE && type != MarkerGroupType.SHAPE) return lineWidth;

        if (lineWidth <= 0) {
            LOGGER.warn("Marker group '{}' has a non-positive 'lineWidth' ({}); falling back to default {}",
                    markerGroup.name(), lineWidth, DEFAULT_LINE_WIDTH);
            return DEFAULT_LINE_WIDTH;
        }

        return lineWidth;
    }

    // Warns and falls back to the default color at load time (rather than silently at dispatch time via
    // ColorUtils.parseHex's fallback) so a malformed color gets a clear, attributable log message.
    // Only validated for LINE/SHAPE groups - see resolveLineWidth above.
    private static final String DEFAULT_LINE_COLOR = "#FF0000FF";

    private static String resolveLineColor(LoadingMarkerGroupV2 markerGroup) {
        var lineColor = markerGroup.lineColor();
        if (lineColor == null) return DEFAULT_LINE_COLOR;

        var type = effectiveType(markerGroup);
        if (type != MarkerGroupType.LINE && type != MarkerGroupType.SHAPE) return lineColor;

        if (!ColorUtils.isValidHex(lineColor)) {
            LOGGER.warn("Marker group '{}' has a malformed 'lineColor' ({}); falling back to default {}",
                    markerGroup.name(), lineColor, DEFAULT_LINE_COLOR);
            return DEFAULT_LINE_COLOR;
        }

        return lineColor;
    }

    // fillColor is SHAPE-only; its default is translucent (unlike lineColor's opaque default) so a SHAPE
    // group configured without styling doesn't blot out the map underneath it. Only validated for SHAPE
    // groups - see resolveLineWidth above.
    private static final String DEFAULT_FILL_COLOR = "#FF000033";

    private static String resolveFillColor(LoadingMarkerGroupV2 markerGroup) {
        var fillColor = markerGroup.fillColor();
        if (fillColor == null) return DEFAULT_FILL_COLOR;

        var type = effectiveType(markerGroup);
        if (type != MarkerGroupType.SHAPE) return fillColor;

        if (!ColorUtils.isValidHex(fillColor)) {
            LOGGER.warn("Marker group '{}' has a malformed 'fillColor' ({}); falling back to default {}",
                    markerGroup.name(), fillColor, DEFAULT_FILL_COLOR);
            return DEFAULT_FILL_COLOR;
        }

        return fillColor;
    }

    private static BMSMConfigV2 loadV1Config(File file, BMSMConfigV1 v1Config) {
        var path = file.toString();
        LOGGER.info("Migrating config from v1 to v2...");
        if (!FileUtils.createBackup(path, ".v1.bak", "config file")) {
            throw new IllegalStateException(
                    "Failed to back up config file " + path + " before v1-to-v2 migration; aborting migration to "
                            + "avoid overwriting the original with no recoverable backup");
        }

        return new BMSMConfigV2(
                new MarkerGroup(
                        v1Config.getPoiPrefix(),
                        MarkerGroupMatchType.STARTS_WITH,
                        MarkerGroupType.POI,
                        "Points of Interest",
                        null,
                        0,
                        0,
                        false,
                        0,
                        10000000.0,
                        2,
                        "#FF0000FF",
                        "#FF000033",
                        0,
                        true,
                        true,
                        List.of()));
    }
}
