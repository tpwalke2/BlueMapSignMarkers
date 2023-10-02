package com.tpwalke2.bluemapsignmarkers.config;

import org.apache.commons.io.IOUtils;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ConfigProvider {
    private final Path configRoot;

    public ConfigProvider(Path configRoot) {
        this.configRoot = configRoot;
    }

    public <T> T loadConfig(Path rawPath, Class<T> type) throws ConfigurationException {
        Path path = findConfigPath(rawPath);
        ConfigurationNode configNode = loadConfigFile(path);
        try {
            return Objects.requireNonNull(configNode.get(type));
        } catch (SerializationException | NullPointerException ex) {
            throw new ConfigurationException(
                    "BlueMap failed to parse this file:\n" +
                            path + "\n" +
                            "Check if the file is correctly formatted and all values are correct!",
                    ex);
        }
    }

    public String loadDefaultConfig(String resource) throws IOException {
        var in = ConfigProvider.class.getResourceAsStream(resource);
        if (in == null) throw new IOException("Resource not found: " + resource);
        return IOUtils.toString(in, StandardCharsets.UTF_8);
    }

    public Path findConfigPath(Path rawPath) {
        if (!rawPath.startsWith(configRoot)) {
            rawPath = configRoot.resolve(rawPath);
        }

        return rawPath.getFileName().endsWith(".conf")
                ? rawPath
                : rawPath.resolveSibling(rawPath.getFileName() + ".conf");
    }

    private ConfigurationNode loadConfigFile(Path path) throws ConfigurationException {
        if (!Files.exists(path)) {
            throw new ConfigurationException(
                    "This config file does not exist:\n" +
                            path);
        }

        if (!Files.isReadable(path)) {
            throw new ConfigurationException(
                    "This file is not accessible:\n" +
                            path);
        }

        try {
            return  getLoader(path).load();
        } catch (IOException ex) {
            throw new ConfigurationException(
                    "BlueMap tried to read this file, but failed:\n" +
                            path + "\n" +
                            "Check if BlueMap has the permission to read this file.",
                    ex);
        }
    }

    private ConfigurationLoader<? extends ConfigurationNode> getLoader(Path path) {
        return HoconConfigurationLoader.builder()
                .path(path)
                .build();
    }
}
