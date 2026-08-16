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
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV3;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV4;
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
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, prefix, null, 0, 0, false, 0, 0,
                2, "#FF0000FF");
    }

    @Test
    void v5ContentIsParsedDirectlyWithoutCreatingABackup(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var entry = new SignEntry(KEY, "player-1", new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""), 1000L, new String[]{"[poi]", "label"}, new String[]{});
        var content = GSON.toJson(new VersionedSignFile(SignFileVersions.V5, GSON.toJson(new SignEntry[]{entry})));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        var result = VersionedFileSignEntryLoader.loadSignEntries(path, content, NO_GROUPS, GSON);

        assertArrayEquals(new SignEntry[]{entry}, result);
        assertFalse(Files.exists(Path.of(path + ".v4.bak")), "a V5 file should not be backed up as a V4 file");
    }

    @Test
    void v4ContentIsConvertedThroughVersion5ConverterAndBackedUp(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var v4Entry = new SignEntryV4(KEY, "player-1", new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""), 1000L);
        var content = GSON.toJson(new VersionedSignFile(SignFileVersions.V4, GSON.toJson(new SignEntryV4[]{v4Entry})));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        var result = VersionedFileSignEntryLoader.loadSignEntries(path, content, NO_GROUPS, GSON);

        assertEquals(1, result.length);
        assertEquals(KEY, result[0].key());
        assertEquals("player-1", result[0].playerId());
        assertEquals("[poi]", result[0].frontText().prefix());
        assertEquals(1000L, result[0].createdAtMillis());
        assertNull(result[0].frontRawLines(), "a V4 entry has no raw sign text to backfill from");
        assertNull(result[0].backRawLines(), "a V4 entry has no raw sign text to backfill from");

        var backup = Path.of(path + ".v4.bak");
        assertTrue(Files.exists(backup), "the original V4 file should be backed up before being replaced");
        assertEquals(content, Files.readString(backup));
    }

    @Test
    void v3ContentIsConvertedThroughVersion4And5ConvertersAndBackedUp(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var v3Entry = new SignEntryV3(KEY, "player-1", new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""));
        var content = GSON.toJson(new VersionedSignFile(SignFileVersions.V3, GSON.toJson(new SignEntryV3[]{v3Entry})));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        var result = VersionedFileSignEntryLoader.loadSignEntries(path, content, NO_GROUPS, GSON);

        assertEquals(1, result.length);
        assertEquals(KEY, result[0].key());
        assertEquals("player-1", result[0].playerId());
        assertEquals("[poi]", result[0].frontText().prefix());
        assertNull(result[0].frontRawLines());
        assertNull(result[0].backRawLines());

        var backup = Path.of(path + ".v3.bak");
        assertTrue(Files.exists(backup), "the original V3 file should be backed up before being replaced");
        assertEquals(content, Files.readString(backup));
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

    // content is already fully read into memory by the caller before this method runs, so deleting the on-disk
    // source file leaves parsing unaffected but makes the backup copy step fail - simulating a disk-full or
    // permissions failure without depending on platform-specific filesystem permission enforcement.
    //
    // The loader only migrates in-memory here - it never overwrites the source file itself, so a failed .bak
    // journal copy is logged but not fatal; the caller (LegacySignFileMigrator) is the one that backs up and
    // replaces the original, and only after every region file is confirmed written.
    @Test
    void v2ContentStillMigratesWhenTheBackupFails(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var v2Entry = new SignEntryV2(KEY, "player-1",
                new SignLinesParseResultV2(MarkerTypeV2.POI, "label", "detail"),
                new SignLinesParseResultV2(null, "", ""));
        var content = GSON.toJson(new VersionedSignFile(SignFileVersions.V2, GSON.toJson(new SignEntryV2[]{v2Entry})));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
        Files.delete(Path.of(path));

        var result = VersionedFileSignEntryLoader.loadSignEntries(
                path, content, new MarkerGroup[]{poiGroup("[poi]")}, GSON);

        assertEquals(1, result.length, "a failed backup must not abort the migration");
        assertEquals(KEY, result[0].key());
        assertFalse(Files.exists(Path.of(path + ".v2.bak")), "no backup should exist after a failed copy");
    }

    @Test
    void malformedJsonReturnsNullRatherThanThrowing() {
        var result = VersionedFileSignEntryLoader.loadSignEntries(
                "unused-path", "{ this is not valid json", NO_GROUPS, GSON);

        assertNull(result);
    }

    @Test
    void emptyContentReturnsNullRatherThanThrowing() {
        // gson.fromJson("", ...) returns null rather than throwing; the explicit null/version/data check
        // catches this and falls back, rather than relying on a coincidental NPE.
        var result = VersionedFileSignEntryLoader.loadSignEntries("unused-path", "", NO_GROUPS, GSON);

        assertNull(result);
    }

    @Test
    void jsonMissingVersionAndDataFallsBackToVersion1RatherThanThrowing() {
        var result = VersionedFileSignEntryLoader.loadSignEntries("unused-path", "{}", NO_GROUPS, GSON);

        assertNull(result);
    }

    @Test
    void v2ContentSkipsAMalformedEntryInsteadOfDroppingTheWholeFile(@TempDir Path tempDir) throws IOException {
        var path = tempDir.resolve("signs.json").toString();
        var goodEntry = new SignEntryV2(KEY, "player-1",
                new SignLinesParseResultV2(MarkerTypeV2.POI, "label", "detail"),
                new SignLinesParseResultV2(null, "", ""));
        var badEntry = new SignEntryV2(new SignEntryKey(3, 64, 4, "minecraft:overworld"), "player-2", null,
                new SignLinesParseResultV2(null, "", ""));
        var content = GSON.toJson(
                new VersionedSignFile(SignFileVersions.V2, GSON.toJson(new SignEntryV2[]{goodEntry, badEntry})));
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);

        var result = VersionedFileSignEntryLoader.loadSignEntries(
                path, content, new MarkerGroup[]{poiGroup("[poi]")}, GSON);

        assertEquals(1, result.length);
        assertEquals(KEY, result[0].key());
    }
}
