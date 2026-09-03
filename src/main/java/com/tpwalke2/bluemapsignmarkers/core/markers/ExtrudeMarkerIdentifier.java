package com.tpwalke2.bluemapsignmarkers.core.markers;

public record ExtrudeMarkerIdentifier(String label, MarkerSetIdentifier parentSet) implements DispatchedMarkerIdentifier {
    public String getId() {
        return "extrude:" + label;
    }
}
