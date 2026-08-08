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
        if (prefix == null) return false;
        var group = prefixGroupMap.get(prefix);
        return group != null && group.type() == markerGroupType;
    }

    public static String getLabel(SignEntry signEntry) {
        if (!signEntry.frontText().label().isBlank()) {
            return signEntry.frontText().label();
        }

        return signEntry.backText().label().isBlank() ? "" : signEntry.backText().label();
    }

    public static String getDetail(SignEntry signEntry) {
        var frontPrefix = signEntry.frontText().prefix();
        var backPrefix = signEntry.backText().prefix();

        // Front and back matched different marker groups: the marker itself belongs to the front's
        // group (see getPrefix), so only the front's detail is shown - merging in the back's detail
        // would attribute text from a group the marker doesn't belong to.
        if (frontPrefix != null && backPrefix != null && !frontPrefix.equals(backPrefix)) {
            return signEntry.frontText().detail();
        }

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
