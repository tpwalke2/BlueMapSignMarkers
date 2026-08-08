package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;

// Bundles a remove-from-old-group and add-to-new-group into one dispatched unit so ReactiveQueue -
// which gives no ordering guarantee between independently-submitted messages - can't apply the add
// before the remove under concurrent load and leave a sign's marker duplicated across both groups.
// Both halves run inside the same synchronized BlueMapAPIConnector.processMarkerAction() call.
public class ChangeGroupMarkerAction extends MarkerAction {
    private final MarkerIdentifier oldMarkerIdentifier;
    private final String label;
    private final String detail;

    public ChangeGroupMarkerAction(
            MarkerIdentifier oldMarkerIdentifier,
            MarkerIdentifier newMarkerIdentifier,
            String label,
            String detail) {
        super(newMarkerIdentifier);
        this.oldMarkerIdentifier = oldMarkerIdentifier;
        this.label = label;
        this.detail = detail;
    }

    public MarkerIdentifier getOldMarkerIdentifier() {
        return oldMarkerIdentifier;
    }

    public MarkerIdentifier getNewMarkerIdentifier() {
        return getMarkerIdentifier();
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "ChangeGroupMarkerAction{" +
                "oldMarkerIdentifier=" + oldMarkerIdentifier +
                ", newMarkerIdentifier=" + getMarkerIdentifier() +
                '}';
    }
}
