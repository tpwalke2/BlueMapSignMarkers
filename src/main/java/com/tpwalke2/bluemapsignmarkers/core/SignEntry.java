package com.tpwalke2.bluemapsignmarkers.core;

import java.util.Arrays;

public record SignEntry(
        SignEntryKey key,
        String[] frontTextLines,
        String[] backTextLines) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignEntry signEntry)) return false;

        return key.equals(signEntry.key)
                && Arrays.equals(frontTextLines, signEntry.frontTextLines)
                && Arrays.equals(backTextLines, signEntry.backTextLines);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + Arrays.hashCode(frontTextLines);
        result = 31 * result + Arrays.hashCode(backTextLines);
        return result;
    }

    @Override
    public String toString() {
        return "SignEntry{" +
                "key=" + key +
                ", frontTextLines=" + frontTextLines +
                ", backTextLines=" + backTextLines +
                '}';
    }
}
