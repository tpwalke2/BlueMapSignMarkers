package com.tpwalke2.bluemapsignmarkers.common;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPathResolverTest {

    // getWorldPath(LevelResource.ROOT) carries an unresolved trailing "." segment (see
    // ServerPathResolver's own comment) - mirrored here rather than passed as an already-normalized
    // Path, since that trailing "." is exactly what each method's own (mis)handling of it is tested for.
    private static Path rawLevelPath(String... levelDirSegments) {
        return Path.of(".", levelDirSegments).resolve(".");
    }

    @Test
    void resolveMarkerStorageRootNormalizesTheTrailingDotSegment() {
        var result = ServerPathResolver.resolveMarkerStorageRoot(rawLevelPath("run", "myworld"));

        assertEquals(Path.of("run", Constants.MOD_ID, "myworld"), result);
    }

    @Test
    void resolveLegacyMarkerFilePathReproducesTheUnnormalizedFormula() {
        var result = ServerPathResolver.resolveLegacyMarkerFilePath(rawLevelPath("run", "myworld"));

        assertEquals("config/" + Constants.MOD_ID + "/myworld/signs.json", result);
    }
}
