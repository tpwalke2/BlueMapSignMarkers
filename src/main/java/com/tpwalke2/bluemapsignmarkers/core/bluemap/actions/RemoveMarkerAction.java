package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;

public class RemoveMarkerAction extends MarkerAction {
    public RemoveMarkerAction(MarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public String toString() {
        return "RemoveMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}