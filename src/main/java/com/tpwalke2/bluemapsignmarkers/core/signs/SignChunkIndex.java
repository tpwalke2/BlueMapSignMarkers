package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Secondary lookup alongside SignManager's flat sign cache: which sign keys fall in a given chunk, so chunk-load
// reconciliation can ask "signs known here?" in O(1) instead of scanning every cached sign.
public class SignChunkIndex {
    private final ConcurrentHashMap<SignChunkKey, Set<SignEntryKey>> signsByChunk = new ConcurrentHashMap<>();

    public void add(SignEntryKey key) {
        signsByChunk
                .computeIfAbsent(SignChunkKey.forEntryKey(key), unused -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    public void remove(SignEntryKey key) {
        var chunkKey = SignChunkKey.forEntryKey(key);
        var keysInChunk = signsByChunk.get(chunkKey);
        if (keysInChunk == null) {
            return;
        }

        keysInChunk.remove(key);
        if (keysInChunk.isEmpty()) {
            signsByChunk.remove(chunkKey, keysInChunk);
        }
    }

    public List<SignEntryKey> keysInChunk(String parentMap, int chunkX, int chunkZ) {
        var keysInChunk = signsByChunk.get(new SignChunkKey(parentMap, chunkX, chunkZ));
        return keysInChunk == null ? List.of() : new ArrayList<>(keysInChunk);
    }

    public void clear() {
        signsByChunk.clear();
    }
}
