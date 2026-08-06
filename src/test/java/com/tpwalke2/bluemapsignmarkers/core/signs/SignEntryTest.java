package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignEntryTest {

    private static final SignEntryKey KEY = new SignEntryKey(1, 2, 3, "world");
    private static final String PLAYER_ID = "player-1";
    private static final SignLinesParseResult FRONT = new SignLinesParseResult("[poi]", "label", "detail");
    private static final SignLinesParseResult BACK = new SignLinesParseResult(null, "", "");

    private static SignEntry baseEntry() {
        return new SignEntry(KEY, PLAYER_ID, FRONT, BACK);
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
                new SignLinesParseResult(null, "", ""));

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
        var differentPlayerId = new SignEntry(KEY, "player-2", FRONT, BACK);

        assertNotEquals(entry, differentPlayerId);
    }

    @Test
    void equalsReturnsFalseForADifferentFrontText() {
        var entry = baseEntry();
        var differentFrontText = new SignEntry(KEY, PLAYER_ID, new SignLinesParseResult("[poi]", "other", "detail"), BACK);

        assertNotEquals(entry, differentFrontText);
    }

    @Test
    void equalsReturnsFalseForADifferentBackText() {
        var entry = baseEntry();
        var differentBackText = new SignEntry(KEY, PLAYER_ID, FRONT, new SignLinesParseResult(null, "other", ""));

        assertNotEquals(entry, differentBackText);
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
        assertEquals(KEY, entry.key(), "the original entry should be unmodified");
    }

    // Documents a latent risk flagged in plans/codebase-review-2026-07-11.md: the hand-written equals/hashCode
    // call straight into key.equals(...)/key.hashCode() with no null guard, so an entry whose own key (or
    // playerId/frontText/backText, by the same unguarded pattern) is null throws NPE instead of behaving like a
    // normal equals/hashCode implementation. Currently latent only - no call site actually calls
    // SignEntry.equals()/hashCode(), since the sign cache is keyed by SignEntryKey, not SignEntry itself.
    @Test
    void equalsAndHashCodeThrowNpeWhenThisEntrysKeyIsNull() {
        var entryWithNullKey = new SignEntry(null, PLAYER_ID, FRONT, BACK);

        assertThrows(NullPointerException.class, () -> entryWithNullKey.equals(baseEntry()));
        assertThrows(NullPointerException.class, entryWithNullKey::hashCode);
    }
}
