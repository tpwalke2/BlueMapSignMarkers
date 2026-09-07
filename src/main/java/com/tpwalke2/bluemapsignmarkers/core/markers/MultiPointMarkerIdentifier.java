package com.tpwalke2.bluemapsignmarkers.core.markers;

// Unifies what used to be three near-identical records (LineMarkerIdentifier, ShapeMarkerIdentifier,
// ExtrudeMarkerIdentifier), differing only by a hardcoded id-prefix string in getId().
public record MultiPointMarkerIdentifier(String kind, String label, MarkerSetIdentifier parentSet) implements DispatchedMarkerIdentifier {
    public String getId() {
        return kind + ":" + label;
    }
}
