package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.Objects;

public record SignEntry(
        SignEntryKey key,
        String playerId,
        SignLinesParseResult frontText,
        SignLinesParseResult backText,
        long createdAtMillis) {

    public SignEntry withKey(SignEntryKey key) {
        return new SignEntry(key, playerId, frontText, backText, createdAtMillis);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignEntry signEntry)) return false;

        return Objects.equals(key, signEntry.key)
                && Objects.equals(playerId, signEntry.playerId)
                && Objects.equals(frontText, signEntry.frontText)
                && Objects.equals(backText, signEntry.backText)
                && createdAtMillis == signEntry.createdAtMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, playerId, frontText, backText, createdAtMillis);
    }

    @Override
    public String toString() {
        return "SignEntry{" +
                "key=" + key +
                ", playerId='" + playerId + "'" +
                ", frontText=" + frontText +
                ", backText=" + backText +
                ", createdAtMillis=" + createdAtMillis +
                '}';
    }
}
