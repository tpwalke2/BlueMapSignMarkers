package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;

public class RemoveLineMarkerAction extends MarkerAction {
    public RemoveLineMarkerAction(MultiPointMarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public String toString() {
        return "RemoveLineMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
