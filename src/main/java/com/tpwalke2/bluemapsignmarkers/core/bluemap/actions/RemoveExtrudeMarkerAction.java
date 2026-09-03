package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.ExtrudeMarkerIdentifier;

public class RemoveExtrudeMarkerAction extends MarkerAction {
    public RemoveExtrudeMarkerAction(ExtrudeMarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public String toString() {
        return "RemoveExtrudeMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
