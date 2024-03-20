package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public class BMSMConfigV2 {
    private MarkerGroup[] markerGroups = new MarkerGroup[]{new MarkerGroup("[poi]", MarkerGroupType.POI, "Points of Interest", null, 0, 0)};

    public MarkerGroup[] getMarkerGroups() {
        return markerGroups;
    }
}
