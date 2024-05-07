package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;

public class AddPOIMarkerAction extends MarkerAction {
    private final String label;
    private final String detail;

    public AddPOIMarkerAction(MarkerIdentifier markerIdentifier, String label, String detail) {
        super(markerIdentifier);
        this.label = label;
        this.detail = detail;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "AddMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                ", label='" + label + '\'' +
                ", detail='" + detail + '\'' +
                '}';
    }
}