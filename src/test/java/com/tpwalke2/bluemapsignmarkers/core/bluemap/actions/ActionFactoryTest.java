package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ActionFactoryTest {

    @Test
    void createAddPOIActionBuildsTheMarkerIdentifierAndActionFields() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var action = factory.createAddPOIAction(1, 2, 3, "world", "label", "detail", group);

        assertEquals(1, action.getMarkerIdentifier().x());
        assertEquals(2, action.getMarkerIdentifier().y());
        assertEquals(3, action.getMarkerIdentifier().z());
        assertEquals("world", action.getMarkerIdentifier().parentSet().mapId());
        assertEquals(group, action.getMarkerIdentifier().parentSet().markerGroup());
        assertEquals("label", action.getLabel());
        assertEquals("detail", action.getDetail());
    }

    @Test
    void createRemovePOIActionBuildsTheMarkerIdentifier() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var action = factory.createRemovePOIAction(1, 2, 3, "world", group);

        assertEquals(1, action.getMarkerIdentifier().x());
        assertEquals(2, action.getMarkerIdentifier().y());
        assertEquals(3, action.getMarkerIdentifier().z());
        assertEquals("world", action.getMarkerIdentifier().parentSet().mapId());
        assertEquals(group, action.getMarkerIdentifier().parentSet().markerGroup());
    }

    @Test
    void createUpdatePOIActionBuildsTheMarkerIdentifierAndActionFields() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var action = factory.createUpdatePOIAction(1, 2, 3, "world", "new label", "new detail", group);

        assertEquals(1, action.getMarkerIdentifier().x());
        assertEquals(2, action.getMarkerIdentifier().y());
        assertEquals(3, action.getMarkerIdentifier().z());
        assertEquals("world", action.getMarkerIdentifier().parentSet().mapId());
        assertEquals(group, action.getMarkerIdentifier().parentSet().markerGroup());
        assertEquals("new label", action.getNewLabel());
        assertEquals("new detail", action.getNewDetails());
    }

    @Test
    void repeatedCallsForTheSameMapAndGroupReuseTheSameMarkerSetIdentifier() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var first = factory.createAddPOIAction(1, 2, 3, "world", "label", "detail", group);
        var second = factory.createAddPOIAction(4, 5, 6, "world", "other label", "other detail", group);

        assertSame(first.getMarkerIdentifier().parentSet(), second.getMarkerIdentifier().parentSet());
    }

    @Test
    void differentActionTypesForTheSameMapAndGroupReuseTheSameMarkerSetIdentifier() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var added = factory.createAddPOIAction(1, 2, 3, "world", "label", "detail", group);
        var removed = factory.createRemovePOIAction(4, 5, 6, "world", group);
        var updated = factory.createUpdatePOIAction(7, 8, 9, "world", "new label", "new detail", group);

        assertSame(added.getMarkerIdentifier().parentSet(), removed.getMarkerIdentifier().parentSet());
        assertSame(added.getMarkerIdentifier().parentSet(), updated.getMarkerIdentifier().parentSet());
    }

    private static MarkerGroup markerGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, prefix, "icon.png", 0, 0, false, 0, 0);
    }
}
