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

    private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();

    public void addSign(SignEntry signEntry) {
        var key = signEntry.getKey();

        signCache.put(key, signEntry);
    }

    private void removeSign(SignEntry signEntry) {
        var key = signEntry.getKey();

        signCache.remove(key);
    }
}
