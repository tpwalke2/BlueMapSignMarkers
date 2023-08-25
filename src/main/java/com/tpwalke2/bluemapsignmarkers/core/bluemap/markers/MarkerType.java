package com.tpwalke2.bluemapsignmarkers.core.bluemap.markers;

public enum MarkerType {
    POI("points_of_interest", "Points of Interest", "[map]");
    public final String id;
    public final String label;
    public final String prefix;

    MarkerType(String id, String label, String prefix) {
        this.id = id;
        this.label = label;
        this.prefix = prefix;
    }
}
