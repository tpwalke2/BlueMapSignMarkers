package com.tpwalke2.bluemapsignmarkers.config.persistence;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

public final class LoadingBMSMConfigV2 {
    public LoadingBMSMConfigV2() {
    }

    public LoadingBMSMConfigV2(LoadingMarkerGroupV2[] markerGroups) {
        this.markerGroups = markerGroups;
    }

    public LoadingBMSMConfigV2(LoadingMarkerGroupV2 markerGroup) {
        this.markerGroups = new LoadingMarkerGroupV2[]{markerGroup};
    }

    private LoadingMarkerGroupV2[] markerGroups = new LoadingMarkerGroupV2[]{
            new LoadingMarkerGroupV2("[poi]",
                    MarkerGroupMatchType.STARTS_WITH,
                    MarkerGroupType.POI,
                    "Points of Interest",
                    null,
                    0,
                    0,
                    false,
                    0.0,
                    10000000.0)};

    public LoadingMarkerGroupV2[] getMarkerGroups() {
        return markerGroups;
    }
}
