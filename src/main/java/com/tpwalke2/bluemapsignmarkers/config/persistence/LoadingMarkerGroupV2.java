package com.tpwalke2.bluemapsignmarkers.config.persistence;

import com.google.gson.JsonElement;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

import java.util.List;

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
        Double maxDistance,
        Integer lineWidth,
        String lineColor,
        String fillColor,
        // JsonElement (rather than Integer) so a non-integer JSON value doesn't fail the whole config's Gson
        // parse - resolveSorting in ConfigProvider validates it manually and falls back to the default on a
        // malformed value, the same "never crash on a bad field" treatment lineWidth/lineColor get downstream.
        JsonElement sorting,
        Boolean toggleable,
        Boolean depthTest,
        List<String> cssClasses) {
}
