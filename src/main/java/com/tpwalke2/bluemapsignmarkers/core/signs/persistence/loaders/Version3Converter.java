package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignLinesParseResultV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Version3Converter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

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
        // A null markerType means this side never matched any group under V1/V2 - keep it non-matching
        // rather than fabricating a prefix for it (GitHub issue #138).
        if (result.markerType() == null) {
            return new SignLinesParseResult(null, result.label(), result.detail());
        }

        var markerGroup = Arrays.stream(markerGroups)
                .filter(group -> group.type() == MarkerGroupType.POI)
                .findFirst();

        if (markerGroup.isEmpty()) {
            LOGGER.warn("V2 sign data matched a POI marker but no POI-type marker group is configured; " +
                    "treating it as non-matching during migration");
            return new SignLinesParseResult(null, result.label(), result.detail());
        }

        return new SignLinesParseResult(
                markerGroup.get().prefix(),
                result.label(),
                result.detail()
        );
    }
}
