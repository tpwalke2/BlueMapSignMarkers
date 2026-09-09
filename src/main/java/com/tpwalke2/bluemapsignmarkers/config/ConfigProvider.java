package com.tpwalke2.bluemapsignmarkers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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
import java.nio.file.StandardCopyOption;
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
        if (parent != null && !parent.exists()) {
            try {
                Files.createDirectories(Paths.get(parent.getAbsolutePath()));
            } catch (IOException e) {
                LOGGER.error("Failed to create parent directories for config", e);
                return;
            }
        }

        // Writes via a temp file in the same directory, then an atomic move into place (same pattern as
        // FileUtils.copyFile / RegionShardedSignEntryWriter), so a crash or disk-full mid-write never
        // leaves a truncated config file sitting at path.
        var tempFile = path.resolveSibling(path.getFileName() + ".tmp");
        try (var writer = new OutputStreamWriter(Files.newOutputStream(tempFile), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
            FileUtils.deleteQuietly(tempFile);
            return;
        }

        try {
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to move temp config file into place at {}", path, e);
            FileUtils.deleteQuietly(tempFile);
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
            var markerGroups = new MarkerGroup[loadingMarkerGroups.length];
            for (var i = 0; i < loadingMarkerGroups.length; i++) {
                var loadingGroup = loadingMarkerGroups[i];
                // Resolved once per group and threaded through both the conversion and the mismatch
                // warnings below, rather than re-resolved at each of the several call sites that need the
                // effective type - avoids logging a redundant warning per call site for one malformed value.
                var type = resolveType(loadingGroup);
                warnOnTypeFieldMismatches(loadingGroup, type);
                markerGroups[i] = convertToLoadedMarkerGroup(loadingGroup, type);
            }

            validateMarkerGroups(markerGroups);

            return new BMSMConfigV2(markerGroups, resolveShutdownAwaitSeconds(result.getShutdownAwaitSeconds()));

        } catch (Exception e) {
            LOGGER.error("Failed to load config:", e);
            return null;
        }
    }

    // Mod-wide (not per-group) setting, so it's read straight off LoadingBMSMConfigV2 rather than through
    // the per-group resolve*Field helpers. A malformed or non-positive value falls back to the default with
    // a warning rather than failing GSON.fromJson for the whole config (same treatment as resolveLineWidth).
    private static int resolveShutdownAwaitSeconds(JsonElement element) {
        var defaultValue = BMSMConfigV2.DEFAULT_SHUTDOWN_AWAIT_SECONDS;
        if (element == null || element.isJsonNull()) return defaultValue;

        int value;
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("not a numeric JSON primitive");
            }
            value = element.getAsBigDecimal().intValueExact();
        } catch (RuntimeException e) {
            LOGGER.warn("Config has a malformed 'shutdownAwaitSeconds' ({}); falling back to default {}",
                    element, defaultValue);
            return defaultValue;
        }

        if (value <= 0) {
            LOGGER.warn("Config has a non-positive 'shutdownAwaitSeconds' ({}); falling back to default {}",
                    value, defaultValue);
            return defaultValue;
        }

        return value;
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

            // Keyed on raw prefix text alone, not matchType + prefix: a STARTS_WITH "[a]" group and a
            // REGEX "[a]" group match different sign text, but every runtime lookup that resolves a
            // sign's representation back to its group (SignManager.buildPrefixGroupMap,
            // SignEntryHelper.getPrefix/SignTransitionResolver.computeRepresentation) is keyed on raw
            // prefix text alone - it has no way to recover which of two same-prefix groups a given sign
            // matched. Allowing this pair to validate previously let a config load successfully while one
            // of the two groups silently never matched any sign (see
            // agent-context/reviews/copilotreview.2026-09-09.md); rejecting it here instead gives the
            // admin an upfront error instead of the group quietly not working. Revisit only alongside
            // threading (matchType, prefix) identity through SignLinesParseResult and every downstream
            // lookup.
            if (!seenPrefixes.add(prefix)) {
                throw new IllegalArgumentException(
                        "Marker group '" + markerGroup.name() + "' has a prefix duplicated across groups: "
                                + prefix);
            }
        }
    }

    // Resolves the 'type' field to its effective MarkerGroupType: missing/null defaults to POI, and a
    // malformed value (not a string, or not one of the enum's names) degrades to POI with a warning rather
    // than failing GSON.fromJson for the whole config. Called once per group (see the loadConfig loop) and
    // threaded through as a parameter so a single malformed value only logs once.
    private static MarkerGroupType resolveType(LoadingMarkerGroupV2 markerGroup) {
        var element = markerGroup.type();
        if (element == null || element.isJsonNull()) return MarkerGroupType.POI;

        var parsed = parseEnum(element, MarkerGroupType.class);
        if (parsed == null) {
            LOGGER.warn("Marker group '{}' has a malformed 'type' ({}); falling back to default {}",
                    markerGroup.name(), element, MarkerGroupType.POI);
            return MarkerGroupType.POI;
        }
        return parsed;
    }

    private static MarkerGroupMatchType resolveMatchType(LoadingMarkerGroupV2 markerGroup) {
        var element = markerGroup.matchType();
        if (element == null || element.isJsonNull()) return MarkerGroupMatchType.STARTS_WITH;

        var parsed = parseEnum(element, MarkerGroupMatchType.class);
        if (parsed == null) {
            LOGGER.warn("Marker group '{}' has a malformed 'matchType' ({}); falling back to default {}",
                    markerGroup.name(), element, MarkerGroupMatchType.STARTS_WITH);
            return MarkerGroupMatchType.STARTS_WITH;
        }
        return parsed;
    }

    private static final String DEFAULT_NAME_PLACEHOLDER = "(unnamed)";

    // name has no default-safe value like the other fields (it's not derivable from a Java default, only from
    // the group's own prefix), so it's resolved here rather than via resolve*Field - a missing/blank name must
    // degrade to a fallback with a warning instead of throwing MarkerGroup's requireNonNull and wiping the
    // whole config back to defaults.
    private static String resolveName(LoadingMarkerGroupV2 markerGroup) {
        var name = markerGroup.name();
        if (name != null && !name.isBlank()) return name;

        var prefix = markerGroup.prefix();
        var hasPrefix = prefix != null && !prefix.isBlank();
        var fallback = hasPrefix ? prefix : DEFAULT_NAME_PLACEHOLDER;

        // Identify by prefix (like every sibling resolver's warning does via markerGroup.name() - name is
        // unavailable here since it's the broken field), falling back to a distinct marker when the prefix is
        // also missing/blank, so this warning doesn't read identically for every broken group in a config.
        LOGGER.warn("Marker group '{}' has a missing or blank 'name'; falling back to '{}'",
                hasPrefix ? prefix : "(no prefix)", fallback);
        return fallback;
    }

    private static <T extends Enum<T>> T parseEnum(JsonElement element, Class<T> enumType) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
            return Enum.valueOf(enumType, element.getAsString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int resolveIntField(LoadingMarkerGroupV2 markerGroup, JsonElement element, int defaultValue,
                                        String fieldName) {
        if (element == null || element.isJsonNull()) return defaultValue;

        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("not a numeric JSON primitive");
            }
            return element.getAsBigDecimal().intValueExact();
        } catch (RuntimeException e) {
            LOGGER.warn("Marker group '{}' has a malformed '{}' ({}); falling back to default {}",
                    markerGroup.name(), fieldName, element, defaultValue);
            return defaultValue;
        }
    }

    private static double resolveDoubleField(LoadingMarkerGroupV2 markerGroup, JsonElement element,
                                              double defaultValue, String fieldName) {
        if (element == null || element.isJsonNull()) return defaultValue;

        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("not a numeric JSON primitive");
            }
            return element.getAsDouble();
        } catch (RuntimeException e) {
            LOGGER.warn("Marker group '{}' has a malformed '{}' ({}); falling back to default {}",
                    markerGroup.name(), fieldName, element, defaultValue);
            return defaultValue;
        }
    }

    private static boolean resolveBooleanField(
            LoadingMarkerGroupV2 markerGroup,
            JsonElement element,
            boolean defaultValue,
            String fieldName) {
        if (element == null || element.isJsonNull()) return defaultValue;

        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("not a boolean JSON primitive");
            }
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            LOGGER.warn("Marker group '{}' has a malformed '{}' ({}); falling back to default {}",
                    markerGroup.name(), fieldName, element, defaultValue);
            return defaultValue;
        }
    }

    private static void warnOnTypeFieldMismatches(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
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
            if (markerGroup.allowPlayerColors() != null) {
                LOGGER.warn("Marker group '{}' is type POI but has 'allowPlayerColors' set; this field is ignored for POI groups", name);
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
        } else if (type == MarkerGroupType.EXTRUDE) {
            if (markerGroup.icon() != null) {
                LOGGER.warn("Marker group '{}' is type EXTRUDE but has 'icon' set; this field is ignored for EXTRUDE groups", name);
            }
            if (markerGroup.offsetX() != null) {
                LOGGER.warn("Marker group '{}' is type EXTRUDE but has 'offsetX' set; this field is ignored for EXTRUDE groups", name);
            }
            if (markerGroup.offsetY() != null) {
                LOGGER.warn("Marker group '{}' is type EXTRUDE but has 'offsetY' set; this field is ignored for EXTRUDE groups", name);
            }
            if (markerGroup.cssClasses() != null && !markerGroup.cssClasses().isEmpty()) {
                LOGGER.warn("Marker group '{}' is type EXTRUDE but has 'cssClasses' set; this field is ignored for EXTRUDE groups", name);
            }
        }
    }

    private static final int DEFAULT_LINE_WIDTH = 2;

    private static MarkerGroup convertToLoadedMarkerGroup(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
        return new MarkerGroup(
                markerGroup.prefix(),
                resolveMatchType(markerGroup),
                type,
                resolveName(markerGroup),
                markerGroup.icon(),
                resolveIntField(markerGroup, markerGroup.offsetX(), 0, "offsetX"),
                resolveIntField(markerGroup, markerGroup.offsetY(), 0, "offsetY"),
                resolveBooleanField(markerGroup, markerGroup.defaultHidden(), false, "defaultHidden"),
                resolveDoubleField(markerGroup, markerGroup.minDistance(), 0.0, "minDistance"),
                resolveDoubleField(markerGroup, markerGroup.maxDistance(), 10000000.0, "maxDistance"),
                resolveLineWidth(markerGroup, type),
                resolveLineColor(markerGroup, type),
                resolveFillColor(markerGroup, type),
                resolveSorting(markerGroup),
                resolveToggleable(markerGroup),
                resolveDepthTest(markerGroup, type),
                resolveCssClasses(markerGroup, type),
                resolveAllowPlayerColors(markerGroup, type)
        );
    }

    private static final int DEFAULT_SORTING = 0;

    // sorting is read as a raw JsonElement (see LoadingMarkerGroupV2) specifically so a non-integer value
    // (e.g. a string) falls back here with a warning instead of failing Gson's parse for the entire config.
    private static int resolveSorting(LoadingMarkerGroupV2 markerGroup) {
        var sorting = markerGroup.sorting();
        if (sorting == null || sorting.isJsonNull()) return DEFAULT_SORTING;

        try {
            if (!sorting.isJsonPrimitive() || !sorting.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("not a numeric JSON primitive");
            }
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

    // depthTest is LINE/SHAPE/EXTRUDE-only (POI markers are always billboarded on top); unset or set on a
    // POI group both resolve to the BlueMap default of true - warnOnTypeFieldMismatches already warns on
    // the POI case.
    private static boolean resolveDepthTest(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
        var depthTest = markerGroup.depthTest();
        if (depthTest == null) return true;

        if (type != MarkerGroupType.LINE && type != MarkerGroupType.SHAPE && type != MarkerGroupType.EXTRUDE) return true;

        return depthTest;
    }

    // allowPlayerColors is LINE/SHAPE/EXTRUDE-only; unset or set on a POI group both resolve to false -
    // warnOnTypeFieldMismatches already warns on the POI case.
    private static boolean resolveAllowPlayerColors(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
        var allowPlayerColors = markerGroup.allowPlayerColors();
        if (allowPlayerColors == null) return false;

        if (type != MarkerGroupType.LINE && type != MarkerGroupType.SHAPE && type != MarkerGroupType.EXTRUDE) return false;

        return allowPlayerColors;
    }

    // cssClasses is POI-only; unset or set on a LINE/SHAPE group both resolve to an empty list -
    // warnOnTypeFieldMismatches already warns on the LINE/SHAPE case.
    private static List<String> resolveCssClasses(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
        var cssClasses = markerGroup.cssClasses();
        if (cssClasses == null) return List.of();

        if (type != MarkerGroupType.POI) return List.of();

        var filtered = cssClasses.stream().filter(Objects::nonNull).toList();
        if (filtered.size() != cssClasses.size()) {
            LOGGER.warn("Marker group '{}' has null entries in 'cssClasses'; dropping them", markerGroup.name());
        }
        return filtered;
    }

    // Falls back to the default width on a malformed or non-positive value rather than throwing - a bad
    // config value must not crash the server (same treatment as ColorUtils.parseHex's fallback on a
    // malformed color). Only validated for LINE/SHAPE/EXTRUDE groups - lineWidth is ignored for POI groups
    // (warnOnTypeFieldMismatches already warns about it being set), so validating it here too would just be
    // a second, confusing warning.
    private static int resolveLineWidth(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
        var lineWidth = resolveIntField(markerGroup, markerGroup.lineWidth(), DEFAULT_LINE_WIDTH, "lineWidth");
        if (type != MarkerGroupType.LINE && type != MarkerGroupType.SHAPE && type != MarkerGroupType.EXTRUDE) {
            // Not validated for POI groups, so a malformed value is left as the fallback default above,
            // and a non-positive-but-parseable value passes through unchanged.
            return lineWidth;
        }

        if (lineWidth <= 0) {
            LOGGER.warn("Marker group '{}' has a non-positive 'lineWidth' ({}); falling back to default {}",
                    markerGroup.name(), lineWidth, DEFAULT_LINE_WIDTH);
            return DEFAULT_LINE_WIDTH;
        }

        return lineWidth;
    }

    // Warns and falls back to the default color at load time (rather than silently at dispatch time via
    // ColorUtils.parseHex's fallback) so a malformed color gets a clear, attributable log message.
    // Only validated for LINE/SHAPE/EXTRUDE groups - see resolveLineWidth above.
    private static final String DEFAULT_LINE_COLOR = "#FF0000FF";

    private static String resolveLineColor(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
        var lineColor = markerGroup.lineColor();
        if (lineColor == null) return DEFAULT_LINE_COLOR;

        if (type != MarkerGroupType.LINE && type != MarkerGroupType.SHAPE && type != MarkerGroupType.EXTRUDE) return lineColor;

        if (!ColorUtils.isValidHex(lineColor)) {
            LOGGER.warn("Marker group '{}' has a malformed 'lineColor' ({}); falling back to default {}",
                    markerGroup.name(), lineColor, DEFAULT_LINE_COLOR);
            return DEFAULT_LINE_COLOR;
        }

        return lineColor;
    }

    // fillColor is SHAPE/EXTRUDE-only; its default is translucent (unlike lineColor's opaque default) so a
    // SHAPE/EXTRUDE group configured without styling doesn't blot out the map underneath it. Only validated
    // for SHAPE/EXTRUDE groups - see resolveLineWidth above.
    private static final String DEFAULT_FILL_COLOR = "#FF000033";

    private static String resolveFillColor(LoadingMarkerGroupV2 markerGroup, MarkerGroupType type) {
        var fillColor = markerGroup.fillColor();
        if (fillColor == null) return DEFAULT_FILL_COLOR;

        if (type != MarkerGroupType.SHAPE && type != MarkerGroupType.EXTRUDE) return fillColor;

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

        var migratedGroup = new MarkerGroup(
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
                DEFAULT_LINE_COLOR,
                DEFAULT_FILL_COLOR,
                0,
                true,
                true,
                List.of(),
                false);

        // Runs the migrated group through the same validation a normally-loaded v2 config's groups get
        // (e.g. rejecting a blank v1 poiPrefix, which would otherwise silently match every sign's text via
        // STARTS_WITH ""), falling back to defaults rather than persisting an invalid migrated config. The
        // v1 file has already been backed up above regardless of the outcome here.
        try {
            validateMarkerGroups(new MarkerGroup[]{migratedGroup});
        } catch (IllegalArgumentException e) {
            LOGGER.error("Migrated v1 config produced an invalid marker group; falling back to defaults", e);
            return new BMSMConfigV2();
        }

        return new BMSMConfigV2(migratedGroup);
    }
}
