package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegionShardedSignEntryWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private RegionShardedSignEntryWriter() {
    }

    public static void write(Path storageRoot, List<SignEntry> signEntries, Gson gson) {
        var partitions = SignRegionPartitioner.partition(signEntries);

        var writtenFiles = new HashSet<Path>();
        for (var partition : partitions.entrySet()) {
            var filePath = storageRoot.resolve(partition.getKey().relativeFilePath());
            writeRegionFile(filePath, partition.getValue(), gson);
            writtenFiles.add(filePath);
        }

        deleteStaleRegionFiles(storageRoot, writtenFiles);
    }

    private static void writeRegionFile(Path filePath, List<SignEntry> signEntries, Gson gson) {
        try {
            Files.createDirectories(filePath.getParent());
            var signEntryData = gson.toJson(signEntries);
            var json = gson.toJson(new VersionedSignFile(SignFileVersions.V3, signEntryData));
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write region file {}", filePath, e);
        }
    }

    private static void deleteStaleRegionFiles(Path storageRoot, Set<Path> writtenFiles) {
        if (!Files.isDirectory(storageRoot)) return;

        try (var existing = Files.walk(storageRoot)) {
            existing.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> !writtenFiles.contains(path))
                    .forEach(RegionShardedSignEntryWriter::deleteQuietly);
        } catch (IOException e) {
            LOGGER.error("Failed to clean up stale region files under {}", storageRoot, e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.delete(path);
            LOGGER.debug("Removed stale region file {}", path);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete stale region file {}", path, e);
        }
    }
}
