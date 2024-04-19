package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignLinesParseResultV2;

import java.util.Arrays;

public class Version3Converter {
    private Version3Converter() {
    }

    public static SignEntry convertToV3(SignEntryV2 entry, MarkerGroup[] markerGroups) {
        return new SignEntry(
                entry.key(),
                entry.playerId(),
                convertToV3(entry.frontText(), markerGroups),
                convertToV3(entry.backText(), markerGroups)
        );
    }

    private static SignLinesParseResult convertToV3(SignLinesParseResultV2 result, MarkerGroup[] markerGroups) {
        var markerGroup = Arrays.stream(markerGroups).filter(markerGroup1 -> markerGroup1.type() == MarkerGroupType.POI).findFirst().orElseThrow();
        return new SignLinesParseResult(
                markerGroup.prefix(),
                result.label(),
                result.detail()
        );
    }
}
