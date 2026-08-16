package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Version4ConverterTest {

    private static final SignEntryKey KEY = new SignEntryKey(1, 64, 2, "minecraft:overworld");

    private static SignEntryV3 entry() {
        return new SignEntryV3(KEY, "player-1",
                new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""));
    }

    @Test
    void copiesEveryFieldUnchangedExceptCreatedAtMillis() {
        var converted = Version4Converter.convertToV4(entry(), 0, 5000L);

        assertEquals(KEY, converted.key());
        assertEquals("player-1", converted.playerId());
        assertEquals("[poi]", converted.frontText().prefix());
        assertEquals("label", converted.frontText().label());
        assertEquals("detail", converted.frontText().detail());
        assertEquals(entry().backText(), converted.backText());
    }

    // createdAtMillis for a migrated entry is arbitrary but stable - file mtime plus array index - not a
    // reconstruction of true placement history, which doesn't exist for pre-existing signs.
    @Test
    void createdAtMillisIsFileLastModifiedPlusIndexInFile() {
        var converted = Version4Converter.convertToV4(entry(), 3, 5000L);

        assertEquals(5003L, converted.createdAtMillis());
    }

    @Test
    void differentIndicesInTheSameFileProduceDifferentButStableTimestamps() {
        var first = Version4Converter.convertToV4(entry(), 0, 5000L);
        var second = Version4Converter.convertToV4(entry(), 1, 5000L);

        assertEquals(5000L, first.createdAtMillis());
        assertEquals(5001L, second.createdAtMillis());
    }
}
