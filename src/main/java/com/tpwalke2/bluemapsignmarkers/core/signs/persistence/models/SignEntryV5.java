package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;

import java.util.Arrays;
import java.util.Objects;

// Frozen shape of SignEntry as it existed at V5, before dye fields were added (V6) - kept as its own
// model so old region files still deserialize correctly, same pattern as SignEntryV4.
public record SignEntryV5(
        SignEntryKey key,
        String playerId,
        SignLinesParseResult frontText,
        SignLinesParseResult backText,
        long createdAtMillis,
        String[] frontRawLines,
        String[] backRawLines) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignEntryV5 signEntry)) return false;

        return Objects.equals(key, signEntry.key)
                && Objects.equals(playerId, signEntry.playerId)
                && Objects.equals(frontText, signEntry.frontText)
                && Objects.equals(backText, signEntry.backText)
                && createdAtMillis == signEntry.createdAtMillis
                && Arrays.equals(frontRawLines, signEntry.frontRawLines)
                && Arrays.equals(backRawLines, signEntry.backRawLines);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(key, playerId, frontText, backText, createdAtMillis);
        result = 31 * result + Arrays.hashCode(frontRawLines);
        result = 31 * result + Arrays.hashCode(backRawLines);
        return result;
    }
}
