package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import de.bluecolored.bluemap.api.markers.Marker;

public record MarkerEntry(MarkerCollection markerSet, MarkerOperation markerOperation, Marker marker) {
}
