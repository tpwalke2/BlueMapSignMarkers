package com.tpwalke2.bluemapsignmarkers.config;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final BMSMConfig coreConfig = loadCoreConfig();

    private ConfigManager() {
    }

    public static BMSMConfig get() {
        return coreConfig;
    }

    public static void save() {
        ConfigProvider.saveConfig(coreConfig);
    }

    private static synchronized BMSMConfig loadCoreConfig() {
        var result = ConfigProvider.loadConfig();
        if (result == null) {
            LOGGER.warn("Failed to load core config, using defaults");
            result = new BMSMConfig();
        }

        return result;
    }
}
