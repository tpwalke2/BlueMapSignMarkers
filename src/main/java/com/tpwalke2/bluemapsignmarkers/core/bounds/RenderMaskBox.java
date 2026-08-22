package com.tpwalke2.bluemapsignmarkers.core.bounds;

record RenderMaskBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, boolean subtract)
        implements RenderMaskShape {

    @Override
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
