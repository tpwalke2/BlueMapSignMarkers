package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignLinesParseResult;

public record SignEntryV3(
        SignEntryKey key,
        String playerId,
        SignLinesParseResult frontText,
        SignLinesParseResult backText) {

    public SignEntryV3 withKey(SignEntryKey key) {
        return new SignEntryV3(key, playerId, frontText, backText);
    }

    @Override
    public String toString() {
        return "SignEntryV3{" +
                "key=" + key +
                ", playerId='" + playerId + "'" +
                ", frontText=" + frontText.toString() +
                ", backText=" + backText.toString() +
                '}';
    }
}
