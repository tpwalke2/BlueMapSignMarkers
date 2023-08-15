package com.tpwalke2.bluemapsignmarkers.core;

public record SignEntry(int x, int y, int z, SignSide side, SignParentMap signParentMap, String allText) {
    public SignEntryKey getKey() {
        return new SignEntryKey(this.x(), this.y(), this.z(), this.side(), this.signParentMap());
    }
}
