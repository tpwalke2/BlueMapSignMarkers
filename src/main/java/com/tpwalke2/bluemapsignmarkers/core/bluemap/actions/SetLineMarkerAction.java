package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LineMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;

import java.util.List;

public class SetLineMarkerAction extends MarkerAction {
    private final String label;
    private final String detail;
    private final List<LinePoint> points;
    private final int lineWidth;
    private final String lineColor;
    // Log-only: whether this line is being created for the first time vs. re-set with an updated point
    // list. Not a distinct subtype - BlueMap has no separate add/update call for line markers, so this
    // only affects the log message, not the BlueMap API call made.
    private final boolean isFirstAppearance;

    public SetLineMarkerAction(
            LineMarkerIdentifier markerIdentifier,
            String label,
            String detail,
            List<LinePoint> points,
            int lineWidth,
            String lineColor,
            boolean isFirstAppearance) {
        super(markerIdentifier);
        this.label = label;
        this.detail = detail;
        this.points = points;
        this.lineWidth = lineWidth;
        this.lineColor = lineColor;
        this.isFirstAppearance = isFirstAppearance;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    public List<LinePoint> getPoints() {
        return points;
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public String getLineColor() {
        return lineColor;
    }

    public boolean isFirstAppearance() {
        return isFirstAppearance;
    }

    @Override
    public String toString() {
        return "SetLineMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                ", label='" + label + '\'' +
                ", detail='" + detail + '\'' +
                ", points=" + points +
                ", lineWidth=" + lineWidth +
                ", lineColor='" + lineColor + '\'' +
                ", isFirstAppearance=" + isFirstAppearance +
                '}';
    }
}
