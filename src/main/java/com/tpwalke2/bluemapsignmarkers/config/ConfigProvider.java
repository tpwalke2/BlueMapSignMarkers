package com.tpwalke2.bluemapsignmarkers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.FileUtils;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV1;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV2;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final Gson GSON = new GsonBuilder()
            .setLenient()
            .setPrettyPrinting()
            .create();

    private ConfigProvider() {}

    private static Path getConfigPath() {
        return Path.of("config", Constants.MOD_ID, "BMSM-Core.json");
    }

    public static void saveConfig(BMSMConfigV2 config) {
        var path = getConfigPath();
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

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public static BMSMConfigV2 loadConfig() {
        var file = getConfigPath().toFile();

        LOGGER.info("Loading config from file: {}...", file);

        if (!file.exists()) {
            LOGGER.info("Markers file does not yet exist, skipping...");
            return new BMSMConfigV2();
        }

        String configContent;
        try {
            configContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to read config file", e);
            return null;
        }

        try {
            // v1 attempt
            var v1Config = GSON.fromJson(configContent, BMSMConfigV1.class);
            if (v1Config != null && v1Config.getPoiPrefix() != null && !v1Config.getPoiPrefix().isEmpty()) {
                return loadV1Config(file, v1Config);
            }

            // v2 attempt
            return GSON.fromJson(configContent, BMSMConfigV2.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load config:", e);
            return null;
        }
    }

    private static BMSMConfigV2 loadV1Config(File file, BMSMConfigV1 v1Config) {
        var path = file.toString();
        LOGGER.info("Migrating config from v1 to v2...");
        // make copy of v1 config
        var preMigrationBackupPath = path + ".v1.bak";
        var preMigrationBackupFile = new File(preMigrationBackupPath);
        if (!preMigrationBackupFile.exists()) {
            LOGGER.info("Creating backup of config file...");
            FileUtils.copyFile(path, preMigrationBackupPath);
        }

        var v2Config = new BMSMConfigV2(new MarkerGroup(v1Config.getPoiPrefix(), MarkerGroupType.POI, "Points of Interest", null, 0, 0));
        saveConfig(v2Config);
        return v2Config;
    }
}
