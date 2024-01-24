package com.tpwalke2.bluemapsignmarkers.core.signs;

public record SignEntryKey(
        int x,
        int y,
        int z,
        String parentMap) {

    public SignEntryKey withParentMap(String parentMap) {
        return new SignEntryKey(x, y, z, parentMap);
    }
}
