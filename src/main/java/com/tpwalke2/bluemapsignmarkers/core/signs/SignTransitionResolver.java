package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.GroupTransitionMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SignTransitionResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private SignTransitionResolver() {
    }

    // A sign's marker representation under the current config: null means the sign matches no marker
    // group (NONE); otherwise group.type() says whether it's a POI or a LINE member.
    record Representation(MarkerGroup group, String label, String detail) {
    }

    static Representation computeRepresentation(SignEntry entry, Map<String, MarkerGroup> prefixGroupMap) {
        if (entry == null) return null;

        String prefix;
        String label;
        String detail;
        try {
            prefix = SignEntryHelper.getPrefix(entry);
            if (prefix == null) return null;
            label = SignEntryHelper.getLabel(entry);
            detail = SignEntryHelper.getDetail(entry);
        } catch (Exception e) {
            LOGGER.error("Failed to compute representation for malformed sign entry {}; skipping.", entry, e);
            return null;
        }

        var group = prefixGroupMap.get(prefix);
        if (group == null) {
            LOGGER.warn("No marker group configured for prefix {}, skipping: {}", prefix, entry);
            return null;
        }

        return new Representation(group, label, detail);
    }

    static boolean sameGroupAndLabel(Representation a, Representation b) {
        return a.group().equals(b.group()) && a.label().equals(b.label());
    }

    // The (oldRepresentation, newRepresentation) transition table from
    // .scratch/line-markers/spec.md §6. Returns null for a no-op, a single MarkerAction when only one
    // effect applies, or a GroupTransitionMarkerAction bundling a leave-effect + join-effect so
    // ReactiveQueue's lack of ordering guarantees can't transiently show a sign in two places.
    //
    // allSignsSupplier is lazy (only POI<->POI transitions never touch it) since callers may hold the
    // full sign cache, which is expensive to copy on every single-sign event when this is a pure POI
    // transition (the common case).
    static MarkerAction computeTransitionAction(
            Supplier<List<SignEntry>> allSignsSupplier,
            SignEntryKey key,
            Representation oldRep,
            Representation newRep,
            ActionFactory actionFactory,
            boolean isReload,
            Map<String, MarkerGroup> currentPrefixGroupMap) {
        if (oldRep == null && newRep == null) return null;

        if (oldRep == null) {
            return joinEffect(allSignsSupplier, key, newRep, actionFactory, false);
        }

        if (newRep == null) {
            return leaveEffect(allSignsSupplier, key, oldRep, actionFactory, currentPrefixGroupMap);
        }

        var oldType = oldRep.group().type();
        var newType = newRep.group().type();

        if (oldType == MarkerGroupType.POI && newType == MarkerGroupType.POI) {
            if (oldRep.group().prefix().equals(newRep.group().prefix())) {
                var unchanged = oldRep.label().equals(newRep.label()) && oldRep.detail().equals(newRep.detail());
                if (unchanged) {
                    return isReload
                            ? actionFactory.createAddPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group())
                            : null;
                }
                return actionFactory.createUpdatePOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group());
            }
            return actionFactory.createChangeGroupPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), oldRep.group(), newRep.group());
        }

        // Same-group-and-label recompute for either multi-point type (LINE↔LINE or SHAPE↔SHAPE); sameGroupAndLabel
        // requires the whole MarkerGroup to be unchanged (not just its type/prefix), since any other edit - e.g. a
        // rename - moves the marker to a different BlueMap marker set (see groupIdentityObsolete) and must go
        // through the general leave+join bundling below instead, or the old marker set would never get cleared.
        if (oldType == newType && oldType != MarkerGroupType.POI && sameGroupAndLabel(oldRep, newRep)) {
            if (oldRep.detail().equals(newRep.detail()) && !isReload) return null;
            return joinEffect(allSignsSupplier, key, newRep, actionFactory, true);
        }

        var effects = new ArrayList<MarkerAction>(2);

        var leave = leaveEffect(allSignsSupplier, key, oldRep, actionFactory, currentPrefixGroupMap);
        if (leave != null) effects.add(leave);

        var join = joinEffect(allSignsSupplier, key, newRep, actionFactory, false);
        if (join != null) effects.add(join);

        if (effects.isEmpty()) return null;
        if (effects.size() == 1) return effects.get(0);
        return new GroupTransitionMarkerAction(effects);
    }

    // Dispatches the "this sign no longer holds this representation" effect for whichever type the
    // representation belongs to - a plain POI remove, or a LINE/SHAPE recompute-or-remove.
    private static MarkerAction leaveEffect(Supplier<List<SignEntry>> allSignsSupplier, SignEntryKey key, Representation rep, ActionFactory actionFactory, Map<String, MarkerGroup> currentPrefixGroupMap) {
        return switch (rep.group().type()) {
            case POI -> actionFactory.createRemovePOIAction(key.x(), key.y(), key.z(), key.parentMap(), rep.group());
            case LINE -> lineLeaveAction(allSignsSupplier, key.parentMap(), rep, actionFactory, currentPrefixGroupMap);
            case SHAPE -> shapeLeaveAction(allSignsSupplier, key.parentMap(), rep, actionFactory, currentPrefixGroupMap);
            case EXTRUDE -> extrudeLeaveAction(allSignsSupplier, key.parentMap(), rep, actionFactory, currentPrefixGroupMap);
        };
    }

    // True once rep's prefix no longer resolves to the exact same group under the current config - i.e. the
    // group was deleted, reassigned to a different prefix, had its type flipped (e.g. SHAPE -> POI), or was
    // otherwise edited (e.g. renamed, which moves its markers to a different BlueMap marker set - see
    // BlueMapAPIConnector.getMarkerSets keying MarkerSet lookup by MarkerGroup.name()). When true, every
    // sign still sharing rep's old prefix/label is undergoing this same transition simultaneously, so
    // LineGroupResolver/ShapeGroupResolver's raw prefix/label membership count (which knows nothing about
    // group identity) can't be trusted to decide recompute-vs-remove: it would see the other members still
    // "present" and recompute the old multi-point marker instead of retiring it. See
    // agent-context/reviews/review-2026-08-20.md.
    private static boolean groupIdentityObsolete(Representation rep, Map<String, MarkerGroup> currentPrefixGroupMap) {
        var currentGroup = currentPrefixGroupMap.get(rep.group().prefix());
        return currentGroup == null || !currentGroup.equals(rep.group());
    }

    // Dispatches the "this sign now holds this representation" effect for whichever type the
    // representation belongs to - a plain POI add, or a LINE/SHAPE recompute-or-first-appearance.
    // sameGroupRecompute is only meaningful for LINE/SHAPE (see lineJoinAction/shapeJoinAction).
    private static MarkerAction joinEffect(Supplier<List<SignEntry>> allSignsSupplier, SignEntryKey key, Representation rep, ActionFactory actionFactory, boolean sameGroupRecompute) {
        return switch (rep.group().type()) {
            case POI -> actionFactory.createAddPOIAction(key.x(), key.y(), key.z(), key.parentMap(), rep.label(), rep.detail(), rep.group());
            case LINE -> lineJoinAction(allSignsSupplier, key.parentMap(), rep, actionFactory, sameGroupRecompute);
            case SHAPE -> shapeJoinAction(allSignsSupplier, key.parentMap(), rep, actionFactory, sameGroupRecompute);
            case EXTRUDE -> extrudeJoinAction(allSignsSupplier, key.parentMap(), rep, actionFactory, sameGroupRecompute);
        };
    }

    // Recomputes a line group including the current sign (it must already be in signCache under this
    // group/label by the time this is called). Dispatches Set once ≥2 members exist; below that the line
    // is still incomplete and nothing is dispatched. sameGroupRecompute forces isFirstAppearance=false,
    // since it's used both for same-group/label recomputes (only reachable at ≥2 members if a marker
    // already existed) and for reload-forced recreates (isFirstAppearance is log-only there, so the
    // false value is harmless either way).
    private static MarkerAction lineJoinAction(Supplier<List<SignEntry>> allSignsSupplier, String parentMap, Representation rep, ActionFactory actionFactory, boolean sameGroupRecompute) {
        var members = LineGroupResolver.members(allSignsSupplier.get(), parentMap, rep.group().prefix(), rep.label());
        if (members.size() < 2) return null;

        var isFirstAppearance = !sameGroupRecompute && members.size() == 2;
        return actionFactory.createSetLineAction(parentMap, rep.group(), rep.label(), rep.detail(), toPoints(members), isFirstAppearance);
    }

    // Recomputes a line group excluding the current sign (it must already be removed from/no longer
    // present in signCache under this group/label by the time this is called). Dispatches Set if ≥2
    // members remain, Remove if it drops below 2 and a marker existed before (i.e. exactly 1 remains -
    // meaning there were 2 before), or nothing if there was never a marker to begin with (0 remain).
    private static MarkerAction lineLeaveAction(Supplier<List<SignEntry>> allSignsSupplier, String parentMap, Representation rep, ActionFactory actionFactory, Map<String, MarkerGroup> currentPrefixGroupMap) {
        if (groupIdentityObsolete(rep, currentPrefixGroupMap)) {
            return actionFactory.createRemoveLineAction(parentMap, rep.group(), rep.label());
        }

        var members = LineGroupResolver.members(allSignsSupplier.get(), parentMap, rep.group().prefix(), rep.label());

        if (members.size() >= 2) {
            return actionFactory.createSetLineAction(parentMap, rep.group(), rep.label(), rep.detail(), toPoints(members), false);
        }

        if (members.size() == 1) {
            return actionFactory.createRemoveLineAction(parentMap, rep.group(), rep.label());
        }

        return null;
    }

    private static List<LinePoint> toPoints(List<SignEntry> members) {
        return members.stream().map(e -> new LinePoint(e.key().x(), e.key().y(), e.key().z())).toList();
    }

    // SHAPE mirrors LINE's join/leave/recompute shape (see lineJoinAction/lineLeaveAction above), but at a
    // 3-member render threshold instead of 2 - see docs/adr/0002-shape-duplicates-line-pattern.md. Points
    // stay ordered by createdAtMillis (toPoints/ShapeGroupResolver.members) purely for polygon vertex order;
    // the shape's Y anchor (BlueMapAPIConnector.setShapeMarker) is the tallest member, not the oldest.
    private static final int SHAPE_MIN_MEMBERS = 3;

    private static MarkerAction shapeJoinAction(Supplier<List<SignEntry>> allSignsSupplier, String parentMap, Representation rep, ActionFactory actionFactory, boolean sameGroupRecompute) {
        var members = ShapeGroupResolver.members(allSignsSupplier.get(), parentMap, rep.group().prefix(), rep.label());
        if (members.size() < SHAPE_MIN_MEMBERS) return null;

        var isFirstAppearance = !sameGroupRecompute && members.size() == SHAPE_MIN_MEMBERS;
        return actionFactory.createSetShapeAction(parentMap, rep.group(), rep.label(), rep.detail(), toPoints(members), isFirstAppearance);
    }

    private static MarkerAction shapeLeaveAction(Supplier<List<SignEntry>> allSignsSupplier, String parentMap, Representation rep, ActionFactory actionFactory, Map<String, MarkerGroup> currentPrefixGroupMap) {
        if (groupIdentityObsolete(rep, currentPrefixGroupMap)) {
            return actionFactory.createRemoveShapeAction(parentMap, rep.group(), rep.label());
        }

        var members = ShapeGroupResolver.members(allSignsSupplier.get(), parentMap, rep.group().prefix(), rep.label());

        if (members.size() >= SHAPE_MIN_MEMBERS) {
            return actionFactory.createSetShapeAction(parentMap, rep.group(), rep.label(), rep.detail(), toPoints(members), false);
        }

        if (members.size() == SHAPE_MIN_MEMBERS - 1) {
            return actionFactory.createRemoveShapeAction(parentMap, rep.group(), rep.label());
        }

        return null;
    }

    // EXTRUDE mirrors SHAPE's join/leave/recompute shape (see shapeJoinAction/shapeLeaveAction above) at the
    // same 3-member render threshold - the floor/ceiling Y values are computed from the ordered points at
    // dispatch time (BlueMapAPIConnector.setExtrudeMarker), not here.
    private static final int EXTRUDE_MIN_MEMBERS = 3;

    private static MarkerAction extrudeJoinAction(Supplier<List<SignEntry>> allSignsSupplier, String parentMap, Representation rep, ActionFactory actionFactory, boolean sameGroupRecompute) {
        var members = ExtrudeGroupResolver.members(allSignsSupplier.get(), parentMap, rep.group().prefix(), rep.label());
        if (members.size() < EXTRUDE_MIN_MEMBERS) return null;

        var isFirstAppearance = !sameGroupRecompute && members.size() == EXTRUDE_MIN_MEMBERS;
        return actionFactory.createSetExtrudeAction(parentMap, rep.group(), rep.label(), rep.detail(), toPoints(members), isFirstAppearance);
    }

    private static MarkerAction extrudeLeaveAction(Supplier<List<SignEntry>> allSignsSupplier, String parentMap, Representation rep, ActionFactory actionFactory, Map<String, MarkerGroup> currentPrefixGroupMap) {
        if (groupIdentityObsolete(rep, currentPrefixGroupMap)) {
            return actionFactory.createRemoveExtrudeAction(parentMap, rep.group(), rep.label());
        }

        var members = ExtrudeGroupResolver.members(allSignsSupplier.get(), parentMap, rep.group().prefix(), rep.label());

        if (members.size() >= EXTRUDE_MIN_MEMBERS) {
            return actionFactory.createSetExtrudeAction(parentMap, rep.group(), rep.label(), rep.detail(), toPoints(members), false);
        }

        if (members.size() == EXTRUDE_MIN_MEMBERS - 1) {
            return actionFactory.createRemoveExtrudeAction(parentMap, rep.group(), rep.label());
        }

        return null;
    }
}
