package com.tpwalke2.bluemapsignmarkers.core.markers;

public enum MarkerType {
    POI("points_of_interest", "Points of Interest");
    public final String id;
    public final String label;

    MarkerType(String id, String label) {
        this.id = id;
        this.label = label;
    }
}
