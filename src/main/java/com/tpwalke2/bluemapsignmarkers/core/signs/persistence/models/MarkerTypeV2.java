package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models;

public enum MarkerTypeV2 {
    POI("points_of_interest", "Points of Interest");
    public final String id;
    public final String label;

    MarkerTypeV2(String id, String label) {
        this.id = id;
        this.label = label;
    }
}
