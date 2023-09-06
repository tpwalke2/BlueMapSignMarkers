package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.WorldMap;

public record SignEntryKey(int x, int y, int z, WorldMap parentMap) {
}
