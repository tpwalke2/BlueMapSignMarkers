package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LineMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionFactoryTest {

    @Test
    void createAddPOIActionBuildsTheMarkerIdentifierAndActionFields() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var action = factory.createAddPOIAction(1, 2, 3, "world", "label", "detail", group);
        var identifier = (MarkerIdentifier) action.getMarkerIdentifier();

        assertEquals(1, identifier.x());
        assertEquals(2, identifier.y());
        assertEquals(3, identifier.z());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
        assertEquals("label", action.getLabel());
        assertEquals("detail", action.getDetail());
    }

    @Test
    void createRemovePOIActionBuildsTheMarkerIdentifier() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var action = factory.createRemovePOIAction(1, 2, 3, "world", group);
        var identifier = (MarkerIdentifier) action.getMarkerIdentifier();

        assertEquals(1, identifier.x());
        assertEquals(2, identifier.y());
        assertEquals(3, identifier.z());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
    }

    @Test
    void createUpdatePOIActionBuildsTheMarkerIdentifierAndActionFields() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = markerGroup("[poi]");

        var action = factory.createUpdatePOIAction(1, 2, 3, "world", "new label", "new detail", group);
        var identifier = (MarkerIdentifier) action.getMarkerIdentifier();

        assertEquals(1, identifier.x());
        assertEquals(2, identifier.y());
        assertEquals(3, identifier.z());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
        assertEquals("new label", action.getNewLabel());
        assertEquals("new detail", action.getNewDetails());
    }

    @Test
    void createChangeGroupPOIActionBuildsARemoveAndAddEffectPair() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var oldGroup = markerGroup("[event]");
        var newGroup = markerGroup("[poi]");

        var action = factory.createChangeGroupPOIAction(1, 2, 3, "world", "label", "detail", oldGroup, newGroup);

        assertEquals(2, action.effects().size());
        var removeAction = assertInstanceOf(RemoveMarkerAction.class, action.effects().get(0));
        var addAction = assertInstanceOf(AddMarkerAction.class, action.effects().get(1));

        var oldIdentifier = (MarkerIdentifier) removeAction.getMarkerIdentifier();
        var newIdentifier = (MarkerIdentifier) addAction.getMarkerIdentifier();

        assertEquals(oldGroup, oldIdentifier.parentSet().markerGroup());
        assertEquals(newGroup, newIdentifier.parentSet().markerGroup());
        assertEquals(1, newIdentifier.x());
        assertEquals(2, newIdentifier.y());
        assertEquals(3, newIdentifier.z());
        assertEquals("world", oldIdentifier.parentSet().mapId());
        assertEquals("label", addAction.getLabel());
        assertEquals("detail", addAction.getDetail());
    }

    @Test
    void createSetLineActionBuildsTheLineMarkerIdentifierAndActionFields() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = lineMarkerGroup("[line]");
        var points = List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6));

        var action = factory.createSetLineAction("world", group, "label", "detail", points, true);
        var identifier = (LineMarkerIdentifier) action.getMarkerIdentifier();

        assertEquals("label", identifier.label());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
        assertEquals("label", action.getLabel());
        assertEquals("detail", action.getDetail());
        assertEquals(points, action.getPoints());
        assertEquals(group.lineWidth(), action.getLineWidth());
        assertEquals(group.lineColor(), action.getLineColor());
        assertTrue(action.isFirstAppearance());
    }

    @Test
    void createRemoveLineActionBuildsTheLineMarkerIdentifier() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = lineMarkerGroup("[line]");

        var action = factory.createRemoveLineAction("world", group, "label");
        var identifier = (LineMarkerIdentifier) action.getMarkerIdentifier();

        assertEquals("label", identifier.label());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
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

    @Test
    void createSetShapeActionBuildsTheShapeMarkerIdentifierAndActionFields() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = shapeMarkerGroup("[shape]");
        var points = List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6), new LinePoint(7, 8, 9));

        var action = factory.createSetShapeAction("world", group, "label", "detail", points, true);
        var identifier = (com.tpwalke2.bluemapsignmarkers.core.markers.ShapeMarkerIdentifier) action.getMarkerIdentifier();

        assertEquals("label", identifier.label());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
        assertEquals("label", action.getLabel());
        assertEquals("detail", action.getDetail());
        assertEquals(points, action.getPoints());
        assertEquals(group.lineWidth(), action.getLineWidth());
        assertEquals(group.lineColor(), action.getLineColor());
        assertEquals(group.fillColor(), action.getFillColor());
        assertTrue(action.isFirstAppearance());
    }

    @Test
    void createRemoveShapeActionBuildsTheShapeMarkerIdentifier() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = shapeMarkerGroup("[shape]");

        var action = factory.createRemoveShapeAction("world", group, "label");
        var identifier = (com.tpwalke2.bluemapsignmarkers.core.markers.ShapeMarkerIdentifier) action.getMarkerIdentifier();

        assertEquals("label", identifier.label());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
    }

    @Test
    void shapeActionsForTheSameMapAndGroupReuseTheSameMarkerSetIdentifierAsPOIActions() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = shapeMarkerGroup("[shape]");

        var set = factory.createSetShapeAction("world", group, "label", "detail", List.of(), true);
        var removed = factory.createRemoveShapeAction("world", group, "other label");

        assertSame(set.getMarkerIdentifier().parentSet(), removed.getMarkerIdentifier().parentSet());
    }

    @Test
    void lineActionsForTheSameMapAndGroupReuseTheSameMarkerSetIdentifierAsPOIActions() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = lineMarkerGroup("[line]");

        var set = factory.createSetLineAction("world", group, "label", "detail", List.of(), true);
        var removed = factory.createRemoveLineAction("world", group, "other label");

        assertSame(set.getMarkerIdentifier().parentSet(), removed.getMarkerIdentifier().parentSet());
    }

    @Test
    void createSetExtrudeActionBuildsTheExtrudeMarkerIdentifierAndActionFields() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = extrudeMarkerGroup("[extrude]");
        var points = List.of(new LinePoint(1, 2, 3), new LinePoint(4, 5, 6), new LinePoint(7, 8, 9));

        var action = factory.createSetExtrudeAction("world", group, "label", "detail", points, true);
        var identifier = (com.tpwalke2.bluemapsignmarkers.core.markers.ExtrudeMarkerIdentifier) action.getMarkerIdentifier();

        assertEquals("label", identifier.label());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
        assertEquals("label", action.getLabel());
        assertEquals("detail", action.getDetail());
        assertEquals(points, action.getPoints());
        assertEquals(group.lineWidth(), action.getLineWidth());
        assertEquals(group.lineColor(), action.getLineColor());
        assertEquals(group.fillColor(), action.getFillColor());
        assertTrue(action.isFirstAppearance());
    }

    @Test
    void createRemoveExtrudeActionBuildsTheExtrudeMarkerIdentifier() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = extrudeMarkerGroup("[extrude]");

        var action = factory.createRemoveExtrudeAction("world", group, "label");
        var identifier = (com.tpwalke2.bluemapsignmarkers.core.markers.ExtrudeMarkerIdentifier) action.getMarkerIdentifier();

        assertEquals("label", identifier.label());
        assertEquals("world", identifier.parentSet().mapId());
        assertEquals(group, identifier.parentSet().markerGroup());
    }

    @Test
    void extrudeActionsForTheSameMapAndGroupReuseTheSameMarkerSetIdentifierAsPOIActions() {
        var factory = new ActionFactory(new MarkerSetIdentifierCollection());
        var group = extrudeMarkerGroup("[extrude]");

        var set = factory.createSetExtrudeAction("world", group, "label", "detail", List.of(), true);
        var removed = factory.createRemoveExtrudeAction("world", group, "other label");

        assertSame(set.getMarkerIdentifier().parentSet(), removed.getMarkerIdentifier().parentSet());
    }

    private static MarkerGroup markerGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, prefix, "icon.png", 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of());
    }

    private static MarkerGroup lineMarkerGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.LINE, prefix, "icon.png", 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of());
    }

    private static MarkerGroup shapeMarkerGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.SHAPE, prefix, null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of());
    }

    private static MarkerGroup extrudeMarkerGroup(String prefix) {
        return new MarkerGroup(
                prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.EXTRUDE, prefix, null, 0, 0, false, 0, 0,
                2, "#FF0000FF", "#FF000033", 0, true, true, List.of());
    }
}
