package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.ShapeMarkerIdentifier;

import java.util.List;

public class SetShapeMarkerAction extends MarkerAction {
    private final String label;
    private final String detail;
    private final List<LinePoint> points;
    private final int lineWidth;
    private final String lineColor;
    private final String fillColor;
    // Log-only: whether this shape is being created for the first time vs. re-set with an updated point
    // list. Not a distinct subtype - BlueMap has no separate add/update call for shape markers, so this
    // only affects the log message, not the BlueMap API call made.
    private final boolean isFirstAppearance;

    public SetShapeMarkerAction(
            ShapeMarkerIdentifier markerIdentifier,
            String label,
            String detail,
            List<LinePoint> points,
            int lineWidth,
            String lineColor,
            String fillColor,
            boolean isFirstAppearance) {
        super(markerIdentifier);
        this.label = label;
        this.detail = detail;
        this.points = points;
        this.lineWidth = lineWidth;
        this.lineColor = lineColor;
        this.fillColor = fillColor;
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

    public String getFillColor() {
        return fillColor;
    }

    public boolean isFirstAppearance() {
        return isFirstAppearance;
    }

    @Override
    public String toString() {
        return "SetShapeMarkerAction{" +
                "markerIdentifier=" + getMarkerIdentifier() +
                ", label='" + label + '\'' +
                ", detail='" + detail + '\'' +
                ", points=" + points +
                ", lineWidth=" + lineWidth +
                ", lineColor='" + lineColor + '\'' +
                ", fillColor='" + fillColor + '\'' +
                ", isFirstAppearance=" + isFirstAppearance +
                '}';
    }
}
