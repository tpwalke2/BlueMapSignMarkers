package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.MarkerTypeV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignLinesParseResultV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Version3ConverterTest {

    private static final SignEntryKey KEY = new SignEntryKey(1, 64, 2, "minecraft:overworld");

    private static MarkerGroup poiGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, prefix, null, 0, 0, false, 0, 0);
    }

    private static SignEntryV2 entryWith(SignLinesParseResultV2 front, SignLinesParseResultV2 back) {
        return new SignEntryV2(KEY, "player-1", front, back);
    }

    @Test
    void convertToV3UsesThePoiGroupPrefixWhenBothSidesMatched() {
        var entry = entryWith(
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Front Label", "front detail"),
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Back Label", "back detail"));

        var converted = Version3Converter.convertToV3(entry, new MarkerGroup[]{poiGroup("[poi]")});

        assertEquals(KEY, converted.key());
        assertEquals("player-1", converted.playerId());
        assertEquals("[poi]", converted.frontText().prefix());
        assertEquals("Front Label", converted.frontText().label());
        assertEquals("front detail", converted.frontText().detail());
        assertEquals("[poi]", converted.backText().prefix());
        assertEquals("Back Label", converted.backText().label());
        assertEquals("back detail", converted.backText().detail());
    }

    // GitHub issue #138 (review finding #6, part a): a side whose V2 markerType was null never matched any
    // group originally, so migration must keep it non-matching rather than fabricating the POI group's prefix.
    @Test
    void aNonMatchingSideStaysNonMatching() {
        var entry = entryWith(
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Front Label", "front detail"),
                new SignLinesParseResultV2(null, "", ""));

        var converted = Version3Converter.convertToV3(entry, new MarkerGroup[]{poiGroup("[poi]")});

        assertNull(converted.backText().prefix());
    }

    // Documents review finding #6: with more than one POI-type group configured, convertToV3 always picks
    // Arrays.stream(...).findFirst() - i.e. whichever POI group happens to be first in array order - silently
    // reassigning every migrated entry to that group's prefix regardless of which group the sign actually matched.
    @Test
    void whenMultiplePoiGroupsAreConfiguredTheFirstInArrayOrderWins() {
        var entry = entryWith(
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Label", "detail"),
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Label", "detail"));
        var groups = new MarkerGroup[]{poiGroup("[first]"), poiGroup("[second]")};

        var converted = Version3Converter.convertToV3(entry, groups);

        assertEquals("[first]", converted.frontText().prefix());
        assertEquals("[first]", converted.backText().prefix());
    }

    // Github issue #138 (review finding #6, part c): with zero POI-type groups configured, convertToV3 must
    // fail gracefully (non-matching prefix) instead of throwing NoSuchElementException and losing every
    // persisted sign for the session via LegacySignFileMigrator's catch-and-discard.
    @Test
    void treatsAMatchedSideAsNonMatchingWhenNoPoiGroupIsConfigured() {
        var entry = entryWith(
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Label", "detail"),
                new SignLinesParseResultV2(null, "", ""));

        var converted = Version3Converter.convertToV3(entry, new MarkerGroup[0]);

        assertNull(converted.frontText().prefix());
        assertNull(converted.backText().prefix());
    }
}
