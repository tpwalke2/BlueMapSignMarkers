package com.tpwalke2.bluemapsignmarkers.core.signs;

public record SignChunkKey(String parentMap, int chunkX, int chunkZ) {
    // Vanilla chunk size in blocks - distinct from and finer-grained than SignRegionKey's 512-block
    // (32-chunk) region math, which is a persistence file-layout concern, not a runtime lookup one.
    private static final int CHUNK_SIZE_BLOCKS = 16;

    public static SignChunkKey forEntryKey(SignEntryKey key) {
        return new SignChunkKey(
                key.parentMap(),
                Math.floorDiv(key.x(), CHUNK_SIZE_BLOCKS),
                Math.floorDiv(key.z(), CHUNK_SIZE_BLOCKS));
    }
}
