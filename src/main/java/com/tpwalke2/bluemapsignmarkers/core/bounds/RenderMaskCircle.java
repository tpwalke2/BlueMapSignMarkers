package com.tpwalke2.bluemapsignmarkers.core.bounds;

record RenderMaskCircle(double centerX, double centerZ, double radius, int minY, int maxY, boolean subtract)
        implements RenderMaskShape {

    @Override
    public boolean contains(int x, int y, int z) {
        if (y < minY || y > maxY) {
            return false;
        }
        var dx = x - centerX;
        var dz = z - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }
}
