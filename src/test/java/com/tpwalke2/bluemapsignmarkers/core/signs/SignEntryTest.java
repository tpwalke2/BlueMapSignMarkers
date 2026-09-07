package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SignEntryTest {

    private static final SignEntryKey KEY = new SignEntryKey(1, 2, 3, "world");
    private static final String PLAYER_ID = "player-1";
    private static final SignLinesParseResult FRONT = new SignLinesParseResult("[poi]", "label", "detail");
    private static final SignLinesParseResult BACK = new SignLinesParseResult(null, "", "");
    private static final long CREATED_AT_MILLIS = 1000L;
    private static final String[] FRONT_RAW_LINES = new String[]{"[poi]", "label"};
    private static final String[] BACK_RAW_LINES = new String[]{"", "", "", ""};
    private static final String FRONT_DYE = "BLACK";
    private static final String BACK_DYE = "BLACK";

    private static SignEntry baseEntry() {
        return new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS, FRONT_RAW_LINES, BACK_RAW_LINES, FRONT_DYE, BACK_DYE);
    }

    @Test
    void equalsIsReflexive() {
        var entry = baseEntry();

        assertEquals(entry, entry);
    }

    @Test
    void entriesWithTheSameFieldValuesAreEqualAndHaveTheSameHashCode() {
        var first = baseEntry();
        var second = new SignEntry(
                new SignEntryKey(1, 2, 3, "world"),
                "player-1",
                new SignLinesParseResult("[poi]", "label", "detail"),
                new SignLinesParseResult(null, "", ""),
                CREATED_AT_MILLIS,
                new String[]{"[poi]", "label"},
                new String[]{"", "", "", ""},
                "BLACK",
                "BLACK");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equalsIsSymmetric() {
        var first = baseEntry();
        var second = first.withKey(new SignEntryKey(9, 9, 9, "world"));

        assertEquals(first.equals(second), second.equals(first));
    }

    @Test
    void equalsReturnsFalseForADifferentKey() {
        var entry = baseEntry();
        var differentKey = entry.withKey(new SignEntryKey(9, 9, 9, "world"));

        assertNotEquals(entry, differentKey);
    }

    @Test
    void equalsReturnsFalseForADifferentPlayerId() {
        var entry = baseEntry();
        var differentPlayerId = new SignEntry(KEY, "player-2", FRONT, BACK, CREATED_AT_MILLIS, FRONT_RAW_LINES, BACK_RAW_LINES, FRONT_DYE, BACK_DYE);

        assertNotEquals(entry, differentPlayerId);
    }

    @Test
    void equalsReturnsFalseForADifferentFrontText() {
        var entry = baseEntry();
        var differentFrontText = new SignEntry(KEY, PLAYER_ID, new SignLinesParseResult("[poi]", "other", "detail"), BACK, CREATED_AT_MILLIS, FRONT_RAW_LINES, BACK_RAW_LINES, FRONT_DYE, BACK_DYE);

        assertNotEquals(entry, differentFrontText);
    }

    @Test
    void equalsReturnsFalseForADifferentBackText() {
        var entry = baseEntry();
        var differentBackText = new SignEntry(KEY, PLAYER_ID, FRONT, new SignLinesParseResult(null, "other", ""), CREATED_AT_MILLIS, FRONT_RAW_LINES, BACK_RAW_LINES, FRONT_DYE, BACK_DYE);

        assertNotEquals(entry, differentBackText);
    }

    @Test
    void equalsReturnsFalseForADifferentCreatedAtMillis() {
        var entry = baseEntry();
        var differentCreatedAtMillis = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS + 1, FRONT_RAW_LINES, BACK_RAW_LINES, FRONT_DYE, BACK_DYE);

        assertNotEquals(entry, differentCreatedAtMillis);
    }

    @Test
    void equalsReturnsFalseForDifferentFrontRawLines() {
        var entry = baseEntry();
        var differentFrontRawLines = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS,
                new String[]{"[poi]", "other"}, BACK_RAW_LINES, FRONT_DYE, BACK_DYE);

        assertNotEquals(entry, differentFrontRawLines);
    }

    @Test
    void equalsReturnsFalseForDifferentBackRawLines() {
        var entry = baseEntry();
        var differentBackRawLines = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS,
                FRONT_RAW_LINES, new String[]{"other"}, FRONT_DYE, BACK_DYE);

        assertNotEquals(entry, differentBackRawLines);
    }

    @Test
    void equalsReturnsFalseForADifferentFrontDye() {
        var entry = baseEntry();
        var differentFrontDye = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS,
                FRONT_RAW_LINES, BACK_RAW_LINES, "RED", BACK_DYE);

        assertNotEquals(entry, differentFrontDye);
    }

    @Test
    void equalsReturnsFalseForADifferentBackDye() {
        var entry = baseEntry();
        var differentBackDye = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS,
                FRONT_RAW_LINES, BACK_RAW_LINES, FRONT_DYE, "RED");

        assertNotEquals(entry, differentBackDye);
    }

    @Test
    void equalsToleratesNullRawLines() {
        var entryWithNullRawLines = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS, null, null, FRONT_DYE, BACK_DYE);
        var otherEntryWithNullRawLines = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS, null, null, FRONT_DYE, BACK_DYE);

        assertEquals(entryWithNullRawLines, otherEntryWithNullRawLines);
        assertEquals(entryWithNullRawLines.hashCode(), otherEntryWithNullRawLines.hashCode());
        assertNotEquals(baseEntry(), entryWithNullRawLines);
    }

    @Test
    void withParsedTextReturnsANewInstanceWithOnlyTheParsedTextChanged() {
        var entry = baseEntry();
        var newFront = new SignLinesParseResult("[line]", "new-label", "new-detail");
        var newBack = new SignLinesParseResult(null, "", "");

        var updated = entry.withParsedText(newFront, newBack);

        assertEquals(newFront, updated.frontText());
        assertEquals(newBack, updated.backText());
        assertEquals(KEY, updated.key());
        assertEquals(PLAYER_ID, updated.playerId());
        assertEquals(CREATED_AT_MILLIS, updated.createdAtMillis());
        assertArrayEquals(FRONT_RAW_LINES, updated.frontRawLines());
        assertArrayEquals(BACK_RAW_LINES, updated.backRawLines());
        assertEquals(FRONT, entry.frontText(), "the original entry should be unmodified");
    }

    @Test
    void equalsReturnsFalseWhenComparedToNull() {
        var entry = baseEntry();

        assertFalse(entry.equals(null));
    }

    @Test
    void equalsReturnsFalseWhenComparedToADifferentType() {
        var entry = baseEntry();

        assertFalse(entry.equals("not a SignEntry"));
    }

    @Test
    void withKeyReturnsANewInstanceWithOnlyTheKeyChanged() {
        var entry = baseEntry();
        var newKey = new SignEntryKey(9, 9, 9, "world_nether");

        var updated = entry.withKey(newKey);

        assertEquals(newKey, updated.key());
        assertEquals(PLAYER_ID, updated.playerId());
        assertEquals(FRONT, updated.frontText());
        assertEquals(BACK, updated.backText());
        assertEquals(CREATED_AT_MILLIS, updated.createdAtMillis());
        assertArrayEquals(FRONT_RAW_LINES, updated.frontRawLines());
        assertArrayEquals(BACK_RAW_LINES, updated.backRawLines());
        assertEquals(KEY, entry.key(), "the original entry should be unmodified");
    }

    @Test
    void equalsAndHashCodeToleratesNullFields() {
        var entryWithNullKey = new SignEntry(null, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS, FRONT_RAW_LINES, BACK_RAW_LINES, FRONT_DYE, BACK_DYE);

        assertFalse(entryWithNullKey.equals(baseEntry()));
        assertDoesNotThrow(entryWithNullKey::hashCode);

        var entryWithAllNullFields = new SignEntry(null, null, null, null, 0L, null, null, null, null);
        var otherEntryWithAllNullFields = new SignEntry(null, null, null, null, 0L, null, null, null, null);

        assertEquals(entryWithAllNullFields, otherEntryWithAllNullFields);
        assertEquals(entryWithAllNullFields.hashCode(), otherEntryWithAllNullFields.hashCode());
    }
}
