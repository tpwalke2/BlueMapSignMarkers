package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointGroupThresholds;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// A SetMultiPointMarkerAction constructed with the SHAPE minimum (3 points) and a fillColor - see
// SetLineMarkerActionTest for the LINE minimum (2 points, no fillColor).
class SetShapeMarkerActionTest {
    private static final int MIN_POINTS = MultiPointGroupThresholds.SHAPE_MIN_MEMBERS;

    @Test
    void constructorRejectsAnEmptyPointList() {
        assertThrows(IllegalArgumentException.class, () -> newAction(List.of()));
    }

    @Test
    void constructorRejectsFewerThanThreePoints() {
        assertThrows(IllegalArgumentException.class,
                () -> newAction(List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6))));
    }

    @Test
    void constructorAcceptsThreePoints() {
        var points = threePoints();
        var action = newAction(points);

        assertEquals(points, action.getPoints());
    }

    @Test
    void constructorDefensivelyCopiesThePointsList() {
        var mutablePoints = new ArrayList<>(threePoints());
        var action = newAction(mutablePoints);

        mutablePoints.add(new LinePoint(10, 11, 12));

        assertEquals(3, action.getPoints().size());
    }

    @Test
    void constructorRejectsANullLabel() {
        assertThrows(NullPointerException.class, () -> new SetMultiPointMarkerAction(
                identifier(), null, "detail", threePoints(), 2, "#FFFFFFFF", "#FFFFFF33", MIN_POINTS, true));
    }

    @Test
    void constructorRejectsANullDetail() {
        assertThrows(NullPointerException.class, () -> new SetMultiPointMarkerAction(
                identifier(), "label", null, threePoints(), 2, "#FFFFFFFF", "#FFFFFF33", MIN_POINTS, true));
    }

    private static SetMultiPointMarkerAction newAction(List<LinePoint> points) {
        return new SetMultiPointMarkerAction(identifier(), "label", "detail", points, 2, "#FFFFFFFF", "#FFFFFF33", MIN_POINTS, true);
    }

    private static List<LinePoint> threePoints() {
        return List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6), new LinePoint(7, 8, 9));
    }

    private static MultiPointMarkerIdentifier identifier() {
        var group = new MarkerGroup(
                "[shape]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.SHAPE, "[shape]", null, 0, 0, false, 0,
                0, 2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
        return new MultiPointMarkerIdentifier("shape", "label", new MarkerSetIdentifier("world", group));
    }
}
