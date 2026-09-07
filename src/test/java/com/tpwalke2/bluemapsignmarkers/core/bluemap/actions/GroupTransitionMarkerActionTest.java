package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupTransitionMarkerActionTest {

    @Test
    void constructorRejectsAnEmptyEffectsList() {
        assertThrows(IllegalArgumentException.class, () -> new GroupTransitionMarkerAction(List.of()));
    }

    @Test
    void getMarkerIdentifierReturnsTheSoleEffectsIdentifierForASingleEffect() {
        var effect = new RemoveMarkerAction(identifier(1, 2, 3));

        var action = new GroupTransitionMarkerAction(List.of(effect));

        assertSame(effect.getMarkerIdentifier(), action.getMarkerIdentifier());
    }

    @Test
    void getMarkerIdentifierReturnsTheFirstEffectsIdentifierSpecifically() {
        var first = new RemoveMarkerAction(identifier(1, 2, 3));
        var second = new AddMarkerAction(identifier(4, 5, 6), "label", "detail");

        var action = new GroupTransitionMarkerAction(List.of(first, second));

        assertSame(first.getMarkerIdentifier(), action.getMarkerIdentifier());
        assertEquals(2, action.effects().size());
    }

    private static MarkerIdentifier identifier(int x, int y, int z) {
        var group = new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "[poi]", "icon.png", 0, 0, false, 0,
                0, 2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
        return new MarkerIdentifier(x, y, z, new MarkerSetIdentifier("world", group));
    }
}
