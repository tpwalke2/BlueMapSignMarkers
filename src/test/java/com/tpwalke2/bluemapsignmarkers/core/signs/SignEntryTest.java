package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

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

    private static SignEntry baseEntry() {
        return new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS);
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
                CREATED_AT_MILLIS);

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
        var differentPlayerId = new SignEntry(KEY, "player-2", FRONT, BACK, CREATED_AT_MILLIS);

        assertNotEquals(entry, differentPlayerId);
    }

    @Test
    void equalsReturnsFalseForADifferentFrontText() {
        var entry = baseEntry();
        var differentFrontText = new SignEntry(KEY, PLAYER_ID, new SignLinesParseResult("[poi]", "other", "detail"), BACK, CREATED_AT_MILLIS);

        assertNotEquals(entry, differentFrontText);
    }

    @Test
    void equalsReturnsFalseForADifferentBackText() {
        var entry = baseEntry();
        var differentBackText = new SignEntry(KEY, PLAYER_ID, FRONT, new SignLinesParseResult(null, "other", ""), CREATED_AT_MILLIS);

        assertNotEquals(entry, differentBackText);
    }

    @Test
    void equalsReturnsFalseForADifferentCreatedAtMillis() {
        var entry = baseEntry();
        var differentCreatedAtMillis = new SignEntry(KEY, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS + 1);

        assertNotEquals(entry, differentCreatedAtMillis);
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
        assertEquals(KEY, entry.key(), "the original entry should be unmodified");
    }

    @Test
    void equalsAndHashCodeToleratesNullFields() {
        var entryWithNullKey = new SignEntry(null, PLAYER_ID, FRONT, BACK, CREATED_AT_MILLIS);

        assertFalse(entryWithNullKey.equals(baseEntry()));
        assertDoesNotThrow(entryWithNullKey::hashCode);

        var entryWithAllNullFields = new SignEntry(null, null, null, null, 0L);
        var otherEntryWithAllNullFields = new SignEntry(null, null, null, null, 0L);

        assertEquals(entryWithAllNullFields, otherEntryWithAllNullFields);
        assertEquals(entryWithAllNullFields.hashCode(), otherEntryWithAllNullFields.hashCode());
    }
}
