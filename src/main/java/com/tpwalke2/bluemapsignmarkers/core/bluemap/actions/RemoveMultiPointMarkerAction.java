package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;

import java.util.Objects;

// Replaces the former RemoveLineMarkerAction/RemoveShapeMarkerAction/RemoveExtrudeMarkerAction - those
// three were byte-for-byte identical, since MultiPointMarkerIdentifier's kind field already distinguishes
// line/shape/extrude.
public class RemoveMultiPointMarkerAction extends MarkerAction {
    public RemoveMultiPointMarkerAction(MultiPointMarkerIdentifier markerIdentifier) {
        super(markerIdentifier);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RemoveMultiPointMarkerAction that)) {
            return false;
        }
        return Objects.equals(getMarkerIdentifier(), that.getMarkerIdentifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMarkerIdentifier());
    }

    @Override
    public String toString() {
        return "RemoveMultiPointMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
