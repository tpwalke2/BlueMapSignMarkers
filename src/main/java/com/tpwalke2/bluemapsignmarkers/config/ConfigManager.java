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
        return getOrLoad(ConfigManager::reload);
    }

    // Visible for testing: same first-load race protection as get(), but loading from a temp-directory path
    // instead of the hardcoded config path, so merely referencing this class in a test never touches the real
    // config/<mod-id>/BMSM-Core.json on disk.
    static BMSMConfigV2 get(Path configPath) {
        return getOrLoad(() -> reload(configPath));
    }

    // Synchronizes the check-then-act of "is coreConfig loaded yet?" (double-checked locking on the same
    // monitor reload() already uses) so racing first-time callers converge on a single load/parse pass and the
    // same config instance, instead of each redundantly loading config and racing over which instance wins
    // (finding #72, review: agent-context/reviews/full-codebase-review_2026-09-07_0900.md).
    private static BMSMConfigV2 getOrLoad(Runnable loader) {
        if (coreConfig == null) {
            synchronized (ConfigManager.class) {
                if (coreConfig == null) {
                    loader.run();
                }
            }
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

    // Visible for testing: resets the static config to unloaded state so a test can exercise get()'s
    // first-load path repeatedly instead of only once per JVM.
    static synchronized void resetForTesting() {
        coreConfig = null;
    }

    private static BMSMConfigV2 loadCoreConfig(BMSMConfigV2 result) {
        if (result == null) {
            LOGGER.warn("Failed to load core config, using defaults");
            result = new BMSMConfigV2();
        }

        return result;
    }
}
