package com.tpwalke2.bluemapsignmarkers.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SignManager {
    private static SignManager INSTANCE;
    public static SignManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SignManager();
        }

        return INSTANCE;
    }

    private SignManager() {   }

    public static void addOrUpdate(SignEntry signEntry) {
        getInstance().addOrUpdateSign(signEntry);
    }
    private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();

    public List<SignEntry> getAllSigns() {
        return new ArrayList<>(signCache.values());
    }

    public void addOrUpdateSign(SignEntry signEntry) {
        var key = signEntry.getKey();

        signCache.put(key, signEntry);
    }

    private void removeSign(SignEntry signEntry) {
        var key = signEntry.getKey();

        signCache.remove(key);
    }
}
