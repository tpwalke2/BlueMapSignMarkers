package com.tpwalke2.bluemapsignmarkers.core.markers;

// Single source of truth for how many signs a LINE/SHAPE/EXTRUDE group needs before its marker exists -
// shared by SignTransitionResolver (decides when to join/leave/recompute a line/shape/extrude) and
// SetMultiPointMarkerAction/ActionFactory (validates/constructs the resulting marker action). Lives here,
// not in core.signs or core.bluemap.actions, because both of those packages already depend on
// core.markers and neither needs to depend on the other just to share these three numbers.
public final class MultiPointGroupThresholds {
    public static final int LINE_MIN_MEMBERS = 2;
    public static final int SHAPE_MIN_MEMBERS = 3;
    public static final int EXTRUDE_MIN_MEMBERS = 3;

    private MultiPointGroupThresholds() {
    }
}
