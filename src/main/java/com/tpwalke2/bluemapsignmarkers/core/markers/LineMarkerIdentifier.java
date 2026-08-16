package com.tpwalke2.bluemapsignmarkers.core.markers;

public record LineMarkerIdentifier(String label, MarkerSetIdentifier parentSet) implements DispatchedMarkerIdentifier {
    public String getId() {
        return "line:" + label;
    }
}
