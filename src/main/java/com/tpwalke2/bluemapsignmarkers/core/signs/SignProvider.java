package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class SignProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private SignProvider() {}

    public static void loadSigns(String path) {
        LOGGER.info("Loading markers from file: {}...", path);

        var file = new File(path);
        if (!file.exists()) {
            LOGGER.info("Markers file does not yet exist, skipping...");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            var signEntries = MarkerGson.INSTANCE.fromJson(reader, SignEntry[].class);

            for (SignEntry signEntry : signEntries) {
                SignManager.addOrUpdate(signEntry);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load markers from file", e);
        }
    }

    public static void saveSigns(String path) {
        LOGGER.info("Saving markers to file: {}...", path);

        try (FileWriter writer = new FileWriter(path)) {
            var signEntries = SignManager.getAll();
            MarkerGson.INSTANCE.toJson(signEntries, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save markers to file", e);
        }
    }
}
