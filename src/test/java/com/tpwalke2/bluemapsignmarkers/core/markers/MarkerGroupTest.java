package com.tpwalke2.bluemapsignmarkers.core.markers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerGroupTest {

    @Test
    void defaultPoiGroupHasExpectedValues() {
        var group = MarkerGroup.DEFAULT_POI_GROUP;

        assertEquals("[poi]", group.prefix());
        assertEquals(MarkerGroupMatchType.STARTS_WITH, group.matchType());
        assertEquals(MarkerGroupType.POI, group.type());
        assertEquals("Points of Interest", group.name());
        assertEquals(null, group.icon());
        assertEquals(0, group.offsetX());
        assertEquals(0, group.offsetY());
        assertFalse(group.defaultHidden());
        assertEquals(0.0, group.minDistance());
        assertEquals(10000000.0, group.maxDistance());
        assertEquals(2, group.lineWidth());
        assertEquals("#FF0000FF", group.lineColor());
        assertEquals("#FF000033", group.fillColor());
        assertEquals(0, group.sorting());
        assertTrue(group.toggleable());
        assertTrue(group.depthTest());
        assertEquals(List.of(), group.cssClasses());
        assertFalse(group.allowPlayerColors());
    }

    @Test
    void constructorDefensivelyCopiesCssClasses() {
        var mutable = new ArrayList<String>();
        mutable.add("marker-a");

        var group = markerGroup(mutable);
        mutable.add("marker-b");

        assertEquals(List.of("marker-a"), group.cssClasses());
    }

    @Test
    void constructorAllowsNullPrefix() {
        // SignLinesParser deliberately tolerates this (see SignLinesParser.hasValidPrefix) - a
        // misconfigured group with no prefix logs a warning and is ignored, rather than the whole config
        // load failing.
        var group = markerGroupWithPrefix(null);

        assertEquals(null, group.prefix());
    }

    @Test
    void constructorRejectsNullMatchType() {
        assertThrows(NullPointerException.class, () -> new MarkerGroup(
                "[poi]", null, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false));
    }

    @Test
    void constructorRejectsNullType() {
        assertThrows(NullPointerException.class, () -> new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, null, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false));
    }

    @Test
    void constructorRejectsNullName() {
        assertThrows(NullPointerException.class, () -> new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, null, null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false));
    }

    @Test
    void constructorRejectsNullLineColor() {
        assertThrows(NullPointerException.class, () -> new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, null, "#FF000033", 0, true, true, List.of(), false));
    }

    @Test
    void constructorRejectsNullFillColor() {
        assertThrows(NullPointerException.class, () -> new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", null, 0, true, true, List.of(), false));
    }

    @Test
    void constructorRejectsNullCssClasses() {
        assertThrows(NullPointerException.class, () -> new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, null, false));
    }

    @Test
    void constructorAllowsNullIcon() {
        var group = new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);

        assertEquals(null, group.icon());
    }

    private static MarkerGroup markerGroup(List<String> cssClasses) {
        return new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, cssClasses, false);
    }

    private static MarkerGroup markerGroupWithPrefix(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
    }
}
