package com.tpwalke2.bluemapsignmarkers.core.markers;

public record MarkerGroup(
        String prefix,
        MarkerGroupType type,
        String name,
        String icon,
        int offsetX,
        int offsetY,
        MarkerGroupColor lineColor,
        int lineWidth) {
    public MarkerGroup withType(MarkerGroupType type) {
        return new MarkerGroup(prefix, type, name, icon, offsetX, offsetY, lineColor, lineWidth);
    }
    public MarkerGroup withColorAndWidth(MarkerGroupColor lineColor, int lineWidth) {
        return new MarkerGroup(prefix, type, name, icon, offsetX, offsetY, lineColor, lineWidth);
    }
}
