package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.Arrays;
import java.util.Objects;

// frontRawLines/backRawLines are null (not empty arrays) when raw text isn't available - either a
// pre-V5 entry migrated without it, or (rarely) a sign side with zero messages. null is the sentinel
// SignManager.reloadConfig checks before attempting a reparse-from-source.
public record SignEntry(
        SignEntryKey key,
        String playerId,
        SignLinesParseResult frontText,
        SignLinesParseResult backText,
        long createdAtMillis,
        String[] frontRawLines,
        String[] backRawLines) {

    public SignEntry withKey(SignEntryKey key) {
        return new SignEntry(key, playerId, frontText, backText, createdAtMillis, frontRawLines, backRawLines);
    }

    public SignEntry withParsedText(SignLinesParseResult frontText, SignLinesParseResult backText) {
        return new SignEntry(key, playerId, frontText, backText, createdAtMillis, frontRawLines, backRawLines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignEntry signEntry)) return false;

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

    @Override
    public String toString() {
        return "SignEntry{" +
                "key=" + key +
                ", playerId='" + playerId + "'" +
                ", frontText=" + frontText +
                ", backText=" + backText +
                ", createdAtMillis=" + createdAtMillis +
                ", frontRawLines=" + Arrays.toString(frontRawLines) +
                ", backRawLines=" + Arrays.toString(backRawLines) +
                '}';
    }
}
