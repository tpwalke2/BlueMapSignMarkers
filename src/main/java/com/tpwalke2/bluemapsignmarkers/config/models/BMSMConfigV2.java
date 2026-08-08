package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;

public final class BMSMConfigV2 {
    public BMSMConfigV2() {}

    public BMSMConfigV2(MarkerGroup[] markerGroups) {
        this.markerGroups = markerGroups;
    }

    public BMSMConfigV2(MarkerGroup markerGroup) {
        this.markerGroups = new MarkerGroup[]{markerGroup};
    }

    private MarkerGroup[] markerGroups = new MarkerGroup[]{MarkerGroup.DEFAULT_POI_GROUP};

    public MarkerGroup[] getMarkerGroups() {
        return markerGroups;
    }
}
