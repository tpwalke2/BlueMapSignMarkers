package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV5;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Version6ConverterTest {

    private static final SignEntryKey KEY = new SignEntryKey(1, 64, 2, "minecraft:overworld");

    private static SignEntryV5 entry() {
        return new SignEntryV5(KEY, "player-1",
                new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""),
                5000L,
                new String[]{"[poi]", "label"},
                null);
    }

    @Test
    void copiesEveryFieldUnchanged() {
        var converted = Version6Converter.convertToV6(entry());

        assertEquals(KEY, converted.key());
        assertEquals("player-1", converted.playerId());
        assertEquals("[poi]", converted.frontText().prefix());
        assertEquals("label", converted.frontText().label());
        assertEquals(entry().backText(), converted.backText());
        assertEquals(5000L, converted.createdAtMillis());
        assertArrayEquals(new String[]{"[poi]", "label"}, converted.frontRawLines());
        assertNull(converted.backRawLines());
    }

    // A pre-V6 entry has no dye on disk - backfills to "BLACK", matching an unwaxed vanilla sign's actual
    // default state (SignText.getColor() defaults to DyeColor.BLACK), so ColorResolver never needs to
    // distinguish "genuinely undyed" from "migrated with no dye data".
    @Test
    void backfillsBothSidesToUndyed() {
        var converted = Version6Converter.convertToV6(entry());

        assertEquals("BLACK", converted.frontDye());
        assertEquals("BLACK", converted.backDye());
    }
}
