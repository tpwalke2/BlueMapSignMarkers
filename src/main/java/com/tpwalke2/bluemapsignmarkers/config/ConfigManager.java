package com.tpwalke2.bluemapsignmarkers.config;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.models.BMSMConfigV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final BMSMConfigV2 coreConfig = loadCoreConfig();

    private ConfigManager() {
    }

    public static BMSMConfigV2 get() {
        return coreConfig;
    }

    private static synchronized BMSMConfigV2 loadCoreConfig() {
        var result = ConfigProvider.loadConfig();
        if (result == null) {
            LOGGER.warn("Failed to load core config, using defaults");
            result = new BMSMConfigV2();
        }

        return result;
    }
}
