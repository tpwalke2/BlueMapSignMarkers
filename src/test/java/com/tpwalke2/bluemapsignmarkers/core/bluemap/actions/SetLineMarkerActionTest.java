package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetLineMarkerActionTest {

    @Test
    void constructorRejectsAnEmptyPointList() {
        assertThrows(IllegalArgumentException.class, () -> newAction(List.of()));
    }

    @Test
    void constructorRejectsASinglePointList() {
        assertThrows(IllegalArgumentException.class, () -> newAction(List.of(new LinePoint(1, 2, 3))));
    }

    @Test
    void constructorAcceptsTwoPoints() {
        var points = List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6));
        var action = newAction(points);

        assertEquals(points, action.getPoints());
    }

    @Test
    void constructorDefensivelyCopiesThePointsList() {
        var mutablePoints = new ArrayList<>(List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6)));
        var action = newAction(mutablePoints);

        mutablePoints.add(new LinePoint(7, 8, 9));

        assertEquals(2, action.getPoints().size());
    }

    @Test
    void constructorRejectsANullLabel() {
        assertThrows(NullPointerException.class, () -> new SetLineMarkerAction(
                identifier(), null, "detail", twoPoints(), 2, "#FFFFFFFF", true));
    }

    @Test
    void constructorRejectsANullDetail() {
        assertThrows(NullPointerException.class, () -> new SetLineMarkerAction(
                identifier(), "label", null, twoPoints(), 2, "#FFFFFFFF", true));
    }

    private static SetLineMarkerAction newAction(List<LinePoint> points) {
        return new SetLineMarkerAction(identifier(), "label", "detail", points, 2, "#FFFFFFFF", true);
    }

    private static List<LinePoint> twoPoints() {
        return List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6));
    }

    private static MultiPointMarkerIdentifier identifier() {
        var group = new MarkerGroup(
                "[line]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.LINE, "[line]", null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
        return new MultiPointMarkerIdentifier("line", "label", new MarkerSetIdentifier("world", group));
    }
}
