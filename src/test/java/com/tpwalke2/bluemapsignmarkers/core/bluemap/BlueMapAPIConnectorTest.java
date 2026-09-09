package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.core.bounds.RenderMaskEvaluator;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// resolveExtrudeHeightRange, pointOf, and isInsideRenderBounds(RenderMask, ...) are package-private
// specifically so they're testable without pulling in bluemap-api (compileOnly, not on the test
// classpath - see the class's other private/game-coupled methods, which can't be exercised this way
// since even constructing a BlueMapAPIConnector calls BlueMapAPI.getInstance()).
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

    @Test
    void pointOfWrapsAMarkerIdentifiersCoordinatesIntoASinglePointList() {
        var identifier = new MarkerIdentifier(10, 64, -5, new MarkerSetIdentifier("world", MarkerGroup.DEFAULT_POI_GROUP));

        var points = BlueMapAPIConnector.pointOf(identifier);

        assertEquals(List.of(new LinePoint(10, 64, -5)), points);
    }

    private static Path writeRenderMaskConfig(Path mapsDir, String renderMaskBlock) throws IOException {
        var file = mapsDir.resolve("world.conf");
        Files.writeString(file, "world: \"world\"\n" + renderMaskBlock);
        return file;
    }

    @Test
    void poiPointInsideTheMaskIsInBounds(@TempDir Path mapsDir) throws IOException {
        writeRenderMaskConfig(mapsDir, "render-mask: [ { min-y: 100 } ]\n");
        var mask = RenderMaskEvaluator.load("world", mapsDir);

        var result = BlueMapAPIConnector.isInsideRenderBounds(mask, List.of(new LinePoint(0, 150, 0)));

        assertTrue(result);
    }

    @Test
    void poiPointOutsideTheMaskIsOutOfBounds(@TempDir Path mapsDir) throws IOException {
        writeRenderMaskConfig(mapsDir, "render-mask: [ { min-y: 100 } ]\n");
        var mask = RenderMaskEvaluator.load("world", mapsDir);

        var result = BlueMapAPIConnector.isInsideRenderBounds(mask, List.of(new LinePoint(0, 50, 0)));

        assertFalse(result);
    }

    @Test
    void lineWithAtLeastOneMemberInsideTheMaskIsInBounds(@TempDir Path mapsDir) throws IOException {
        writeRenderMaskConfig(mapsDir, "render-mask: [ { min-y: 100 } ]\n");
        var mask = RenderMaskEvaluator.load("world", mapsDir);
        var points = List.of(new LinePoint(0, 50, 0), new LinePoint(0, 150, 0));

        var result = BlueMapAPIConnector.isInsideRenderBounds(mask, points);

        assertTrue(result);
    }

    @Test
    void lineWithEveryMemberOutsideTheMaskIsOutOfBounds(@TempDir Path mapsDir) throws IOException {
        writeRenderMaskConfig(mapsDir, "render-mask: [ { min-y: 100 } ]\n");
        var mask = RenderMaskEvaluator.load("world", mapsDir);
        var points = List.of(new LinePoint(0, 10, 0), new LinePoint(0, 50, 0));

        var result = BlueMapAPIConnector.isInsideRenderBounds(mask, points);

        assertFalse(result);
    }

    @Test
    void unboundedMaskAllowsAnyPoint(@TempDir Path mapsDir) throws IOException {
        writeRenderMaskConfig(mapsDir, "");
        var mask = RenderMaskEvaluator.load("world", mapsDir);

        var result = BlueMapAPIConnector.isInsideRenderBounds(mask, List.of(new LinePoint(0, -6000, 0)));

        assertTrue(result);
    }
}
