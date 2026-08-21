package com.tpwalke2.bluemapsignmarkers.config.persistence;

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
                defaultGroup.matchType(),
                defaultGroup.type(),
                defaultGroup.name(),
                defaultGroup.icon(),
                defaultGroup.offsetX(),
                defaultGroup.offsetY(),
                defaultGroup.defaultHidden(),
                defaultGroup.minDistance(),
                defaultGroup.maxDistance(),
                defaultGroup.lineWidth(),
                defaultGroup.lineColor(),
                defaultGroup.fillColor());
    }

    public LoadingMarkerGroupV2[] getMarkerGroups() {
        return markerGroups;
    }
}
