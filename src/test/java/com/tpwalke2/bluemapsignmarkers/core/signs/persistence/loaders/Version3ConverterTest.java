package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.MarkerTypeV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignLinesParseResultV2;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void convertToV3FabricatesThePrefixFromThePoiGroupOnBothSides() {
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

    // Documents review finding #6: convertToV3 ignores each side's own markerType entirely and always fabricates
    // the POI group's prefix, even for a side that never matched any group originally (markerType null). A back
    // side that was blank/unmatched under V2 still comes out of migration labeled as if it matched the POI group.
    @Test
    void aNonMatchingSideStillGetsFabricatedThePoiPrefix() {
        var entry = entryWith(
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Front Label", "front detail"),
                new SignLinesParseResultV2(null, "", ""));

        var converted = Version3Converter.convertToV3(entry, new MarkerGroup[]{poiGroup("[poi]")});

        assertEquals("[poi]", converted.backText().prefix());
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

    // Documents review finding #6: with zero POI-type groups configured, the orElseThrow() on the empty stream
    // crashes the whole migration with a NoSuchElementException instead of failing gracefully (e.g. falling back
    // to no prefix, or skipping the entry) - current behavior, not a desired one.
    @Test
    void throwsNoSuchElementExceptionWhenNoPoiGroupIsConfigured() {
        var entry = entryWith(
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Label", "detail"),
                new SignLinesParseResultV2(null, "", ""));

        assertThrows(NoSuchElementException.class,
                () -> Version3Converter.convertToV3(entry, new MarkerGroup[0]));
    }
}
