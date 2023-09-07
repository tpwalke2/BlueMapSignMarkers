package com.tpwalke2.bluemapsignmarkers.core.markers;

public record MarkerIdentifier(int x, int y, int z, MarkerSetIdentifier parentSet) {
    public String getId() {
        return String.format("x%d_y%d_z%d", x, y, z);
    }
}
