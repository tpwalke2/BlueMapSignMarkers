package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignEntryHelperTest {

    private static final SignEntryKey KEY = new SignEntryKey(1, 2, 3, "minecraft:overworld");

    private static MarkerGroup poiGroup(String prefix) {
        return new MarkerGroup(prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "Points of Interest", null, 0, 0, false, 0.0, 10000000.0);
    }

    private static SignEntry signEntry(SignLinesParseResult frontText, SignLinesParseResult backText) {
        return new SignEntry(KEY, "unknown", frontText, backText);
    }

    private static SignLinesParseResult parsed(String prefix, String label, String detail) {
        return new SignLinesParseResult(prefix, label, detail);
    }

    private static SignLinesParseResult empty() {
        return new SignLinesParseResult(null, "", "");
    }

    @Test
    void getPrefixPrefersFrontText() {
        var entry = signEntry(parsed("[poi]", "Town Hall", "Town Hall"), parsed("[event]", "Fair", "Fair"));

        assertEquals("[poi]", SignEntryHelper.getPrefix(entry));
    }

    @Test
    void getPrefixFallsBackToBackText() {
        var entry = signEntry(empty(), parsed("[event]", "Fair", "Fair"));

        assertEquals("[event]", SignEntryHelper.getPrefix(entry));
    }

    @Test
    void getPrefixReturnsNullWhenNeitherSideMatches() {
        var entry = signEntry(empty(), empty());

        assertNull(SignEntryHelper.getPrefix(entry));
    }

    @Test
    void isMarkerTypeTrueWhenPrefixResolvesToMatchingType() {
        var entry = signEntry(parsed("[poi]", "Town Hall", "Town Hall"), empty());
        var prefixGroupMap = Map.of("[poi]", poiGroup("[poi]"));

        assertTrue(SignEntryHelper.isMarkerType(entry, prefixGroupMap, MarkerGroupType.POI));
    }

    @Test
    void isMarkerTypeFalseWhenNoPrefix() {
        var entry = signEntry(empty(), empty());
        var prefixGroupMap = Map.of("[poi]", poiGroup("[poi]"));

        assertFalse(SignEntryHelper.isMarkerType(entry, prefixGroupMap, MarkerGroupType.POI));
    }

    @Test
    void isMarkerTypeFalseWhenPrefixNoLongerConfigured() {
        // Simulates a cached sign whose prefix was removed/renamed out of the config on reload -
        // prefixGroupMap.get(prefix) is null, and isMarkerType must not throw.
        var entry = signEntry(parsed("[poi]", "Town Hall", "Town Hall"), empty());
        var prefixGroupMap = Map.<String, MarkerGroup>of();

        assertFalse(SignEntryHelper.isMarkerType(entry, prefixGroupMap, MarkerGroupType.POI));
    }

    @Test
    void getLabelPrefersFrontText() {
        var entry = signEntry(parsed("[poi]", "Town Hall", "Town Hall"), parsed("[poi]", "Back Label", "Back Label"));

        assertEquals("Town Hall", SignEntryHelper.getLabel(entry));
    }

    @Test
    void getLabelFallsBackToBackTextWhenFrontBlank() {
        var entry = signEntry(empty(), parsed("[poi]", "Back Label", "Back Label"));

        assertEquals("Back Label", SignEntryHelper.getLabel(entry));
    }

    @Test
    void getLabelReturnsEmptyWhenBothBlank() {
        var entry = signEntry(empty(), empty());

        assertEquals("", SignEntryHelper.getLabel(entry));
    }

    @Test
    void getDetailReturnsFrontOnlyWhenBackBlank() {
        var entry = signEntry(parsed("[poi]", "Town Hall", "Open 9-5"), empty());

        assertEquals("Open 9-5", SignEntryHelper.getDetail(entry));
    }

    @Test
    void getDetailReturnsBackOnlyWhenFrontBlank() {
        var entry = signEntry(empty(), parsed("[poi]", "Fair", "Ask for Bob"));

        assertEquals("Ask for Bob", SignEntryHelper.getDetail(entry));
    }

    @Test
    void getDetailCombinesBothSidesWhenNeitherBlank() {
        var entry = signEntry(parsed("[poi]", "Town Hall", "Open 9-5"), parsed("[poi]", "Fair", "Ask for Bob"));

        assertEquals(String.format("FRONT: %s%nBACK: %s", "Open 9-5", "Ask for Bob"), SignEntryHelper.getDetail(entry));
    }
}
