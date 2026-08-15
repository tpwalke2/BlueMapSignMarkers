package com.tpwalke2.bluemapsignmarkers.core.markers;

public interface DispatchedMarkerIdentifier {
    MarkerSetIdentifier parentSet();

    String getId();
}
