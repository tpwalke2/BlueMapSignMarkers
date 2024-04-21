package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public final class BMSMConfigV2 {
    public BMSMConfigV2() {}
    public BMSMConfigV2(MarkerGroup[] markerGroups) {
        this.markerGroups = markerGroups;
    }
    public BMSMConfigV2(MarkerGroup markerGroup) {
        this.markerGroups = new MarkerGroup[]{markerGroup};
    }

    private MarkerGroup[] markerGroups = new MarkerGroup[]{new MarkerGroup("[poi]", MarkerGroupType.POI, "Points of Interest", null, 0, 0)};

    public MarkerGroup[] getMarkerGroups() {
        return markerGroups;
    }
}
