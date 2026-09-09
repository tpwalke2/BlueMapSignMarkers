package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;

public class RemoveShapeMarkerAction extends MarkerAction {
    public RemoveShapeMarkerAction(MultiPointMarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public String toString() {
        return "RemoveShapeMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
