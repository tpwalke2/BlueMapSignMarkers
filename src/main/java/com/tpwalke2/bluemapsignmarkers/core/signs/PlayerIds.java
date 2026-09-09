package com.tpwalke2.bluemapsignmarkers.core.signs;

// Sentinel for "no player id known" - a live sign edit came from a hook with no Player in scope (a
// dye/ink-sac/glow-ink-sac click via SignBlockEntityInject.onUpdateText, or a sign loading via
// BlueMapSignMarkersMod.onBlockEntityLoad). Deliberately a separate constant from WorldMap.UNKNOWN
// (a different sentinel: no dimension/world known) even though both currently hold the same string -
// that value must stay "unknown" regardless, since it's already persisted as a real playerId in
// existing save files and SignManager.addOrUpdateSign compares against it by equality.
public class PlayerIds {
    public static final String UNKNOWN = "unknown";

    private PlayerIds() {}
}
