package com.tpwalke2.bluemapsignmarkers.config.persistence;

import com.google.gson.JsonPrimitive;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;

public final class LoadingBMSMConfigV2 {
    public LoadingBMSMConfigV2() {
    }

    public LoadingBMSMConfigV2(LoadingMarkerGroupV2[] markerGroups) {
        this.markerGroups = markerGroups;
    }

    public LoadingBMSMConfigV2(LoadingMarkerGroupV2 markerGroup) {
        this.markerGroups = new LoadingMarkerGroupV2[]{markerGroup};
    }

    private LoadingMarkerGroupV2[] markerGroups = new LoadingMarkerGroupV2[]{defaultGroup()};

    private static LoadingMarkerGroupV2 defaultGroup() {
        var defaultGroup = MarkerGroup.DEFAULT_POI_GROUP;
        return new LoadingMarkerGroupV2(
                defaultGroup.prefix(),
                new JsonPrimitive(defaultGroup.matchType().name()),
                new JsonPrimitive(defaultGroup.type().name()),
                defaultGroup.name(),
                defaultGroup.icon(),
                new JsonPrimitive(defaultGroup.offsetX()),
                new JsonPrimitive(defaultGroup.offsetY()),
                new JsonPrimitive(defaultGroup.defaultHidden()),
                new JsonPrimitive(defaultGroup.minDistance()),
                new JsonPrimitive(defaultGroup.maxDistance()),
                new JsonPrimitive(defaultGroup.lineWidth()),
                defaultGroup.lineColor(),
                defaultGroup.fillColor(),
                new JsonPrimitive(defaultGroup.sorting()),
                defaultGroup.toggleable(),
                defaultGroup.depthTest(),
                defaultGroup.cssClasses(),
                defaultGroup.allowPlayerColors());
    }

    public LoadingMarkerGroupV2[] getMarkerGroups() {
        return markerGroups;
    }
}
