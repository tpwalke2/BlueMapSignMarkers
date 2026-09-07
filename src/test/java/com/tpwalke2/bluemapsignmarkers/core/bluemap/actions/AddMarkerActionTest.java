package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AddMarkerActionTest {

    @Test
    void constructorRejectsANullLabel() {
        assertThrows(NullPointerException.class, () -> new AddMarkerAction(identifier(), null, "detail"));
    }

    @Test
    void constructorRejectsANullDetail() {
        assertThrows(NullPointerException.class, () -> new AddMarkerAction(identifier(), "label", null));
    }

    private static MarkerIdentifier identifier() {
        var group = new MarkerGroup(
                "[poi]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, "[poi]", "icon.png", 0, 0, false, 0,
                0, 2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
        return new MarkerIdentifier(1, 2, 3, new MarkerSetIdentifier("world", group));
    }
}
