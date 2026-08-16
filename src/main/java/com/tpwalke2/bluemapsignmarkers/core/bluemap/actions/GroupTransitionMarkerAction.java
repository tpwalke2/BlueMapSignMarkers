package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import java.util.List;

// Bundles a variable number of effects (e.g. a remove-from-old-group and an add-to-new-group) into one
// dispatched unit so ReactiveQueue - which gives no ordering guarantee between independently-submitted
// messages - can't apply one effect before another under concurrent load and leave a sign's marker in an
// inconsistent state (e.g. duplicated across both groups). All effects run inside the same synchronized
// BlueMapAPIConnector.processMarkerAction() call.
public class GroupTransitionMarkerAction extends MarkerAction {
    private final List<MarkerAction> effects;

    public GroupTransitionMarkerAction(List<MarkerAction> effects) {
        // Use the first effect's identifier rather than null, so a future dispatch/logging path calling
        // getMarkerIdentifier() on this action directly (instead of going through its effects) doesn't NPE.
        super(requireNonEmpty(effects).get(0).getMarkerIdentifier());
        this.effects = effects;
    }

    private static List<MarkerAction> requireNonEmpty(List<MarkerAction> effects) {
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("GroupTransitionMarkerAction requires at least one effect");
        }
        return effects;
    }

    public List<MarkerAction> effects() {
        return effects;
    }

    @Override
    public String toString() {
        return "GroupTransitionMarkerAction{" +
                "effects=" + effects +
                '}';
    }
}
