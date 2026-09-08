package com.tpwalke2.bluemapsignmarkers.core.signs.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void relativeFilePathRejectsABlankNamespace() {
        var key = new SignRegionKey(":custom/dimension", 0, 0);

        assertThrows(IllegalArgumentException.class, key::relativeFilePath);
    }

    @Test
    void relativeFilePathRejectsADotNamespace() {
        var key = new SignRegionKey(".", 0, 0);

        assertThrows(IllegalArgumentException.class, key::relativeFilePath);
    }

    @Test
    void relativeFilePathRejectsADotDotNamespace() {
        var key = new SignRegionKey("..", 0, 0);

        assertThrows(IllegalArgumentException.class, key::relativeFilePath);
    }

    @Test
    void relativeFilePathRejectsADimensionPathThatEscapesViaDotDotTraversal() {
        var key = new SignRegionKey("somemod:../../escape", 0, 0);

        assertThrows(IllegalArgumentException.class, key::relativeFilePath);
    }

    @Test
    void relativeFilePathRejectsAnAbsoluteDimensionPath() {
        // Built from the platform's own root so this holds on both Windows (drive-absolute paths) and
        // Linux (root-absolute paths) rather than hardcoding one platform's absolute-path syntax.
        var absolutePath = FileSystems.getDefault().getRootDirectories().iterator().next().resolve("escape").toString();
        var key = new SignRegionKey("somemod:" + absolutePath, 0, 0);

        assertThrows(IllegalArgumentException.class, key::relativeFilePath);
    }

    // A raw path segment starting with a single '\' parses as a Windows "drive-relative" path -
    // isAbsolute() reports false for it, but it still carries a root component that discards
    // namespaceDir on resolve(). Only reproducible on Windows; '\' is just an ordinary filename
    // character on other platforms.
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void relativeFilePathRejectsADriveRelativeDimensionPath() {
        var key = new SignRegionKey("somemod:\\escape", 0, 0);

        assertThrows(IllegalArgumentException.class, key::relativeFilePath);
    }
}
