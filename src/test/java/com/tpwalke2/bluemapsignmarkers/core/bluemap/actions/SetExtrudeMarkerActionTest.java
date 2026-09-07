package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.ExtrudeMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetExtrudeMarkerActionTest {

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
        assertThrows(NullPointerException.class, () -> new SetExtrudeMarkerAction(
                identifier(), null, "detail", threePoints(), 2, "#FFFFFFFF", "#FFFFFF33", true));
    }

    @Test
    void constructorRejectsANullDetail() {
        assertThrows(NullPointerException.class, () -> new SetExtrudeMarkerAction(
                identifier(), "label", null, threePoints(), 2, "#FFFFFFFF", "#FFFFFF33", true));
    }

    private static SetExtrudeMarkerAction newAction(List<LinePoint> points) {
        return new SetExtrudeMarkerAction(identifier(), "label", "detail", points, 2, "#FFFFFFFF", "#FFFFFF33", true);
    }

    private static List<LinePoint> threePoints() {
        return List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6), new LinePoint(7, 8, 9));
    }

    private static ExtrudeMarkerIdentifier identifier() {
        var group = new MarkerGroup(
                "[extrude]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.EXTRUDE, "[extrude]", null, 0, 0,
                false, 0, 0, 2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
        return new ExtrudeMarkerIdentifier("label", new MarkerSetIdentifier("world", group));
    }
}
