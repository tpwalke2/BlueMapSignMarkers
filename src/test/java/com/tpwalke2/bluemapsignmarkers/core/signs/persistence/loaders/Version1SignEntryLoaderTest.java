package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.MarkerTypeV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignLinesParseResultV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Version1SignEntryLoaderTest {

    private static final Gson GSON = new Gson();
    private static final MarkerGroup[] POI_GROUP = {
            new MarkerGroup("[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI,
                    "[poi]", null, 0, 0, false, 0, 0)
    };

    private static String load(Path tempDir, String legacyDimension) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var entry = new SignEntryV2(
                new SignEntryKey(0, 64, 0, legacyDimension),
                "player-1",
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Town Hall", "Town Hall"),
                new SignLinesParseResultV2(null, "", ""));
        var content = GSON.toJson(new SignEntryV2[]{entry});
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        var result = Version1SignEntryLoader.loadSignEntries(path, content, POI_GROUP, GSON);

        return result[0].key().parentMap();
    }

    @Test
    void normalizesTheThreeRecognizedLegacyShorthandDimensions(@TempDir Path tempDir) throws IOException {
        assertEquals("minecraft:the_nether", load(tempDir, "nether"));
        assertEquals("minecraft:the_end", load(tempDir, "end"));
        assertEquals("minecraft:overworld", load(tempDir, "overworld"));
    }

    @Test
    void legacyShorthandNormalizationIsCaseInsensitive(@TempDir Path tempDir) throws IOException {
        assertEquals("minecraft:the_nether", load(tempDir, "NETHER"));
        assertEquals("minecraft:the_end", load(tempDir, "End"));
        assertEquals("minecraft:overworld", load(tempDir, "OverWorld"));
    }

    // Documents the Low-severity finding: getNormalizedMapId always lowercases its input, even on the default
    // branch, so an unrecognized legacy dimension string doesn't pass through truly unchanged - it survives with
    // its casing silently altered, rather than being left exactly as-is or flagged as unrecognized.
    @Test
    void anUnrecognizedLegacyDimensionStringFallsThroughLowercasedButOtherwiseUnchanged(@TempDir Path tempDir) throws IOException {
        assertEquals("my_custom_dimension", load(tempDir, "My_Custom_Dimension"));
    }

    @Test
    void anAlreadyNamespacedDimensionStringPassesThroughUnchanged(@TempDir Path tempDir) throws IOException {
        assertEquals("minecraft:overworld", load(tempDir, "minecraft:overworld"));
    }

    @Test
    void loadSignEntriesBacksUpTheOriginalFileBeforeReturning(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var entry = new SignEntryV2(
                new SignEntryKey(0, 64, 0, "overworld"),
                "player-1",
                new SignLinesParseResultV2(MarkerTypeV2.POI, "Town Hall", "Town Hall"),
                new SignLinesParseResultV2(null, "", ""));
        var content = GSON.toJson(new SignEntryV2[]{entry});
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        Version1SignEntryLoader.loadSignEntries(path, content, POI_GROUP, GSON);

        var backup = Path.of(path + ".v1.bak");
        assertTrue(Files.exists(backup), "the original V1 file should be backed up");
        assertEquals(content, Files.readString(backup));
    }
}
