package com.tpwalke2.bluemapsignmarkers.core.markers;

import java.util.*;

public class MarkerSetIdentifierCollection {
    private final Map<String, Set<MarkerSetIdentifier>> mapByMap;
    private final Map<MarkerType, Set<MarkerSetIdentifier>> mapByMarkerSetId;

    public MarkerSetIdentifierCollection() {
        mapByMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        mapByMarkerSetId = new EnumMap<>(MarkerType.class);
    }

    public MarkerSetIdentifier getIdentifier(String mapId, MarkerType markerSetId) {
        var byMap = Optional.ofNullable(mapByMap.get(mapId));
        var byMarkerSetId = Optional.ofNullable(mapByMarkerSetId.get(markerSetId));

        if (byMap.isEmpty()
                || byMarkerSetId.isEmpty()) return addIdentifier(mapId, markerSetId);

        var intersection = byMap.get()
                .stream()
                .filter(byMarkerSetId.get()::contains)
                .toList();

        return intersection.isEmpty()
                ? addIdentifier(mapId, markerSetId)
                : intersection.get(0);
    }

    private MarkerSetIdentifier addIdentifier(String mapId, MarkerType markerSetId) {
        var markerKey = new MarkerSetIdentifier(mapId, markerSetId);

        mapByMap.putIfAbsent(markerKey.mapId(), new HashSet<>());
        mapByMap.get(markerKey.mapId()).add(markerKey);

        mapByMarkerSetId.putIfAbsent(markerKey.markerType(), new HashSet<>());
        mapByMarkerSetId.get(markerKey.markerType()).add(markerKey);

        return markerKey;
    }
}
