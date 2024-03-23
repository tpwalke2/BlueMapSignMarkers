package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

public class V2LoadingMarkerGroup {
    private final String prefix;
    private final MarkerGroupType type;
    private final String name;
    private final String icon;
    private final int offsetX;
    private final int offsetZ;

    public V2LoadingMarkerGroup(String prefix, MarkerGroupType type, String name, String icon, int offsetX, int offsetZ) {
        this.prefix = prefix;
        this.type = type;
        this.name = name;
        this.icon = icon;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    public V2LoadingMarkerGroup(String prefix, String type, String name, String icon, int offsetX, int offsetZ) {
        this(prefix, parseType(type), name, icon, offsetX, offsetZ);
    }

    public V2LoadingMarkerGroup(String prefix, String name, String icon) {
        this(prefix, MarkerGroupType.POI, name, icon, 0, 0);
    }

    public String getPrefix() {
        return prefix;
    }

    public MarkerGroupType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public static MarkerGroupType parseType(String type) {
        return MarkerGroupType.valueOf(type.toUpperCase());
    }
}
