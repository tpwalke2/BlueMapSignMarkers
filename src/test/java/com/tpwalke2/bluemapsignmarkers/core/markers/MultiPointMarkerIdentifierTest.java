package com.tpwalke2.bluemapsignmarkers.core.markers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiPointMarkerIdentifierTest {

    @Test
    void getIdFormatsAsKindColonLabelForLine() {
        var identifier = new MultiPointMarkerIdentifier("line", "Ridge Trail", new MarkerSetIdentifier("world", group(MarkerGroupType.LINE)));

        assertEquals("line:Ridge Trail", identifier.getId());
    }

    @Test
    void getIdFormatsAsKindColonLabelForShape() {
        var identifier = new MultiPointMarkerIdentifier("shape", "Town Border", new MarkerSetIdentifier("world", group(MarkerGroupType.SHAPE)));

        assertEquals("shape:Town Border", identifier.getId());
    }

    @Test
    void getIdFormatsAsKindColonLabelForExtrude() {
        var identifier = new MultiPointMarkerIdentifier("extrude", "Tower Base", new MarkerSetIdentifier("world", group(MarkerGroupType.EXTRUDE)));

        assertEquals("extrude:Tower Base", identifier.getId());
    }

    private static MarkerGroup group(MarkerGroupType type) {
        return new MarkerGroup(
                "[prefix]", MarkerGroupMatchType.STARTS_WITH, type, "name", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
    }
}
