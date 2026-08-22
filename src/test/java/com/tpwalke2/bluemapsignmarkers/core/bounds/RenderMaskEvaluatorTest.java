package com.tpwalke2.bluemapsignmarkers.core.bounds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderMaskEvaluatorTest {

    // Copied from run/config/bluemap/maps/world_nether_roof.conf's render-mask block: an include
    // box (min-y: 127, x/z unbounded) followed by a subtract box (y 0-126).
    private static final String NETHER_ROOF_RENDER_MASK = """
            render-mask: [
              {
                #min-x: -4000
                #max-x: 4000
                #min-z: -4000
                #max-z: 4000
                min-y: 127
                #max-y: 100
              }
              {
                # this removes everything at and between y 0 and 126 (regular nether before the roof)
                subtract: true
                min-y: 0
                max-y: 126
              }
            ]
            """;

    private static Path writeConfig(Path dir, String fileName, String renderMaskBlock) throws IOException {
        var file = dir.resolve(fileName);
        Files.writeString(file, "world: \"world\"\n" + renderMaskBlock);
        return file;
    }

    @Test
    void pointAboveMinYCutoffIsInBounds(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world_nether_roof.conf", NETHER_ROOF_RENDER_MASK);

        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world_nether_roof", mapsDir, 0, 200, 0));
    }

    @Test
    void pointInSubtractedRangeIsOutOfBounds(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world_nether_roof.conf", NETHER_ROOF_RENDER_MASK);

        assertFalse(RenderMaskEvaluator.isInsideRenderBounds("world_nether_roof", mapsDir, 0, 50, 0));
    }

    @Test
    void pointBelowEveryBoxIsOutOfBounds(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world_nether_roof.conf", NETHER_ROOF_RENDER_MASK);

        assertFalse(RenderMaskEvaluator.isInsideRenderBounds("world_nether_roof", mapsDir, 0, -50, 0));
    }

    @Test
    void missingRenderMaskKeyIsUnbounded(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world.conf", "");

        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 0, -6000, 0));
    }

    @Test
    void emptyRenderMaskArrayIsUnbounded(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world.conf", "render-mask: []\n");

        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 0, -6000, 0));
    }

    @Test
    void missingConfigFileIsUnbounded(@TempDir Path mapsDir) {
        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("does_not_exist", mapsDir, 0, -6000, 0));
    }

    @Test
    void unreadableConfigFileIsUnbounded(@TempDir Path mapsDir) throws IOException {
        var file = writeConfig(mapsDir, "world.conf", "render-mask: [ { min-y: 0 } ]\n");
        var permissionsRevoked = file.toFile().setReadable(false);
        try {
            if (permissionsRevoked) {
                assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 0, -6000, 0));
            }
        } finally {
            file.toFile().setReadable(true);
        }
    }

    @Test
    void maskStartingWithSubtractIncludesEverythingElse(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world.conf", """
                render-mask: [
                  {
                    subtract: true
                    min-x: -10
                    max-x: 10
                    min-y: -10
                    max-y: 10
                    min-z: -10
                    max-z: 10
                  }
                ]
                """);

        assertFalse(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 0, 0, 0));
        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 1000, 0, 0));
    }

    @Test
    void omittedAxisBoundIsUnboundedOnlyOnThatAxis(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world.conf", """
                render-mask: [
                  {
                    min-y: 0
                    max-y: 10
                  }
                ]
                """);

        // x/z are unbounded on this box, y is bounded to [0, 10]
        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 1_000_000, 5, -1_000_000));
        assertFalse(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 0, 20, 0));
    }

    @Test
    void listOrderDecidesOverlappingBoxesIncludeLast(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world.conf", """
                render-mask: [
                  {
                    subtract: true
                    min-x: 0
                    max-x: 10
                    min-y: 0
                    max-y: 10
                    min-z: 0
                    max-z: 10
                  }
                  {
                    min-x: 0
                    max-x: 10
                    min-y: 0
                    max-y: 10
                    min-z: 0
                    max-z: 10
                  }
                ]
                """);

        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 5, 5, 5));
    }

    @Test
    void listOrderDecidesOverlappingBoxesSubtractLast(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world.conf", """
                render-mask: [
                  {
                    min-x: 0
                    max-x: 10
                    min-y: 0
                    max-y: 10
                    min-z: 0
                    max-z: 10
                  }
                  {
                    subtract: true
                    min-x: 0
                    max-x: 10
                    min-y: 0
                    max-y: 10
                    min-z: 0
                    max-z: 10
                  }
                ]
                """);

        assertFalse(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 5, 5, 5));
    }

    @Test
    void malformedRenderMaskIsUnbounded(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world.conf", "render-mask: [ { min-y: 127 \n");

        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("world", mapsDir, 0, -6000, 0));
    }

    @Test
    void unmatchedMapIdIsUnbounded(@TempDir Path mapsDir) throws IOException {
        writeConfig(mapsDir, "world_nether_roof.conf", NETHER_ROOF_RENDER_MASK);

        assertTrue(RenderMaskEvaluator.isInsideRenderBounds("some_other_map", mapsDir, 0, -6000, 0));
    }
}
