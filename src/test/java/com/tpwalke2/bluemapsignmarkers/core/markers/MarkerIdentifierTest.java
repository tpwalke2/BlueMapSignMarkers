package com.tpwalke2.bluemapsignmarkers.core.markers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkerIdentifierTest {

    @Test
    void getIdFormatsCoordinates() {
        var identifier = new MarkerIdentifier(1, -2, 3, new MarkerSetIdentifier("world", group()));

        assertEquals("x1_y-2_z3", identifier.getId());
    }

    private static MarkerGroup group() {
        return new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
    }
}
