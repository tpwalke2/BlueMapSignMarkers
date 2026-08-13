package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.DispatchedMarkerIdentifier;

public abstract class MarkerAction {
    private final DispatchedMarkerIdentifier markerIdentifier;

    protected MarkerAction(DispatchedMarkerIdentifier markerIdentifier) {
        this.markerIdentifier = markerIdentifier;
    }

    public DispatchedMarkerIdentifier getMarkerIdentifier() {
        return markerIdentifier;
    }

    @Override
    public String toString() {
        return "MarkerAction{" +
                "markerIdentifier=" + markerIdentifier +
                '}';
    }
}
