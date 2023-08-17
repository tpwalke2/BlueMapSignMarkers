package com.tpwalke2.bluemapsignmarkers.core;

public record SignEntry(int x, int y, int z, SignParentMap signParentMap, String frontText, String backText) {
    public SignEntryKey getKey() {
        return new SignEntryKey(this.x(), this.y(), this.z(), this.signParentMap());
    }
}
