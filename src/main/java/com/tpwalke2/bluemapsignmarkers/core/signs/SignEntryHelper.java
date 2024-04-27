package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

import java.util.Map;

public class SignEntryHelper {
    private SignEntryHelper() {
    }

    public static String getPrefix(SignEntry signEntry) {
        return signEntry.frontText().prefix() != null ? signEntry.frontText().prefix() : signEntry.backText().prefix();
    }

    public static boolean isMarkerType(
            SignEntry signEntry,
            Map<String, MarkerGroup> prefixGroupMap,
            MarkerGroupType markerGroupType) {
        var prefix = getPrefix(signEntry);
        return prefix != null && prefixGroupMap.get(prefix).type() == markerGroupType;
    }

    public static String getLabel(SignEntry signEntry) {
        if (!signEntry.frontText().label().isBlank()) {
            return signEntry.frontText().label();
        }

        return signEntry.backText().label().isBlank() ? "" : signEntry.backText().label();
    }

    public static String getDetail(SignEntry signEntry) {
        var frontDetail = signEntry.frontText().detail();
        var backDetail = signEntry.backText().detail();

        if (frontDetail.isBlank()) {
            return backDetail;
        }

        if (backDetail.isBlank()) {
            return frontDetail;
        }

        return String.format("FRONT: %s%nBACK: %s", frontDetail, backDetail);
    }
}
