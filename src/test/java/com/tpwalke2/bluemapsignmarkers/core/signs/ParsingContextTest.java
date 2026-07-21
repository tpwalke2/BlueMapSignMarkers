package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParsingContextTest {

    @Test
    void buildResultReturnsTheSentinelWhenNoMarkerGroupIsSet() {
        var context = new ParsingContext();

        var result = context.buildResult();

        assertNull(result.prefix());
        assertEquals("", result.label());
        assertEquals("", result.detail());
    }

    @Test
    void buildResultUsesTheMarkerGroupPrefixAndTheLabelWhenAGroupIsSet() {
        var context = new ParsingContext();
        context.setMarkerGroup(markerGroup("[poi]"));
        context.setLabel("My Label");

        var result = context.buildResult();

        assertEquals("[poi]", result.prefix());
        assertEquals("My Label", result.label());
    }

    @Test
    void buildResultDetailIsEmptyWhenAppendDetailWasNeverCalled() {
        var context = new ParsingContext();
        context.setMarkerGroup(markerGroup("[poi]"));

        assertEquals("", context.buildResult().detail());
    }

    @Test
    void appendDetailJoinsMultipleAppendsWithNewlines() {
        var context = new ParsingContext();
        context.setMarkerGroup(markerGroup("[poi]"));
        context.appendDetail("line1");
        context.appendDetail("line2");

        assertEquals("line1\nline2", context.buildResult().detail());
    }

    @Test
    void appendDetailsOverallTrimOnlyStripsTheOutermostWhitespaceNotInternalPadding() {
        var context = new ParsingContext();
        context.setMarkerGroup(markerGroup("[poi]"));
        context.appendDetail(" line1 ");
        context.appendDetail(" line2 ");

        assertEquals("line1 \n line2", context.buildResult().detail());
    }

    private static MarkerGroup markerGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, prefix, "icon.png", 0, 0, false, 0, 0);
    }
}
