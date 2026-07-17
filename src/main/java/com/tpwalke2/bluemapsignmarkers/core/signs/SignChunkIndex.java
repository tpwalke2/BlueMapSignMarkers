package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Secondary lookup alongside SignManager's flat sign cache: which sign keys fall in a given chunk, so chunk-load
// reconciliation can ask "signs known here?" in O(1) instead of scanning every cached sign.
public class SignChunkIndex {
    private final ConcurrentHashMap<SignChunkKey, Set<SignEntryKey>> signsByChunk = new ConcurrentHashMap<>();

    // add/remove mutate the per-chunk set inside the map's own compute-family remapping function (not via a
    // fetch-then-mutate-outside-the-lock pattern) so the "does this chunk still have entries" decision and the
    // mutation happen as one atomic step - ConcurrentHashMap serializes compute/computeIfPresent/computeIfAbsent
    // calls against each other for the same key, so a concurrent add can never resurrect a set remove just deleted.
    public void add(SignEntryKey key) {
        signsByChunk.compute(SignChunkKey.forEntryKey(key), (unusedKey, keysInChunk) -> {
            var result = keysInChunk != null ? keysInChunk : ConcurrentHashMap.<SignEntryKey>newKeySet();
            result.add(key);
            return result;
        });
    }

    public void remove(SignEntryKey key) {
        signsByChunk.computeIfPresent(SignChunkKey.forEntryKey(key), (unusedKey, keysInChunk) -> {
            keysInChunk.remove(key);
            return keysInChunk.isEmpty() ? null : keysInChunk;
        });
    }

    public List<SignEntryKey> keysInChunk(String parentMap, int chunkX, int chunkZ) {
        var keysInChunk = signsByChunk.get(new SignChunkKey(parentMap, chunkX, chunkZ));
        return keysInChunk == null ? List.of() : new ArrayList<>(keysInChunk);
    }

    public void clear() {
        signsByChunk.clear();
    }
}
