package com.tpwalke2.bluemapsignmarkers.core.markers;

import java.util.stream.Stream;

public enum MarkerGroupType {
    POI("Points of Interest"),
    LINE("Lines");

    public final String defaultLabel;

    MarkerGroupType(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public static Stream<MarkerGroupType> stream() {
        return Stream.of(MarkerGroupType.values());
    }
}
