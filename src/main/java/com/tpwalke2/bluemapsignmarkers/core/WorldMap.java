package com.tpwalke2.bluemapsignmarkers.core;

public enum WorldMap {
    UNKNOWN("unknown"),
    OVERWORLD("overworld"),
    NETHER("nether"),
    END("end");

    public final String id;

    WorldMap(String id) {
        this.id = id;
    }
}
