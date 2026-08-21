package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.ShapeMarkerIdentifier;

public class RemoveShapeMarkerAction extends MarkerAction {
    public RemoveShapeMarkerAction(ShapeMarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public String toString() {
        return "RemoveShapeMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
