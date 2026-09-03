package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// resolveExtrudeHeightRange is package-private specifically so it's testable without pulling in bluemap-api
// (compileOnly, not on the test classpath - see the class's other private/game-coupled methods).
class BlueMapAPIConnectorTest {

    @Test
    void allMembersAtTheSameYGetAMinimumOneBlockHeightInsteadOfCollapsingToZero() {
        var points = List.of(new LinePoint(0, 64, 0), new LinePoint(10, 64, 0), new LinePoint(10, 64, 10));

        var range = BlueMapAPIConnector.resolveExtrudeHeightRange("Town Hall", points);

        assertEquals(64f, range.minY());
        assertEquals(65f, range.maxY());
    }

    @Test
    void membersAtDifferentYsSpanTheirActualLowestToTallestHeight() {
        var points = List.of(new LinePoint(0, 60, 0), new LinePoint(10, 70, 0), new LinePoint(10, 65, 10));

        var range = BlueMapAPIConnector.resolveExtrudeHeightRange("Town Hall", points);

        assertEquals(60f, range.minY());
        assertEquals(70f, range.maxY());
    }
}
