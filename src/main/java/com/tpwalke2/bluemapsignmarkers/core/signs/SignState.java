package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SignState {
    private final Map<SignEntryKey, SignEntry> allSigns = new ConcurrentHashMap<>();
    private final SignGroups lineSigns = new SignGroups();

    public synchronized List<SignEntry> getAllSigns() {
        return List.copyOf(allSigns.values());
    }

    public synchronized void clear() {
        allSigns.clear();
        lineSigns.clear();
    }

    public Optional<SignEntry> getSign(SignEntryKey key) {
        return Optional.ofNullable(allSigns.get(key));
    }

    public synchronized void addPoiSign(SignEntry signEntry) {
        allSigns.put(signEntry.key(), signEntry);
    }

    public synchronized void addLineSign(SignGroupKey signGroupKey, SignEntry signEntry) {
        lineSigns.addOrUpdateSign(signGroupKey, signEntry);
        allSigns.put(signEntry.key(), signEntry);
    }

    public synchronized Optional<List<SignEntry>> getLineSigns(SignGroupKey signGroupKey) {
        return Optional.ofNullable(lineSigns.getSigns(signGroupKey));
    }

    // removeSign method that takes a SignEntry and removes it from all caches
    public synchronized void removeSign(SignEntry signEntry) {
        allSigns.remove(signEntry.key());
        lineSigns.removeSign(signEntry);
    }
}
