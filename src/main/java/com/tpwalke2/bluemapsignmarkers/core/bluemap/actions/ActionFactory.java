package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;

public class ActionFactory {
    private final MarkerSetIdentifierCollection markerSetIdentifierCollection;

    public ActionFactory(MarkerSetIdentifierCollection markerSetIdentifierCollection) {
        this.markerSetIdentifierCollection = markerSetIdentifierCollection;
    }

    public AddMarkerAction createAddPOIAction(
            int x,
            int y,
            int z,
            String mapId,
            String label,
            String detail) {
        return new AddMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, MarkerType.POI)),
                label,
                detail);
    }

    public RemoveMarkerAction createRemovePOIAction(
            int x,
            int y,
            int z,
            String mapId) {
        return new RemoveMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, MarkerType.POI)));
    }

    public UpdateMarkerAction createUpdatePOIAction(
            int x,
            int y,
            int z,
            String mapId,
            String newLabel,
            String newDetail) {
        return new UpdateMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, MarkerType.POI)),
                newLabel,
                newDetail);
    }
}
