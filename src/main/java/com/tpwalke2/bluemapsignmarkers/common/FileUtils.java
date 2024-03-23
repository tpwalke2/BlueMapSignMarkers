package com.tpwalke2.bluemapsignmarkers.common;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private FileUtils() {}

    public static void copyFile(String sourcePath, String destinationPath) {
        try {
            Files.copy(Paths.get(sourcePath), Paths.get(destinationPath));
        } catch (IOException e) {
            LOGGER.warn("Failed to copy {} to {}: {}", sourcePath, destinationPath, e);
        }
    }
}
