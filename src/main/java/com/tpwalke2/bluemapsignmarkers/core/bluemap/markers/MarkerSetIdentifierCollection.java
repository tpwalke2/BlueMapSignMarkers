package com.tpwalke2.bluemapsignmarkers.core.bluemap.markers;

import com.tpwalke2.bluemapsignmarkers.core.WorldMap;

import java.util.*;

public class MarkerSetIdentifierCollection {
    private final Map<WorldMap, Set<MarkerSetIdentifier>> mapByMap;
    private final Map<MarkerType, Set<MarkerSetIdentifier>> mapByMarkerSetId;

    public MarkerSetIdentifierCollection() {
        mapByMap = new EnumMap<>(WorldMap.class);
        mapByMarkerSetId = new EnumMap<>(MarkerType.class);
    }

    public MarkerSetIdentifier getIdentifier(WorldMap map, MarkerType markerSetId) {
        var byMap = Optional.ofNullable(mapByMap.get(map));
        var byMarkerSetId = Optional.ofNullable(mapByMarkerSetId.get(markerSetId));

        if (byMap.isEmpty()
                || byMarkerSetId.isEmpty()) return addIdentifier(map, markerSetId);

        var intersection = byMap.get()
                .stream()
                .filter(byMarkerSetId.get()::contains)
                .toList();

        return intersection.isEmpty()
                ? addIdentifier(map, markerSetId)
                : intersection.get(0);
    }

    private MarkerSetIdentifier addIdentifier(WorldMap map, MarkerType markerSetId) {
        var markerKey = new MarkerSetIdentifier(map, markerSetId);

        mapByMap.putIfAbsent(markerKey.map(), new HashSet<>());
        mapByMap.get(markerKey.map()).add(markerKey);

        mapByMarkerSetId.putIfAbsent(markerKey.markerType(), new HashSet<>());
        mapByMarkerSetId.get(markerKey.markerType()).add(markerKey);

        return markerKey;
    }
}
