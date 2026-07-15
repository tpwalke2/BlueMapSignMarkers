package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SignRegionPartitioner {
    private SignRegionPartitioner() {
    }

    public static Map<SignRegionKey, List<SignEntry>> partition(List<SignEntry> signEntries) {
        var result = new LinkedHashMap<SignRegionKey, List<SignEntry>>();

        for (var signEntry : signEntries) {
            var key = signEntry.key();
            var regionKey = SignRegionKey.forPosition(key.parentMap(), key.x(), key.z());
            result.computeIfAbsent(regionKey, unused -> new ArrayList<>()).add(signEntry);
        }

        return result;
    }
}
