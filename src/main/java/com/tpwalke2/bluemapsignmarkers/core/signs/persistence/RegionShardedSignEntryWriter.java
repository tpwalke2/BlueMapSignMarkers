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
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegionShardedSignEntryWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private static final String STALE_SUFFIX = ".stale";

    private RegionShardedSignEntryWriter() {
    }

public static void write(Path storageRoot, List<SignEntry> signEntries, Gson gson) {
    var partitions = SignRegionPartitioner.partition(signEntries);

    var writtenFiles = new HashSet<Path>();
    var hadWriteFailures = false;

    for (var partition : partitions.entrySet()) {
        Path filePath;
        try {
            filePath = storageRoot.resolve(partition.getKey().relativeFilePath());
        } catch (IllegalArgumentException e) {
            hadWriteFailures = true;
            LOGGER.error("Failed to resolve storage path for region key {}; skipping this partition", partition.getKey(), e);
            continue;
        }

        if (writeRegionFile(filePath, partition.getValue(), gson)) {
            writtenFiles.add(filePath);
        } else {
            hadWriteFailures = true;
        }
    }

    if (!hadWriteFailures) {
        quarantineStaleRegionFiles(storageRoot, writtenFiles);
    } else {
        LOGGER.warn("One or more region files failed to write; skipping stale-file quarantine under {}", storageRoot);
    }
}

private static boolean writeRegionFile(Path filePath, List<SignEntry> signEntries, Gson gson) {
    try {
        Files.createDirectories(filePath.getParent());
        var signEntryData = gson.toJson(signEntries);
        var json = gson.toJson(new VersionedSignFile(SignFileVersions.V3, signEntryData));
        Files.writeString(filePath, json, StandardCharsets.UTF_8);
        return true;
    } catch (IOException e) {
        LOGGER.error("Failed to write region file {}", filePath, e);
        return false;
    }
}

    // A region absent from writtenFiles may be genuinely empty, or may have failed to load at
    // startup - can't tell which here, so quarantine instead of delete to avoid losing real data.
    private static void quarantineStaleRegionFiles(Path storageRoot, Set<Path> writtenFiles) {
        if (!Files.isDirectory(storageRoot)) return;

        try (var existing = Files.walk(storageRoot)) {
            existing.filter(Files::isRegularFile)
                    .filter(path -> {
                        var name = path.getFileName().toString();
                        return name.startsWith("r.") && name.endsWith(".json");
                    })
                    .filter(path -> !writtenFiles.contains(path))
                    .forEach(RegionShardedSignEntryWriter::quarantineStaleFile);
        } catch (IOException e) {
            LOGGER.error("Failed to clean up stale region files under {}", storageRoot, e);
        }
    }

    private static void quarantineStaleFile(Path path) {
        var stalePath = path.resolveSibling(path.getFileName() + STALE_SUFFIX);
        try {
            Files.move(path, stalePath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Region file {} had no signs in memory; moved it to {} instead of deleting, " +
                    "in case it failed to load rather than being genuinely empty", path, stalePath);
        } catch (IOException e) {
            LOGGER.warn("Failed to quarantine stale region file {}", path, e);
        }
    }
}
