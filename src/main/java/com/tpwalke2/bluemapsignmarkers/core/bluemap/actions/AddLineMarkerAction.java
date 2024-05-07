package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.BlockPosition;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;

public class AddLineMarkerAction extends MarkerAction {
    private final String label;
    private final BlockPosition[] points;

    public AddLineMarkerAction(MarkerIdentifier markerIdentifier,
                               String label,
                               BlockPosition[] points) {
        super(markerIdentifier);
        this.label = label;
        this.points = points;
    }

    public String getLabel() {
        return label;
    }

    public BlockPosition[] getPoints() {
        return points;
    }
}
