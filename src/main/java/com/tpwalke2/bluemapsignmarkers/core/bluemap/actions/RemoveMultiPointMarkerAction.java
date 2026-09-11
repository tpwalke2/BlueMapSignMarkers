package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;

// Replaces the former RemoveLineMarkerAction/RemoveShapeMarkerAction/RemoveExtrudeMarkerAction - those
// three were structurally identical (differing only in toString()'s class-name literal), since
// MultiPointMarkerIdentifier's kind field already distinguishes line/shape/extrude.
public class RemoveMultiPointMarkerAction extends MarkerAction {
    public RemoveMultiPointMarkerAction(MultiPointMarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public String toString() {
        return "RemoveMultiPointMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
