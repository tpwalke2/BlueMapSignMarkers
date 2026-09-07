package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.ExtrudeMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;

import java.util.List;

public class SetExtrudeMarkerAction extends MarkerAction {
    // An extrude marker only exists once EXTRUDE_MIN_MEMBERS (3) signs share the same group/label - see
    // SignTransitionResolver.EXTRUDE_MIN_MEMBERS.
    private static final int MIN_POINTS = 3;

    private final String label;
    private final String detail;
    private final List<LinePoint> points;
    private final int lineWidth;
    private final String lineColor;
    private final String fillColor;
    // Log-only: whether this extrude volume is being created for the first time vs. re-set with an updated
    // point list. Not a distinct subtype - BlueMap has no separate add/update call for extrude markers, so
    // this only affects the log message, not the BlueMap API call made.
    private final boolean isFirstAppearance;

    public SetExtrudeMarkerAction(
            ExtrudeMarkerIdentifier markerIdentifier,
            String label,
            String detail,
            List<LinePoint> points,
            int lineWidth,
            String lineColor,
            String fillColor,
            boolean isFirstAppearance) {
        super(markerIdentifier);
        this.label = MarkerActionValidation.requireNonNullField(label, "label", "SetExtrudeMarkerAction");
        this.detail = MarkerActionValidation.requireNonNullField(detail, "detail", "SetExtrudeMarkerAction");
        this.points = MarkerActionValidation.requireMinPoints(points, MIN_POINTS, "SetExtrudeMarkerAction");
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
        return "SetExtrudeMarkerAction{" +
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
