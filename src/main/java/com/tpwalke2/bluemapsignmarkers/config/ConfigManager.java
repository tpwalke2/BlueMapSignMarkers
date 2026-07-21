package com.tpwalke2.bluemapsignmarkers.config;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static volatile BMSMConfigV2 coreConfig;

    private ConfigManager() {
    }

    public static BMSMConfigV2 get() {
        if (coreConfig == null) {
            reload();
        }

        return coreConfig;
    }

    public static synchronized void reload() {
        coreConfig = loadCoreConfig(ConfigProvider.loadConfig());
    }

    // Visible for testing: lets tests (re)load from a temp-directory path instead of the hardcoded config path,
    // so merely referencing this class in a test never touches the real config/<mod-id>/BMSM-Core.json on disk.
    static synchronized void reload(Path configPath) {
        coreConfig = loadCoreConfig(ConfigProvider.loadConfig(configPath));
    }

    private static BMSMConfigV2 loadCoreConfig(BMSMConfigV2 result) {
        if (result == null) {
            LOGGER.warn("Failed to load core config, using defaults");
            result = new BMSMConfigV2();
        }

        return result;
    }
}
