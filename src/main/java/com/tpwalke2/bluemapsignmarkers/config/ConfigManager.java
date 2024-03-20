package com.tpwalke2.bluemapsignmarkers.config;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final BMSMConfigV1 coreConfig = loadCoreConfig();

    private ConfigManager() {
    }

    public static BMSMConfigV1 get() {
        return coreConfig;
    }

    public static void save() {
        ConfigProvider.saveConfig(coreConfig);
    }

    private static synchronized BMSMConfigV1 loadCoreConfig() {
        var result = ConfigProvider.loadConfig();
        if (result == null) {
            LOGGER.warn("Failed to load core config, using defaults");
            result = new BMSMConfigV1();
        }

        return result;
    }
}
