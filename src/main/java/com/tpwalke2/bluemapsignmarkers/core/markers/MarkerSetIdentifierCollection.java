package com.tpwalke2.bluemapsignmarkers.core.markers;

import java.util.*;

public class MarkerSetIdentifierCollection {
    private final Map<String, Set<MarkerSetIdentifier>> mapByMap;
    private final Map<MarkerGroup, Set<MarkerSetIdentifier>> mapByMarkerSetId;

    public MarkerSetIdentifierCollection() {
        mapByMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        mapByMarkerSetId = new HashMap<>();
    }

    // synchronized so the "is this combo already cached?" check and the "cache it" write
    // (addIdentifier) are one atomic step. SignManager reads this via a volatile RuntimeConfig
    // snapshot, so it can be called both from the server thread (live sign edits, via the mixins)
    // and from whatever thread fires BlueMapAPI.onEnable/IResetHandler.reset() (replaying every
    // cached sign after a config reload) at the same time, against the same instance — without
    // this, concurrent first-time lookups for the same (mapId, markerGroup) could each miss the
    // cache and construct a distinct MarkerSetIdentifier, and the plain TreeMap/HashMap/HashSet
    // fields could corrupt under concurrent mutation (finding #16,
    // plans/codebase-review-2026-07-11.md).
    public synchronized MarkerSetIdentifier getIdentifier(String mapId, MarkerGroup markerGroup) {
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
