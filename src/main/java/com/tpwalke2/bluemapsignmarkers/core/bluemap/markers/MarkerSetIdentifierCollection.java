package com.tpwalke2.bluemapsignmarkers.core.bluemap.markers;

import com.tpwalke2.bluemapsignmarkers.core.WorldMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class MarkerSetIdentifierCollection {
    private final HashMap<String, Set<MarkerSetIdentifier>> mapByWorldId;
    private final HashMap<WorldMap, Set<MarkerSetIdentifier>> mapByMap;
    private final HashMap<MarkerType, Set<MarkerSetIdentifier>> mapByMarkerSetId;

    public MarkerSetIdentifierCollection() {
        mapByWorldId = new HashMap<>();
        mapByMap = new HashMap<>();
        mapByMarkerSetId = new HashMap<>();
    }

    public MarkerSetIdentifier getIdentifier(String worldId, WorldMap map, MarkerType markerSetId) {
        var byWorldId = Optional.ofNullable(mapByWorldId.get(worldId));
        var byMap = Optional.ofNullable(mapByMap.get(map));
        var byMarkerSetId = Optional.ofNullable(mapByMarkerSetId.get(markerSetId));

        if (byWorldId.isEmpty()
                || byMap.isEmpty()
                || byMarkerSetId.isEmpty()) return addIdentifier(worldId, map, markerSetId);

        var intersection = byWorldId.get()
                .stream()
                .filter(byMap.get()::contains)
                .filter(byMarkerSetId.get()::contains)
                .toList();

        return intersection.isEmpty()
                ? addIdentifier(worldId, map, markerSetId)
                : intersection.get(0);
    }

    private MarkerSetIdentifier addIdentifier(String worldId, WorldMap map, MarkerType markerSetId) {
        var markerKey = new MarkerSetIdentifier(worldId, map, markerSetId);

        mapByWorldId.putIfAbsent(markerKey.worldId(), new HashSet<>());
        mapByWorldId.get(markerKey.worldId()).add(markerKey);

        mapByMap.putIfAbsent(markerKey.map(), new HashSet<>());
        mapByMap.get(markerKey.map()).add(markerKey);

        mapByMarkerSetId.putIfAbsent(markerKey.markerType(), new HashSet<>());
        mapByMarkerSetId.get(markerKey.markerType()).add(markerKey);

        return markerKey;
    }
}
