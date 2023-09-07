package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;

public class SignEntryHelper {
    private SignEntryHelper() {}

    public static boolean isMarkerType(SignEntry signEntry, MarkerType markerType) {
        return signEntry.frontTextLines().markerType() == markerType
                || signEntry.backTextLines().markerType() == markerType;
    }

    public static String getLabel(SignEntry signEntry) {
        if (!signEntry.frontTextLines().label().isBlank()) {
            return signEntry.frontTextLines().label();
        }

        return signEntry.backTextLines().label().isBlank() ? "" : signEntry.backTextLines().label();
    }

    public static String getDetail(SignEntry signEntry) {
        var frontDetail = signEntry.frontTextLines().detail();
        var backDetail = signEntry.backTextLines().detail();

        if (frontDetail.isBlank()) {
            return backDetail;
        }

        if (backDetail.isBlank()) {
            return frontDetail;
        }

        return String.format("FRONT: %s%nBACK: %s", frontDetail, backDetail);
    }
}
