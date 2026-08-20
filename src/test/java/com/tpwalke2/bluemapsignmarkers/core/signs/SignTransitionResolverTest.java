package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.GroupTransitionMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveLineMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveShapeMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.SetLineMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.SetShapeMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Table-driven coverage of SignTransitionResolver.computeTransitionAction, one test per row/sub-branch
// of the transition table in .scratch/line-markers/spec.md §6.
class SignTransitionResolverTest {

    private static final String MAP = "minecraft:overworld";

    private static MarkerGroup poiGroup(String prefix) {
        return new MarkerGroup(prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI,
                "name", null, 0, 0, false, 0.0, 10000000.0, 2, "#FF0000FF", "#FF000033");
    }

    private static MarkerGroup lineGroup(String prefix) {
        return new MarkerGroup(prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.LINE,
                "name", null, 0, 0, false, 0.0, 10000000.0, 2, "#FF0000FF", "#FF000033");
    }

    private static MarkerGroup shapeGroup(String prefix) {
        return new MarkerGroup(prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.SHAPE,
                "name", null, 0, 0, false, 0.0, 10000000.0, 2, "#FF0000FF", "#FF000033");
    }

    private static SignEntry signEntry(int x, int y, int z, String prefix, String label, String detail, long createdAtMillis) {
        return new SignEntry(
                new SignEntryKey(x, y, z, MAP),
                "unknown",
                new SignLinesParseResult(prefix, label, detail),
                new SignLinesParseResult(null, "", ""),
                createdAtMillis,
                null,
                null);
    }

    private static ActionFactory actionFactory() {
        return new ActionFactory(new MarkerSetIdentifierCollection());
    }

    private static SignTransitionResolver.Representation rep(SignEntry entry, MarkerGroup group) {
        return SignTransitionResolver.computeRepresentation(entry, Map.of(group.prefix(), group));
    }

    @Test
    void noneToNoneIsNoOp() {
        var key = new SignEntryKey(0, 64, 0, MAP);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(), key, null, null, actionFactory(), false);

        assertNull(action);
    }

    @Test
    void computeRepresentationOnMalformedEntryWithNullFrontTextReturnsNullInsteadOfThrowing() {
        var group = poiGroup("[poi]");
        var malformed = new SignEntry(new SignEntryKey(0, 64, 0, MAP), "unknown", null, null, 1000L, null, null);

        var rep = SignTransitionResolver.computeRepresentation(malformed, Map.of(group.prefix(), group));

        assertNull(rep);
    }

    @Test
    void noneToPoiDispatchesAdd() {
        var group = poiGroup("[poi]");
        var entry = signEntry(0, 64, 0, "[poi]", "Shop", "detail", 1000L);
        var newRep = rep(entry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(entry), entry.key(), null, newRep, actionFactory(), false);

        var add = assertInstanceOf(AddMarkerAction.class, action);
        assertEquals("Shop", add.getLabel());
        assertEquals("detail", add.getDetail());
    }

    @Test
    void noneToLineWithFewerThanTwoMembersIsNoOp() {
        var group = lineGroup("[trail]");
        var entry = signEntry(0, 64, 0, "[trail]", "Ridge", "detail", 1000L);
        var newRep = rep(entry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(entry), entry.key(), null, newRep, actionFactory(), false);

        assertNull(action);
    }

    @Test
    void noneToLineAtExactlyTwoMembersDispatchesSetWithFirstAppearanceTrue() {
        var group = lineGroup("[trail]");
        var first = signEntry(0, 64, 0, "[trail]", "Ridge", "d1", 1000L);
        var second = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var newRep = rep(second, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(first, second), second.key(), null, newRep, actionFactory(), false);

        var set = assertInstanceOf(SetLineMarkerAction.class, action);
        assertTrue(set.isFirstAppearance());
        assertEquals(2, set.getPoints().size());
        assertEquals("Ridge", set.getDetail());
    }

    @Test
    void noneToLineJoiningAThirdMemberDispatchesSetWithFirstAppearanceFalse() {
        var group = lineGroup("[trail]");
        var first = signEntry(0, 64, 0, "[trail]", "Ridge", "d1", 1000L);
        var second = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var third = signEntry(2, 64, 0, "[trail]", "Ridge", "d3", 3000L);
        var newRep = rep(third, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(first, second, third), third.key(), null, newRep, actionFactory(), false);

        var set = assertInstanceOf(SetLineMarkerAction.class, action);
        assertTrue(!set.isFirstAppearance());
        assertEquals("Ridge", set.getDetail());
        assertEquals(3, set.getPoints().size());
    }

    @Test
    void poiToNoneDispatchesRemove() {
        var group = poiGroup("[poi]");
        var entry = signEntry(0, 64, 0, "[poi]", "Shop", "detail", 1000L);
        var oldRep = rep(entry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(), entry.key(), oldRep, null, actionFactory(), false);

        assertInstanceOf(RemoveMarkerAction.class, action);
    }

    @Test
    void poiToPoiSameGroupAndLabelTextUnchangedIsNoOp() {
        var group = poiGroup("[poi]");
        var entry = signEntry(0, 64, 0, "[poi]", "Shop", "detail", 1000L);
        var oldRep = rep(entry, group);
        var newRep = rep(entry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(entry), entry.key(), oldRep, newRep, actionFactory(), false);

        assertNull(action);
    }

    @Test
    void poiToPoiSameGroupAndLabelTextUnchangedOnReloadDispatchesAdd() {
        var group = poiGroup("[poi]");
        var entry = signEntry(0, 64, 0, "[poi]", "Shop", "detail", 1000L);
        var oldRep = rep(entry, group);
        var newRep = rep(entry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(entry), entry.key(), oldRep, newRep, actionFactory(), true);

        var add = assertInstanceOf(AddMarkerAction.class, action);
        assertEquals("Shop", add.getLabel());
        assertEquals("detail", add.getDetail());
    }

    @Test
    void poiToPoiSameGroupAndLabelTextChangedDispatchesUpdate() {
        var group = poiGroup("[poi]");
        var oldEntry = signEntry(0, 64, 0, "[poi]", "Shop", "old detail", 1000L);
        var newEntry = signEntry(0, 64, 0, "[poi]", "Shop", "new detail", 1000L);
        var oldRep = rep(oldEntry, group);
        var newRep = rep(newEntry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(newEntry), newEntry.key(), oldRep, newRep, actionFactory(), false);

        var update = assertInstanceOf(UpdateMarkerAction.class, action);
        assertEquals("new detail", update.getNewDetails());
    }

    @Test
    void poiToPoiDifferentGroupDispatchesGroupTransition() {
        var oldGroup = poiGroup("[poiA]");
        var newGroup = poiGroup("[poiB]");
        var oldEntry = signEntry(0, 64, 0, "[poiA]", "Shop", "detail", 1000L);
        var entry = signEntry(0, 64, 0, "[poiB]", "Shop", "detail", 1000L);
        var oldRep = rep(oldEntry, oldGroup);
        var newRep = rep(entry, newGroup);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(entry), entry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveMarkerAction.class, transition.effects().get(0));
        assertInstanceOf(AddMarkerAction.class, transition.effects().get(1));
    }

    @Test
    void lineToNoneDroppingToOneRemainingMemberDispatchesRemoveLine() {
        var group = lineGroup("[trail]");
        var departing = signEntry(0, 64, 0, "[trail]", "Ridge", "d1", 1000L);
        var remaining = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var oldRep = rep(departing, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining), departing.key(), oldRep, null, actionFactory(), false);

        assertInstanceOf(RemoveLineMarkerAction.class, action);
    }

    @Test
    void lineToNoneDroppingToZeroRemainingMembersIsNoOp() {
        var group = lineGroup("[trail]");
        var departing = signEntry(0, 64, 0, "[trail]", "Ridge", "d1", 1000L);
        var oldRep = rep(departing, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(), departing.key(), oldRep, null, actionFactory(), false);

        assertNull(action);
    }

    @Test
    void lineToNoneWithTwoOrMoreRemainingDispatchesRefreshedSet() {
        var group = lineGroup("[trail]");
        var departing = signEntry(0, 64, 0, "[trail]", "Ridge", "d1", 1000L);
        var remaining1 = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var remaining2 = signEntry(2, 64, 0, "[trail]", "Ridge", "d3", 3000L);
        var oldRep = rep(departing, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining1, remaining2), departing.key(), oldRep, null, actionFactory(), false);

        var set = assertInstanceOf(SetLineMarkerAction.class, action);
        assertEquals(2, set.getPoints().size());
        assertEquals("Ridge", set.getDetail());
    }

    @Test
    void lineToLineSameGroupAndLabelDetailUnchangedIsNoOp() {
        var group = lineGroup("[trail]");
        var self = signEntry(0, 64, 0, "[trail]", "Ridge", "detail", 1000L);
        var other = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var oldRep = rep(self, group);
        var newRep = rep(self, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(self, other), self.key(), oldRep, newRep, actionFactory(), false);

        assertNull(action);
    }

    @Test
    void lineToLineSameGroupAndLabelDetailChangedDispatchesSetWithFirstAppearanceFalse() {
        var group = lineGroup("[trail]");
        var oldEntry = signEntry(0, 64, 0, "[trail]", "Ridge", "old detail", 1000L);
        var newEntry = signEntry(0, 64, 0, "[trail]", "Ridge", "new detail", 1000L);
        var other = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var oldRep = rep(oldEntry, group);
        var newRep = rep(newEntry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(newEntry, other), newEntry.key(), oldRep, newRep, actionFactory(), false);

        var set = assertInstanceOf(SetLineMarkerAction.class, action);
        assertTrue(!set.isFirstAppearance());
    }

    @Test
    void lineToLineSameGroupAndLabelDetailUnchangedOnReloadDispatchesSet() {
        var group = lineGroup("[trail]");
        var self = signEntry(0, 64, 0, "[trail]", "Ridge", "detail", 1000L);
        var other = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var oldRep = rep(self, group);
        var newRep = rep(self, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(self, other), self.key(), oldRep, newRep, actionFactory(), true);

        var set = assertInstanceOf(SetLineMarkerAction.class, action);
        assertEquals(2, set.getPoints().size());
        assertEquals("Ridge", set.getDetail());
    }

    @Test
    void lineToLineDifferentGroupBundlesLeaveAndJoin() {
        var oldGroup = lineGroup("[trailA]");
        var newGroup = lineGroup("[trailB]");
        var otherOldGroupMember = signEntry(9, 64, 9, "[trailA]", "OldLabel", "d", 500L);
        var movedEntry = signEntry(0, 64, 0, "[trailB]", "NewLabel", "d", 1000L);
        var otherNewGroupMember = signEntry(1, 64, 0, "[trailB]", "NewLabel", "d2", 2000L);
        var oldRep = rep(signEntry(0, 64, 0, "[trailA]", "OldLabel", "d", 1000L), oldGroup);
        var newRep = rep(movedEntry, newGroup);

        var allSigns = List.of(otherOldGroupMember, movedEntry, otherNewGroupMember);
        var action = SignTransitionResolver.computeTransitionAction(() -> allSigns, movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveLineMarkerAction.class, transition.effects().get(0));
        var join = assertInstanceOf(SetLineMarkerAction.class, transition.effects().get(1));
        assertEquals(2, join.getPoints().size());
    }

    @Test
    void poiToLineBundlesRemovePoiAndSetLine() {
        var poi = poiGroup("[poi]");
        var line = lineGroup("[trail]");
        var movedEntry = signEntry(0, 64, 0, "[trail]", "Ridge", "d", 1000L);
        var otherLineMember = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var oldRep = rep(signEntry(0, 64, 0, "[poi]", "Shop", "d", 1000L), poi);
        var newRep = rep(movedEntry, line);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(movedEntry, otherLineMember), movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveMarkerAction.class, transition.effects().get(0));
        assertInstanceOf(SetLineMarkerAction.class, transition.effects().get(1));
    }

    @Test
    void lineToPoiBundlesSetLineAndAddPoi() {
        var line = lineGroup("[trail]");
        var poi = poiGroup("[poi]");
        var departing = signEntry(0, 64, 0, "[trail]", "Ridge", "d", 1000L);
        var remaining1 = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var remaining2 = signEntry(2, 64, 0, "[trail]", "Ridge", "d3", 3000L);
        var movedEntry = signEntry(0, 64, 0, "[poi]", "Shop", "d", 1000L);
        var oldRep = rep(departing, line);
        var newRep = rep(movedEntry, poi);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining1, remaining2, movedEntry), movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        var leave = assertInstanceOf(SetLineMarkerAction.class, transition.effects().get(0));
        assertEquals(2, leave.getPoints().size());
        assertInstanceOf(AddMarkerAction.class, transition.effects().get(1));
    }

    // --- SHAPE: join/leave/recompute at the 3-member threshold (mirrors the LINE table above, but at 3) ---

    @Test
    void noneToShapeWithFewerThanThreeMembersIsNoOp() {
        var group = shapeGroup("[region]");
        var first = signEntry(0, 64, 0, "[region]", "Plot", "d1", 1000L);
        var second = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var newRep = rep(second, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(first, second), second.key(), null, newRep, actionFactory(), false);

        assertNull(action);
    }

    @Test
    void noneToShapeAtExactlyThreeMembersDispatchesSetWithFirstAppearanceTrue() {
        var group = shapeGroup("[region]");
        var first = signEntry(0, 64, 0, "[region]", "Plot", "d1", 1000L);
        var second = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var third = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var newRep = rep(third, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(first, second, third), third.key(), null, newRep, actionFactory(), false);

        var set = assertInstanceOf(SetShapeMarkerAction.class, action);
        assertTrue(set.isFirstAppearance());
        assertEquals(3, set.getPoints().size());
        assertEquals("Plot", set.getDetail());
    }

    @Test
    void noneToShapeJoiningAFourthMemberDispatchesSetWithFirstAppearanceFalse() {
        var group = shapeGroup("[region]");
        var first = signEntry(0, 64, 0, "[region]", "Plot", "d1", 1000L);
        var second = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var third = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var fourth = signEntry(3, 64, 0, "[region]", "Plot", "d4", 4000L);
        var newRep = rep(fourth, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(first, second, third, fourth), fourth.key(), null, newRep, actionFactory(), false);

        var set = assertInstanceOf(SetShapeMarkerAction.class, action);
        assertTrue(!set.isFirstAppearance());
        assertEquals(4, set.getPoints().size());
    }

    @Test
    void shapeToNoneDroppingToTwoRemainingMembersDispatchesRemoveShape() {
        var group = shapeGroup("[region]");
        var departing = signEntry(0, 64, 0, "[region]", "Plot", "d1", 1000L);
        var remaining1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var remaining2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(departing, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining1, remaining2), departing.key(), oldRep, null, actionFactory(), false);

        assertInstanceOf(RemoveShapeMarkerAction.class, action);
    }

    @Test
    void shapeToNoneDroppingToOneOrZeroRemainingMembersIsNoOp() {
        var group = shapeGroup("[region]");
        var departing = signEntry(0, 64, 0, "[region]", "Plot", "d1", 1000L);
        var remaining = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var oldRep = rep(departing, group);

        var actionWithOneRemaining = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining), departing.key(), oldRep, null, actionFactory(), false);
        var actionWithNoneRemaining = SignTransitionResolver.computeTransitionAction(() -> List.of(), departing.key(), oldRep, null, actionFactory(), false);

        assertNull(actionWithOneRemaining);
        assertNull(actionWithNoneRemaining);
    }

    @Test
    void shapeToNoneWithThreeOrMoreRemainingDispatchesRefreshedSet() {
        var group = shapeGroup("[region]");
        var departing = signEntry(0, 64, 0, "[region]", "Plot", "d1", 1000L);
        var remaining1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var remaining2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var remaining3 = signEntry(3, 64, 0, "[region]", "Plot", "d4", 4000L);
        var oldRep = rep(departing, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining1, remaining2, remaining3), departing.key(), oldRep, null, actionFactory(), false);

        var set = assertInstanceOf(SetShapeMarkerAction.class, action);
        assertEquals(3, set.getPoints().size());
        assertEquals("Plot", set.getDetail());
    }

    @Test
    void shapeToShapeSameGroupAndLabelDetailUnchangedIsNoOp() {
        var group = shapeGroup("[region]");
        var self = signEntry(0, 64, 0, "[region]", "Plot", "detail", 1000L);
        var other1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var other2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(self, group);
        var newRep = rep(self, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(self, other1, other2), self.key(), oldRep, newRep, actionFactory(), false);

        assertNull(action);
    }

    @Test
    void shapeToShapeSameGroupAndLabelDetailChangedDispatchesSetWithFirstAppearanceFalse() {
        var group = shapeGroup("[region]");
        var oldEntry = signEntry(0, 64, 0, "[region]", "Plot", "old detail", 1000L);
        var newEntry = signEntry(0, 64, 0, "[region]", "Plot", "new detail", 1000L);
        var other1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var other2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(oldEntry, group);
        var newRep = rep(newEntry, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(newEntry, other1, other2), newEntry.key(), oldRep, newRep, actionFactory(), false);

        var set = assertInstanceOf(SetShapeMarkerAction.class, action);
        assertTrue(!set.isFirstAppearance());
    }

    @Test
    void shapeToShapeSameGroupAndLabelDetailUnchangedOnReloadDispatchesSet() {
        var group = shapeGroup("[region]");
        var self = signEntry(0, 64, 0, "[region]", "Plot", "detail", 1000L);
        var other1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var other2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(self, group);
        var newRep = rep(self, group);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(self, other1, other2), self.key(), oldRep, newRep, actionFactory(), true);

        var set = assertInstanceOf(SetShapeMarkerAction.class, action);
        assertEquals(3, set.getPoints().size());
        assertEquals("Plot", set.getDetail());
    }

    @Test
    void shapeToShapeDifferentGroupBundlesLeaveAndJoin() {
        var oldGroup = shapeGroup("[regionA]");
        var newGroup = shapeGroup("[regionB]");
        var otherOldGroupMember1 = signEntry(8, 64, 8, "[regionA]", "OldLabel", "d", 400L);
        var otherOldGroupMember2 = signEntry(9, 64, 9, "[regionA]", "OldLabel", "d", 500L);
        var movedEntry = signEntry(0, 64, 0, "[regionB]", "NewLabel", "d", 1000L);
        var otherNewGroupMember1 = signEntry(1, 64, 0, "[regionB]", "NewLabel", "d2", 2000L);
        var otherNewGroupMember2 = signEntry(2, 64, 0, "[regionB]", "NewLabel", "d3", 3000L);
        var oldRep = rep(signEntry(0, 64, 0, "[regionA]", "OldLabel", "d", 1000L), oldGroup);
        var newRep = rep(movedEntry, newGroup);

        var allSigns = List.of(otherOldGroupMember1, otherOldGroupMember2, movedEntry, otherNewGroupMember1, otherNewGroupMember2);
        var action = SignTransitionResolver.computeTransitionAction(() -> allSigns, movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveShapeMarkerAction.class, transition.effects().get(0));
        var join = assertInstanceOf(SetShapeMarkerAction.class, transition.effects().get(1));
        assertEquals(3, join.getPoints().size());
    }

    // --- POI<->SHAPE and LINE<->SHAPE type flips (both directions) ---

    @Test
    void poiToShapeBundlesRemovePoiAndSetShape() {
        var poi = poiGroup("[poi]");
        var shape = shapeGroup("[region]");
        var movedEntry = signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L);
        var otherMember1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var otherMember2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(signEntry(0, 64, 0, "[poi]", "Shop", "d", 1000L), poi);
        var newRep = rep(movedEntry, shape);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(movedEntry, otherMember1, otherMember2), movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveMarkerAction.class, transition.effects().get(0));
        var join = assertInstanceOf(SetShapeMarkerAction.class, transition.effects().get(1));
        assertTrue(join.isFirstAppearance());
    }

    @Test
    void shapeToPoiBundlesSetShapeAndAddPoi() {
        var shape = shapeGroup("[region]");
        var poi = poiGroup("[poi]");
        var departing = signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L);
        var remaining1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var remaining2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var remaining3 = signEntry(3, 64, 0, "[region]", "Plot", "d4", 4000L);
        var movedEntry = signEntry(0, 64, 0, "[poi]", "Shop", "d", 1000L);
        var oldRep = rep(departing, shape);
        var newRep = rep(movedEntry, poi);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining1, remaining2, remaining3, movedEntry), movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        var leave = assertInstanceOf(SetShapeMarkerAction.class, transition.effects().get(0));
        assertEquals(3, leave.getPoints().size());
        assertInstanceOf(AddMarkerAction.class, transition.effects().get(1));
    }

    @Test
    void lineToShapeBundlesRemoveLineAndSetShape() {
        var line = lineGroup("[trail]");
        var shape = shapeGroup("[region]");
        var departingLineMember = signEntry(9, 64, 9, "[trail]", "Ridge", "d", 500L);
        var movedEntry = signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L);
        var otherMember1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var otherMember2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(signEntry(0, 64, 0, "[trail]", "Ridge", "d", 1000L), line);
        var newRep = rep(movedEntry, shape);

        var allSigns = List.of(departingLineMember, movedEntry, otherMember1, otherMember2);
        var action = SignTransitionResolver.computeTransitionAction(() -> allSigns, movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveLineMarkerAction.class, transition.effects().get(0));
        var join = assertInstanceOf(SetShapeMarkerAction.class, transition.effects().get(1));
        assertTrue(join.isFirstAppearance());
    }

    @Test
    void shapeToLineBundlesRemoveShapeAndSetLine() {
        var shape = shapeGroup("[region]");
        var line = lineGroup("[trail]");
        var departingShapeMember1 = signEntry(8, 64, 8, "[region]", "Plot", "d", 400L);
        var departingShapeMember2 = signEntry(9, 64, 9, "[region]", "Plot", "d", 500L);
        var movedEntry = signEntry(0, 64, 0, "[trail]", "Ridge", "d", 1000L);
        var otherLineMember = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var oldRep = rep(signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L), shape);
        var newRep = rep(movedEntry, line);

        var allSigns = List.of(departingShapeMember1, departingShapeMember2, movedEntry, otherLineMember);
        var action = SignTransitionResolver.computeTransitionAction(() -> allSigns, movedEntry.key(), oldRep, newRep, actionFactory(), false);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveShapeMarkerAction.class, transition.effects().get(0));
        assertInstanceOf(SetLineMarkerAction.class, transition.effects().get(1));
    }

    // --- isReload variants for POI<->SHAPE and LINE<->SHAPE flips, mirroring the existing POI/LINE reload coverage ---

    @Test
    void poiToShapeOnReloadStillBundlesRemovePoiAndSetShape() {
        var poi = poiGroup("[poi]");
        var shape = shapeGroup("[region]");
        var movedEntry = signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L);
        var otherMember1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var otherMember2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(signEntry(0, 64, 0, "[poi]", "Shop", "d", 1000L), poi);
        var newRep = rep(movedEntry, shape);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(movedEntry, otherMember1, otherMember2), movedEntry.key(), oldRep, newRep, actionFactory(), true);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveMarkerAction.class, transition.effects().get(0));
        var join = assertInstanceOf(SetShapeMarkerAction.class, transition.effects().get(1));
        assertTrue(join.isFirstAppearance());
    }

    @Test
    void shapeToPoiOnReloadStillBundlesSetShapeAndAddPoi() {
        var shape = shapeGroup("[region]");
        var poi = poiGroup("[poi]");
        var departing = signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L);
        var remaining1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var remaining2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var remaining3 = signEntry(3, 64, 0, "[region]", "Plot", "d4", 4000L);
        var movedEntry = signEntry(0, 64, 0, "[poi]", "Shop", "d", 1000L);
        var oldRep = rep(departing, shape);
        var newRep = rep(movedEntry, poi);

        var action = SignTransitionResolver.computeTransitionAction(() -> List.of(remaining1, remaining2, remaining3, movedEntry), movedEntry.key(), oldRep, newRep, actionFactory(), true);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        var leave = assertInstanceOf(SetShapeMarkerAction.class, transition.effects().get(0));
        assertEquals(3, leave.getPoints().size());
        assertInstanceOf(AddMarkerAction.class, transition.effects().get(1));
    }

    @Test
    void lineToShapeOnReloadStillBundlesRemoveLineAndSetShape() {
        var line = lineGroup("[trail]");
        var shape = shapeGroup("[region]");
        var departingLineMember = signEntry(9, 64, 9, "[trail]", "Ridge", "d", 500L);
        var movedEntry = signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L);
        var otherMember1 = signEntry(1, 64, 0, "[region]", "Plot", "d2", 2000L);
        var otherMember2 = signEntry(2, 64, 0, "[region]", "Plot", "d3", 3000L);
        var oldRep = rep(signEntry(0, 64, 0, "[trail]", "Ridge", "d", 1000L), line);
        var newRep = rep(movedEntry, shape);

        var allSigns = List.of(departingLineMember, movedEntry, otherMember1, otherMember2);
        var action = SignTransitionResolver.computeTransitionAction(() -> allSigns, movedEntry.key(), oldRep, newRep, actionFactory(), true);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveLineMarkerAction.class, transition.effects().get(0));
        var join = assertInstanceOf(SetShapeMarkerAction.class, transition.effects().get(1));
        assertTrue(join.isFirstAppearance());
    }

    @Test
    void shapeToLineOnReloadStillBundlesRemoveShapeAndSetLine() {
        var shape = shapeGroup("[region]");
        var line = lineGroup("[trail]");
        var departingShapeMember1 = signEntry(8, 64, 8, "[region]", "Plot", "d", 400L);
        var departingShapeMember2 = signEntry(9, 64, 9, "[region]", "Plot", "d", 500L);
        var movedEntry = signEntry(0, 64, 0, "[trail]", "Ridge", "d", 1000L);
        var otherLineMember = signEntry(1, 64, 0, "[trail]", "Ridge", "d2", 2000L);
        var oldRep = rep(signEntry(0, 64, 0, "[region]", "Plot", "d", 1000L), shape);
        var newRep = rep(movedEntry, line);

        var allSigns = List.of(departingShapeMember1, departingShapeMember2, movedEntry, otherLineMember);
        var action = SignTransitionResolver.computeTransitionAction(() -> allSigns, movedEntry.key(), oldRep, newRep, actionFactory(), true);

        var transition = assertInstanceOf(GroupTransitionMarkerAction.class, action);
        assertEquals(2, transition.effects().size());
        assertInstanceOf(RemoveShapeMarkerAction.class, transition.effects().get(0));
        assertInstanceOf(SetLineMarkerAction.class, transition.effects().get(1));
    }
}
