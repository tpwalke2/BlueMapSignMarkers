package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;

import java.util.List;

// Replaces the former SetLineMarkerAction/SetShapeMarkerAction/SetExtrudeMarkerAction - those three were
// structurally identical (label/detail/points/lineWidth/lineColor/isFirstAppearance), differing only in
// an optional fillColor (SHAPE/EXTRUDE, absent for LINE) and their minimum-points threshold, which the
// caller (ActionFactory) now supplies from MultiPointGroupThresholds' LINE_MIN_MEMBERS/SHAPE_MIN_MEMBERS/
// EXTRUDE_MIN_MEMBERS - the same single source of truth SignTransitionResolver itself uses (via static
// import) to decide when a line/shape/extrude marker should exist at all.
public class SetMultiPointMarkerAction extends MarkerAction {
    private final String label;
    private final String detail;
    private final List<LinePoint> points;
    private final int lineWidth;
    private final String lineColor;
    private final String fillColor;
    // Log-only: whether this marker is being created for the first time vs. re-set with an updated point
    // list. Not a distinct subtype - BlueMap has no separate add/update call for line/shape/extrude
    // markers, so this only affects the log message, not the BlueMap API call made.
    private final boolean isFirstAppearance;

    public SetMultiPointMarkerAction(
            MultiPointMarkerIdentifier markerIdentifier,
            String label,
            String detail,
            List<LinePoint> points,
            int lineWidth,
            String lineColor,
            String fillColor,
            int minPoints,
            boolean isFirstAppearance) {
        super(markerIdentifier);
        this.label = MarkerActionValidation.requireNonNullField(label, "label", "SetMultiPointMarkerAction");
        this.detail = MarkerActionValidation.requireNonNullField(detail, "detail", "SetMultiPointMarkerAction");
        this.points = MarkerActionValidation.requireMinPoints(points, minPoints, "SetMultiPointMarkerAction");
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
        return "SetMultiPointMarkerAction{" +
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
