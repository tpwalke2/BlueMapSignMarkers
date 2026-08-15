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
import java.util.stream.Collectors;

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

        var prefix = SignEntryHelper.getPrefix(entry);
        if (prefix == null) return null;

        var group = prefixGroupMap.get(prefix);
        if (group == null) {
            LOGGER.warn("No marker group configured for prefix {}, skipping: {}", prefix, entry);
            return null;
        }

        return new Representation(group, SignEntryHelper.getLabel(entry), SignEntryHelper.getDetail(entry));
    }

    static boolean sameGroupAndLabel(Representation a, Representation b) {
        return a.group().prefix().equals(b.group().prefix()) && a.label().equals(b.label());
    }

    // The (oldRepresentation, newRepresentation) transition table from
    // .scratch/line-markers/spec.md §6. Returns null for a no-op, a single MarkerAction when only one
    // effect applies, or a GroupTransitionMarkerAction bundling a leave-effect + join-effect so
    // ReactiveQueue's lack of ordering guarantees can't transiently show a sign in two places.
    static MarkerAction computeTransitionAction(
            List<SignEntry> allSigns,
            SignEntryKey key,
            Representation oldRep,
            Representation newRep,
            ActionFactory actionFactory) {
        if (oldRep == null && newRep == null) return null;

        if (oldRep == null) {
            return newRep.group().type() == MarkerGroupType.POI
                    ? actionFactory.createAddPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group())
                    : lineJoinAction(allSigns, key.parentMap(), newRep, actionFactory, false);
        }

        if (newRep == null) {
            return oldRep.group().type() == MarkerGroupType.POI
                    ? actionFactory.createRemovePOIAction(key.x(), key.y(), key.z(), key.parentMap(), oldRep.group())
                    : lineLeaveAction(allSigns, key.parentMap(), oldRep, actionFactory);
        }

        var oldType = oldRep.group().type();
        var newType = newRep.group().type();

        if (oldType == MarkerGroupType.POI && newType == MarkerGroupType.POI) {
            if (oldRep.group().prefix().equals(newRep.group().prefix())) {
                var unchanged = oldRep.label().equals(newRep.label()) && oldRep.detail().equals(newRep.detail());
                return unchanged
                        ? null
                        : actionFactory.createUpdatePOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group());
            }
            return actionFactory.createChangeGroupPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), oldRep.group(), newRep.group());
        }

        if (oldType == MarkerGroupType.LINE && newType == MarkerGroupType.LINE && sameGroupAndLabel(oldRep, newRep)) {
            return oldRep.detail().equals(newRep.detail())
                    ? null
                    : lineJoinAction(allSigns, key.parentMap(), newRep, actionFactory, true);
        }

        var effects = new ArrayList<MarkerAction>(2);

        var leave = oldType == MarkerGroupType.POI
                ? actionFactory.createRemovePOIAction(key.x(), key.y(), key.z(), key.parentMap(), oldRep.group())
                : lineLeaveAction(allSigns, key.parentMap(), oldRep, actionFactory);
        if (leave != null) effects.add(leave);

        var join = newType == MarkerGroupType.POI
                ? actionFactory.createAddPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group())
                : lineJoinAction(allSigns, key.parentMap(), newRep, actionFactory, false);
        if (join != null) effects.add(join);

        if (effects.isEmpty()) return null;
        if (effects.size() == 1) return effects.get(0);
        return new GroupTransitionMarkerAction(effects);
    }

    // Recomputes a line group including the current sign (it must already be in signCache under this
    // group/label by the time this is called). Dispatches Set once ≥2 members exist; below that the line
    // is still incomplete and nothing is dispatched. sameGroupRecompute forces isFirstAppearance=false,
    // since a same-group/label recompute can only reach ≥2 members if a marker already existed.
    private static MarkerAction lineJoinAction(List<SignEntry> allSigns, String parentMap, Representation rep, ActionFactory actionFactory, boolean sameGroupRecompute) {
        var members = LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(), rep.label());
        if (members.size() < 2) return null;

        var isFirstAppearance = !sameGroupRecompute && members.size() == 2;
        return actionFactory.createSetLineAction(parentMap, rep.group(), rep.label(), joinLineDetail(members), toPoints(members), isFirstAppearance);
    }

    // Recomputes a line group excluding the current sign (it must already be removed from/no longer
    // present in signCache under this group/label by the time this is called). Dispatches Set if ≥2
    // members remain, Remove if it drops below 2 and a marker existed before (i.e. exactly 1 remains -
    // meaning there were 2 before), or nothing if there was never a marker to begin with (0 remain).
    private static MarkerAction lineLeaveAction(List<SignEntry> allSigns, String parentMap, Representation rep, ActionFactory actionFactory) {
        var members = LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(), rep.label());

        if (members.size() >= 2) {
            return actionFactory.createSetLineAction(parentMap, rep.group(), rep.label(), joinLineDetail(members), toPoints(members), false);
        }

        if (members.size() == 1) {
            return actionFactory.createRemoveLineAction(parentMap, rep.group(), rep.label());
        }

        return null;
    }

    private static List<LinePoint> toPoints(List<SignEntry> members) {
        return members.stream().map(e -> new LinePoint(e.key().x(), e.key().y(), e.key().z())).toList();
    }

    private static String joinLineDetail(List<SignEntry> members) {
        return members.stream().map(SignEntryHelper::getDetail).collect(Collectors.joining(System.lineSeparator()));
    }
}
