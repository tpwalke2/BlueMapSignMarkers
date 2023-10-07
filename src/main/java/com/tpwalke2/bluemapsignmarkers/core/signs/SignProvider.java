package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SignProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final Gson GSON = new GsonBuilder()
            .setLenient()
            .create();

    private SignProvider() {}

    public static void loadSigns(String path) {
        LOGGER.info("Loading markers from file: {}...", path);

        var file = new File(path);
        if (!file.exists()) {
            LOGGER.info("Markers file does not yet exist, skipping...");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            var signEntries = GSON.fromJson(reader, SignEntry[].class);

            for (SignEntry signEntry : signEntries) {
                SignManager.addOrUpdate(signEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load markers from file", e);
        }
    }

    public static void saveSigns(String path) {
        LOGGER.info("Saving markers to file: {}...", path);

        var file = new File(path);
        var parent = file.getParentFile();
        if (!parent.exists()) {
            try {
                Files.createDirectories(Paths.get(parent.getAbsolutePath()));
            } catch (IOException e) {
                LOGGER.error("Failed to create parent directories for markers file", e);
                return;
            }
        }

        try (FileWriter writer = new FileWriter(path)) {
            var signEntries = SignManager.getAll();
            GSON.toJson(signEntries, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save markers to file", e);
        }
    }
}
