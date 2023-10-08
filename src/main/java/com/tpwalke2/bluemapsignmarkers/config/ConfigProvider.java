package com.tpwalke2.bluemapsignmarkers.config;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tpwalke2.bluemapsignmarkers.Constants;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
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

    public static BMSMConfig loadConfig() {
        return loadConfigFile(getConfigPath());
    }

    public static void saveConfig(BMSMConfig config) {
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

    public static BMSMConfig loadDefaultConfig(String resource) throws IOException {
        var in = ConfigProvider.class.getResourceAsStream(resource);
        if (in == null) throw new IOException("Resource not found: " + resource);
        return GSON.fromJson(IOUtils.toString(in, StandardCharsets.UTF_8), BMSMConfig.class);
    }

    private static BMSMConfig loadConfigFile(Path path) {
        if (!Files.exists(path)) {
            LOGGER.info("Config file does not exist: {}", path);
            return null;
        }

        try (FileReader reader = new FileReader(path.toFile())) {
            LOGGER.info("Loading config file: {}", path);
            return GSON.fromJson(reader, BMSMConfig.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load config:", e);
            return null;
        }
    }
}
