package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SignGroups {
    private final ConcurrentMap<SignEntryKey, SignGroupKey> signGroupKeyMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<SignGroupKey, List<SignEntry>> signsCache = new ConcurrentHashMap<>();

    public List<SignEntry> getSigns(SignGroupKey key) {
        return signsCache.get(key);
    }

    public synchronized void addOrUpdateSign(SignGroupKey key, SignEntry signEntry) {
        removeSign(signEntry);

        signGroupKeyMap.putIfAbsent(signEntry.key(), key);
        signsCache.putIfAbsent(key, new ArrayList<>());

        var signs = signsCache.get(key);
        if (!signs.contains(signEntry)) {
            signs.add(signEntry);
        }
    }

    public synchronized void removeSign(SignEntry signEntry) {
        var key = signGroupKeyMap.get(signEntry.key());
        if (key != null) {
            var groupSignList = signsCache.get(key);
            if (groupSignList != null) {
                groupSignList.remove(signEntry);

                if (groupSignList.isEmpty()) {
                    signsCache.remove(key);
                }
            }
        }
        signGroupKeyMap.remove(signEntry.key());
    }

    public synchronized void clear() {
        signsCache.clear();
        signGroupKeyMap.clear();
    }
}
