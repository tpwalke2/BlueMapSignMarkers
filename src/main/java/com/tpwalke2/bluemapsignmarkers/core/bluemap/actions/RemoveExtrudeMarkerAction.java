package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;

public class RemoveExtrudeMarkerAction extends MarkerAction {
    public RemoveExtrudeMarkerAction(MultiPointMarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public String toString() {
        return "RemoveExtrudeMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
