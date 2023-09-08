package com.tpwalke2.bluemapsignmarkers.core.signs;

public record SignEntry(
        SignEntryKey key,
        String playerId,
        SignLinesParseResult frontText,
        SignLinesParseResult backText) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignEntry signEntry)) return false;

        return key.equals(signEntry.key)
                && frontText.equals(signEntry.frontText)
                && backText.equals(signEntry.backText);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + frontText.hashCode();
        result = 31 * result + backText.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SignEntry{" +
                "key=" + key +
                ", frontText=" + frontText.toString() +
                ", backText=" + backText.toString() +
                '}';
    }
}
