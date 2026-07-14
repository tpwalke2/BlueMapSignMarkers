package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import java.nio.file.Path;

public record SignRegionKey(String dimension, int regionX, int regionZ) {
    // 32x32 chunks * 16 blocks/chunk, matching Minecraft's own Anvil (.mca) region size.
    private static final int REGION_SIZE_BLOCKS = 512;

    public static SignRegionKey forPosition(String dimension, int x, int z) {
        return new SignRegionKey(
                dimension,
                Math.floorDiv(x, REGION_SIZE_BLOCKS),
                Math.floorDiv(z, REGION_SIZE_BLOCKS));
    }

    public Path relativeFilePath() {
        var separatorIndex = dimension.indexOf(':');
        var namespace = separatorIndex < 0 ? dimension : dimension.substring(0, separatorIndex);
        var rawPath = separatorIndex < 0 ? "" : dimension.substring(separatorIndex + 1);
        var fileName = "r." + regionX + "." + regionZ + ".json";

        if (namespace.isBlank() || namespace.equals(".") || namespace.equals("..")) {
            throw new IllegalArgumentException("Invalid dimension namespace: " + namespace);
        }

        var relativeDir = Path.of(namespace).resolve(rawPath).normalize();
        if (relativeDir.isAbsolute() || relativeDir.startsWith("..")) {
            throw new IllegalArgumentException("Unsafe dimension id for storage path: " + dimension);
        }

        return relativeDir.resolve(fileName);
    }
}
