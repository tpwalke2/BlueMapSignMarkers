package com.tpwalke2.bluemapsignmarkers.core.markers;

public record MarkerGroup(
        String prefix,
        MarkerGroupMatchType matchType,
        MarkerGroupType type,
        String name,
        String icon,
        int offsetX,
        int offsetY,
        boolean defaultHidden,
        double minDistance,
        double maxDistance,
        int lineWidth,
        String lineColor) {
    public static final MarkerGroup DEFAULT_POI_GROUP = new MarkerGroup(
            "[poi]",
            MarkerGroupMatchType.STARTS_WITH,
            MarkerGroupType.POI,
            "Points of Interest",
            null,
            0,
            0,
            false,
            0.0,
            10000000.0,
            2,
            "#FF0000FF");

    public MarkerGroup withType(MarkerGroupType type) {
        return new MarkerGroup(prefix, matchType, type, name, icon, offsetX, offsetY, defaultHidden, minDistance, maxDistance, lineWidth, lineColor);
    }
}
