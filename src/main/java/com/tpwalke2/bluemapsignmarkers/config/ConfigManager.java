package com.tpwalke2.bluemapsignmarkers.config;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.ServerPathProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ConfigManager implements ConfigContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private final CoreConfig coreConfig;

    public ConfigManager(ServerPathProvider serverPathProvider) throws ConfigurationException {
        var configProvider = new ConfigProvider(serverPathProvider.getConfigFolder());

        this.coreConfig = loadCoreConfig(configProvider);
    }

    @Override
    public CoreConfig getCoreConfig() {
        return coreConfig;
    }

    private static synchronized CoreConfig loadCoreConfig(ConfigProvider configProvider) throws ConfigurationException {
        var configFileRaw = Path.of("core");
        var configFile = configProvider.findConfigPath(configFileRaw);
        var configFolder = configFile.getParent();

        if (!Files.exists(configFile)) {
            try {
                Files.createDirectories(configFolder);
                Files.writeString(
                        configFolder.resolve("core.conf"),
                        configProvider.loadDefaultConfig("/com/tpwalke2/bluemapsignmarkers/config/core.conf"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ex) {
                LOGGER.warn("Failed to create default core config file", ex);
            }
        }

        return configProvider.loadConfig(configFileRaw, CoreConfig.class);
    }
}
