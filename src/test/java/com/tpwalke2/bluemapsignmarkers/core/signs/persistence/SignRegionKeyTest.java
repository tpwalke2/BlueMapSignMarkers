package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignRegionKeyTest {

    @Test
    void forPositionAssignsRegionZeroAtOrigin() {
        var key = SignRegionKey.forPosition("minecraft:overworld", 0, 0);

        assertEquals(new SignRegionKey("minecraft:overworld", 0, 0), key);
    }

    @Test
    void forPositionUsesFloorDivisionForNegativeCoordinates() {
        var negative = SignRegionKey.forPosition("minecraft:overworld", -1, -1);

        // Truncating division would put x=-1 in the same region as x=0 (both round toward zero); floorDiv
        // correctly puts it in the region to the west/north instead.
        assertEquals(-1, negative.regionX());
        assertEquals(-1, negative.regionZ());
    }

    @Test
    void forPositionSplitsAtRegionBoundary() {
        var lastBlockOfRegionZero = SignRegionKey.forPosition("minecraft:overworld", 511, 511);
        var firstBlockOfRegionOne = SignRegionKey.forPosition("minecraft:overworld", 512, 512);

        assertEquals(0, lastBlockOfRegionZero.regionX());
        assertEquals(0, lastBlockOfRegionZero.regionZ());
        assertEquals(1, firstBlockOfRegionOne.regionX());
        assertEquals(1, firstBlockOfRegionOne.regionZ());
    }

    @Test
    void relativeFilePathSplitsNamespaceAndPath() {
        var key = new SignRegionKey("minecraft:the_nether", 2, -3);

        assertEquals(Path.of("minecraft", "the_nether", "r.2.-3.json"), key.relativeFilePath());
    }

    @Test
    void relativeFilePathHandlesDimensionWithoutColon() {
        var key = new SignRegionKey("unknown", 0, 0);

        assertEquals(Path.of("unknown", "r.0.0.json"), key.relativeFilePath());
    }

    @Test
    void relativeFilePathHandlesNestedDimensionPath() {
        var key = new SignRegionKey("somemod:custom/dimension", 0, 0);

        assertEquals(Path.of("somemod", "custom", "dimension", "r.0.0.json"), key.relativeFilePath());
    }
}
