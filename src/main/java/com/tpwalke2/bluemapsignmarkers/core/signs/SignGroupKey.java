package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;

public record SignGroupKey(
        String parentMap,
        MarkerGroup markerGroup,
        String name) {
}
