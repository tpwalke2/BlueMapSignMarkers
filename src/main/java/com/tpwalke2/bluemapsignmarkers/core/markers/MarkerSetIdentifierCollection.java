package com.tpwalke2.bluemapsignmarkers.core.markers;

import java.util.*;

public class MarkerSetIdentifierCollection {
    private final Map<String, Set<MarkerSetIdentifier>> mapByMap;
    private final Map<MarkerGroup, Set<MarkerSetIdentifier>> mapByMarkerSetId;

    public MarkerSetIdentifierCollection() {
        mapByMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        mapByMarkerSetId = new HashMap<>();
    }

    public MarkerSetIdentifier getIdentifier(String mapId, MarkerGroup markerGroup) {
        var byMap = Optional.ofNullable(mapByMap.get(mapId));
        var byMarkerSetId = Optional.ofNullable(mapByMarkerSetId.get(markerGroup));

        if (byMap.isEmpty()
                || byMarkerSetId.isEmpty()) return addIdentifier(mapId, markerGroup);

        var intersection = byMap.get()
                .stream()
                .filter(byMarkerSetId.get()::contains)
                .toList();

        return intersection.isEmpty()
                ? addIdentifier(mapId, markerGroup)
                : intersection.get(0);
    }

    private MarkerSetIdentifier addIdentifier(String mapId, MarkerGroup markerGroup) {
        var markerKey = new MarkerSetIdentifier(mapId, markerGroup);

        mapByMap.putIfAbsent(markerKey.mapId(), new HashSet<>());
        mapByMap.get(markerKey.mapId()).add(markerKey);

        mapByMarkerSetId.putIfAbsent(markerKey.markerGroup(), new HashSet<>());
        mapByMarkerSetId.get(markerKey.markerGroup()).add(markerKey);

        return markerKey;
    }
}
