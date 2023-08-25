package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.markers.MarkerIdentifier;

public class UpdateMarkerAction extends MarkerAction {
    private final String newLabel;
    private final String newDetails;

    public UpdateMarkerAction(MarkerIdentifier markerIdentifier, String newLabel, String newDetail) {
        super(markerIdentifier);
        this.newLabel = newLabel;
        this.newDetails = newDetail;
    }

    public String getNewLabel() {
        return newLabel;
    }

    public String getNewDetails() {
        return newDetails;
    }
}