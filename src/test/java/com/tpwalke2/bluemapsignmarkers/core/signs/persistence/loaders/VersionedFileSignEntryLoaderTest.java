package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.google.gson.Gson;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.SignFileVersions;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.VersionedSignFile;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.MarkerTypeV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV2;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignLinesParseResultV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedFileSignEntryLoaderTest {

    private static final Gson GSON = new Gson();
    private static final MarkerGroup[] NO_GROUPS = new MarkerGroup[0];
    private static final SignEntryKey KEY = new SignEntryKey(1, 64, 2, "minecraft:overworld");

    private static MarkerGroup poiGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, prefix, null, 0, 0, false, 0, 0);
    }

    @Test
    void v3ContentIsParsedDirectlyWithoutCreatingABackup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var entry = new SignEntry(KEY, "player-1", new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""));
        var content = GSON.toJson(new VersionedSignFile(SignFileVersions.V3, GSON.toJson(new SignEntry[]{entry})));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        var result = VersionedFileSignEntryLoader.loadSignEntries(path, content, NO_GROUPS, GSON);

        assertArrayEquals(new SignEntry[]{entry}, result);
        assertFalse(Files.exists(Path.of(path + ".v2.bak")), "a V3 file should not be backed up as a V2 file");
    }

    @Test
    void v2ContentIsConvertedThroughVersion3ConverterAndBackedUp(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var v2Entry = new SignEntryV2(KEY, "player-1",
                new SignLinesParseResultV2(MarkerTypeV2.POI, "label", "detail"),
                new SignLinesParseResultV2(null, "", ""));
        var content = GSON.toJson(new VersionedSignFile(SignFileVersions.V2, GSON.toJson(new SignEntryV2[]{v2Entry})));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        var result = VersionedFileSignEntryLoader.loadSignEntries(
                path, content, new MarkerGroup[]{poiGroup("[poi]")}, GSON);

        assertEquals(1, result.length);
        assertEquals(KEY, result[0].key());
        assertEquals("[poi]", result[0].frontText().prefix());
        assertEquals("label", result[0].frontText().label());

        var backup = Path.of(path + ".v2.bak");
        assertTrue(Files.exists(backup), "the original V2 file should be backed up before being replaced");
        assertEquals(content, Files.readString(backup));
    }

    @Test
    void malformedJsonReturnsNullRatherThanThrowing() {
        var result = VersionedFileSignEntryLoader.loadSignEntries(
                "unused-path", "{ this is not valid json", NO_GROUPS, GSON);

        assertNull(result);
    }

    @Test
    void emptyContentReturnsNullRatherThanThrowing() {
        // gson.fromJson("", ...) returns null rather than throwing, so versionedSignFile.version() below it NPEs -
        // still caught by the same catch-all and turned into a null return, not a crash.
        var result = VersionedFileSignEntryLoader.loadSignEntries("unused-path", "", NO_GROUPS, GSON);

        assertNull(result);
    }
}
