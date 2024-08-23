package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

public final class BMSMConfigV2 {

    private MarkerGroup[] markerGroups = new MarkerGroup[]{new MarkerGroup("[poi]", MarkerGroupType.POI, "Points of Interest", null, 0, 0)};
    private boolean silentLogs = false;

    public BMSMConfigV2() {}

    public BMSMConfigV2(MarkerGroup[] markerGroups, boolean silentLogs) {
        this.markerGroups = markerGroups;
        this.silentLogs = silentLogs;
    }
    public BMSMConfigV2(MarkerGroup markerGroup) {
        this.markerGroups = new MarkerGroup[]{markerGroup};
        this.silentLogs = false;
    }



    public MarkerGroup[] getMarkerGroups() {
        return markerGroups;
    }

    public boolean isSilentLogs() {
        return silentLogs;
    }
}
