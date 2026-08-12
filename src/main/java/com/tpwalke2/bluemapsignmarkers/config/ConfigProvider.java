package com.tpwalke2.bluemapsignmarkers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.tpwalke2.bluemapsignmarkers.Constants;
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

    private static void warnOnTypeFieldMismatches(LoadingMarkerGroupV2[] markerGroups) {
        for (var markerGroup : markerGroups) {
            var type = markerGroup.type() == null ? MarkerGroupType.POI : markerGroup.type();
            var name = markerGroup.name();

            if (type == MarkerGroupType.POI) {
                if (markerGroup.lineWidth() != null) {
                    LOGGER.warn("Marker group '{}' is type POI but has 'lineWidth' set; this field is ignored for POI groups", name);
                }
                if (markerGroup.lineColor() != null) {
                    LOGGER.warn("Marker group '{}' is type POI but has 'lineColor' set; this field is ignored for POI groups", name);
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
            }
        }
    }

    private static MarkerGroup convertToLoadedMarkerGroup(LoadingMarkerGroupV2 markerGroup) {
        return new MarkerGroup(
                markerGroup.prefix(),
                markerGroup.matchType() == null ? MarkerGroupMatchType.STARTS_WITH : markerGroup.matchType(),
                markerGroup.type() == null ? MarkerGroupType.POI : markerGroup.type(),
                markerGroup.name(),
                markerGroup.icon(),
                markerGroup.offsetX() == null ? 0 : markerGroup.offsetX(),
                markerGroup.offsetY() == null ? 0 : markerGroup.offsetY(),
                markerGroup.defaultHidden() != null && markerGroup.defaultHidden(),
                markerGroup.minDistance() == null ? 0.0 : markerGroup.minDistance(),
                markerGroup.maxDistance() == null ? 10000000.0 : markerGroup.maxDistance(),
                markerGroup.lineWidth() == null ? 2 : markerGroup.lineWidth(),
                markerGroup.lineColor() == null ? "#FF0000FF" : markerGroup.lineColor()
        );
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
                        "#FF0000FF"));
    }
}
