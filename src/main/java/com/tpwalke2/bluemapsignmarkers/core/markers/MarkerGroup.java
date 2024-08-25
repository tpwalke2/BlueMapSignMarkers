package com.tpwalke2.bluemapsignmarkers.core.markers;

public record MarkerGroup(String prefix, MarkerGroupMatchType matchType, MarkerGroupType type, String name, String icon, int offsetX, int offsetY) {
    public MarkerGroup withType(MarkerGroupType type) {
        return new MarkerGroup(prefix, matchType, type, name, icon, offsetX, offsetY);
    }
}
