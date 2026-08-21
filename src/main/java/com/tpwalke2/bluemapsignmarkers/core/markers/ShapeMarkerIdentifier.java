package com.tpwalke2.bluemapsignmarkers.core.markers;

public record ShapeMarkerIdentifier(String label, MarkerSetIdentifier parentSet) implements DispatchedMarkerIdentifier {
    public String getId() {
        return "shape:" + label;
    }
}
