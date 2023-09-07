package com.tpwalke2.bluemapsignmarkers.core.signs;

public record SignEntry(
        SignEntryKey key,
        SignLinesParseResult frontTextLines,
        SignLinesParseResult backTextLines) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignEntry signEntry)) return false;

        return key.equals(signEntry.key)
                && frontTextLines.equals(signEntry.frontTextLines)
                && backTextLines.equals(signEntry.backTextLines);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + frontTextLines.hashCode();
        result = 31 * result + backTextLines.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SignEntry{" +
                "key=" + key +
                ", frontTextLines=" + frontTextLines.toString() +
                ", backTextLines=" + backTextLines.toString() +
                '}';
    }
}
