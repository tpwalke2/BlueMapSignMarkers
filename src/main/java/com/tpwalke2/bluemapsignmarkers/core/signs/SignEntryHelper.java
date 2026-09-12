package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;

import java.util.Map;

public class SignEntryHelper {
    // The dye name a sign side has when it's never been dyed (SignText.getColor() defaults to
    // DyeColor.BLACK) - also what Version6Converter backfills pre-V6 entries to, so both a genuinely
    // undyed sign and a migrated one read as "no player color chosen" with no null-handling.
    public static final String UNDYED_DYE = "BLACK";

    private SignEntryHelper() {
    }

    public static String getPrefix(SignEntry signEntry) {
        return signEntry.frontText().prefix() != null ? signEntry.frontText().prefix() : signEntry.backText().prefix();
    }

    public static boolean isMarkerType(
            String prefix,
            Map<String, MarkerGroup> prefixGroupMap,
            MarkerGroupType markerGroupType) {
        if (prefix == null) return false;
        var group = prefixGroupMap.get(prefix);
        return group != null && group.type() == markerGroupType;
    }

    // The sign's dye follows the same side-selection rule as getPrefix: whichever side produced the sign's
    // matching representation (front, unless only the back matched a group) is the side whose dye counts.
    public static String getDye(SignEntry signEntry) {
        return signEntry.frontText().prefix() != null ? signEntry.frontDye() : signEntry.backDye();
    }

    public static String getLabel(SignEntry signEntry) {
        if (!signEntry.frontText().label().isBlank()) {
            return signEntry.frontText().label();
        }

        var frontPrefix = signEntry.frontText().prefix();
        var backPrefix = signEntry.backText().prefix();

        // Front and back matched different marker groups: the marker belongs to the front's group
        // (see getPrefix), so a blank front label must not fall back to the back's label - that
        // label belongs to a group the marker doesn't represent.
        if (frontPrefix != null && backPrefix != null && !frontPrefix.equals(backPrefix)) {
            return "";
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
