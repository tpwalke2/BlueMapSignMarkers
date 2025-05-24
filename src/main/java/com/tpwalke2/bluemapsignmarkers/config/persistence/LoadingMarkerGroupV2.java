package com.tpwalke2.bluemapsignmarkers.config.persistence;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

public record LoadingMarkerGroupV2(
        String prefix,
        MarkerGroupMatchType matchType,
        MarkerGroupType type,
        String name,
        String icon,
        Integer offsetX,
        Integer offsetY,
        Boolean defaultHidden,
        Double minDistance,
        Double maxDistance) {
}
