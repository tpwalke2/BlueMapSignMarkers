package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.markers.MarkerIdentifier;

public class AddMarkerAction extends MarkerAction {
    private final String label;
    private final String detail;

    public AddMarkerAction(MarkerIdentifier markerIdentifier, String label, String detail) {
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
}