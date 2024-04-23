package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;

public record SignEntryV2(
        SignEntryKey key,
        String playerId,
        SignLinesParseResultV2 frontText,
        SignLinesParseResultV2 backText) {

    public SignEntryV2 withKey(SignEntryKey key) {
        return new SignEntryV2(key, playerId, frontText, backText);
    }

    @Override
    public String toString() {
        return "SignEntryV2{" +
                "key=" + key +
                ", playerId='" + playerId + "'" +
                ", frontText=" + frontText.toString() +
                ", backText=" + backText.toString() +
                '}';
    }
}