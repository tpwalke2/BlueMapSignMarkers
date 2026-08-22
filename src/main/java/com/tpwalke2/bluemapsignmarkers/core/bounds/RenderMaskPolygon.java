package com.tpwalke2.bluemapsignmarkers.core.bounds;

import java.util.List;

record RenderMaskPolygon(List<RenderMaskPoint> points, int minY, int maxY, boolean subtract)
        implements RenderMaskShape {

    // Standard ray-casting point-in-polygon test on the XZ plane: count how many polygon edges
    // cross a ray cast from the point in the +x direction, odd count = inside. Works for
    // non-convex polygons.
    @Override
    public boolean contains(int x, int y, int z) {
        if (y < minY || y > maxY) {
            return false;
        }

        var inside = false;
        var pointCount = points.size();
        for (int i = 0, j = pointCount - 1; i < pointCount; j = i++) {
            var pi = points.get(i);
            var pj = points.get(j);
            var crossesRay = ((pi.z() > z) != (pj.z() > z))
                    && (x < (pj.x() - pi.x()) * (z - pi.z()) / (pj.z() - pi.z()) + pi.x());
            if (crossesRay) {
                inside = !inside;
            }
        }
        return inside;
    }
}
