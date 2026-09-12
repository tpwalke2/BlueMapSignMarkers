package com.tpwalke2.bluemapsignmarkers.core.markers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MarkerSetIdentifierCollection {
    private final Map<Key, MarkerSetIdentifier> identifiers = new HashMap<>();

    // synchronized so the "is this combo already cached?" check and the "cache it" write are one atomic
    // step. SignManager reads this via a volatile RuntimeConfig snapshot, so it can be called both from the
    // server thread (live sign edits, via the mixins) and from whatever thread fires
    // BlueMapAPI.onEnable/IResetHandler.reset() (replaying every cached sign after a config reload) at the
    // same time, against the same instance - without this, concurrent first-time lookups for the same
    // (mapId, markerGroup) could each miss the cache and construct a distinct MarkerSetIdentifier, and the
    // plain HashMap field could corrupt under concurrent mutation (finding #16, plans/codebase-review-2026-07-11.md).
    public synchronized MarkerSetIdentifier getIdentifier(String mapId, MarkerGroup markerGroup) {
        if (mapId == null) return null;

        // Keyed by lowercased mapId so map-id matching stays case-insensitive; the cached identifier itself
        // still carries whichever casing was passed in on its first call for this combo (deliberate - see
        // spec.md's "No action" section on finding #60).
        return identifiers.computeIfAbsent(
                new Key(mapId.toLowerCase(Locale.ROOT), markerGroup),
                k -> new MarkerSetIdentifier(mapId, markerGroup));
    }

    private record Key(String mapIdLower, MarkerGroup markerGroup) {}
}
