package com.tpwalke2.bluemapsignmarkers.core.bounds;

record RenderMaskEllipse(
        double centerX, double centerZ, double radiusX, double radiusZ, int minY, int maxY, boolean subtract)
        implements RenderMaskShape {

    @Override
    public boolean contains(int x, int y, int z) {
        if (y < minY || y > maxY) {
            return false;
        }
        var dx = (x - centerX) / radiusX;
        var dz = (z - centerZ) / radiusZ;
        return dx * dx + dz * dz <= 1.0;
    }
}
