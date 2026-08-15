package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class LineGroupResolver {
    private LineGroupResolver() {
    }

    public static List<SignEntry> members(Collection<SignEntry> allSigns, String parentMap, String prefix, String label) {
        return allSigns.stream()
                .filter(e -> parentMap.equals(e.key().parentMap()))
                .filter(e -> prefix.equals(SignEntryHelper.getPrefix(e)))
                .filter(e -> label.equals(SignEntryHelper.getLabel(e)))
                .sorted(Comparator.comparingLong(SignEntry::createdAtMillis)
                        .thenComparingInt(e -> e.key().x())
                        .thenComparingInt(e -> e.key().y())
                        .thenComparingInt(e -> e.key().z()))
                .toList();
    }
}
