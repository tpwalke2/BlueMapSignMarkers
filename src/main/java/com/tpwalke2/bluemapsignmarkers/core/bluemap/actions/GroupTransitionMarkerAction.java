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
        super(null);
        this.effects = effects;
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
