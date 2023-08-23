package com.tpwalke2.bluemapsignmarkers.core;

import java.util.HashMap;
import java.util.Map;

public enum WorldMap {
    UNKNOWN("unknown"),
    OVERWORLD("overworld"),
    NETHER("the_nether"),
    END("the_end");

    public final String id;

    WorldMap(String id) {
        this.id = id;
    }

    public static WorldMap valueOfId(String id) {
        return BY_ID.get(id);
    }

    private static final Map<String, WorldMap> BY_ID = new HashMap<>();

    static {
        for (WorldMap m: values()) {
            BY_ID.put(m.id, m);
        }
    }
}
