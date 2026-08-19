package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;

public record SignEntryV4(
        SignEntryKey key,
        String playerId,
        SignLinesParseResult frontText,
        SignLinesParseResult backText,
        long createdAtMillis) {

    public SignEntryV4 withKey(SignEntryKey key) {
        return new SignEntryV4(key, playerId, frontText, backText, createdAtMillis);
    }

    @Override
    public String toString() {
        return "SignEntryV4{" +
                "key=" + key +
                ", playerId='" + playerId + "'" +
                ", frontText=" + frontText.toString() +
                ", backText=" + backText.toString() +
                ", createdAtMillis=" + createdAtMillis +
                '}';
    }
}
