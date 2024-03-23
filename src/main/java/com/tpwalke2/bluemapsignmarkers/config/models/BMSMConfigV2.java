package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public final class BMSMConfigV2 {
    public BMSMConfigV2() {}
    public BMSMConfigV2(V2LoadingMarkerGroup[] markerGroups) {
        this.markerGroups = convert(markerGroups);
    }
    public BMSMConfigV2(MarkerGroup markerGroup) {
        this.markerGroups = new MarkerGroup[]{markerGroup};
    }

    public static MarkerGroup convert(V2LoadingMarkerGroup v2LoadingMarkerGroup) {
        return new MarkerGroup(v2LoadingMarkerGroup.getPrefix(), v2LoadingMarkerGroup.getType(), v2LoadingMarkerGroup.getName(), v2LoadingMarkerGroup.getIcon(), v2LoadingMarkerGroup.getOffsetX(), v2LoadingMarkerGroup.getOffsetZ());
    }

    public static MarkerGroup[] convert(V2LoadingMarkerGroup[] v2LoadingMarkerGroups) {
        MarkerGroup[] markerGroups = new MarkerGroup[v2LoadingMarkerGroups.length];
        for (int i = 0; i < v2LoadingMarkerGroups.length; i++) {
            markerGroups[i] = convert(v2LoadingMarkerGroups[i]);
        }
        return markerGroups;
    }

    private MarkerGroup[] markerGroups = new MarkerGroup[]{new MarkerGroup("[poi]", MarkerGroupType.POI, "Points of Interest", null, 0, 0)};

    public MarkerGroup[] getMarkerGroups() {
        return markerGroups;
    }
}
