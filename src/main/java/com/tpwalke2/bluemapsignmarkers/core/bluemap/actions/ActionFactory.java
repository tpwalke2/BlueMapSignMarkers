package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
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
            WorldMap map,
            String label,
            String detail) {
        return new AddMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(map, MarkerType.POI)),
                label,
                detail);
    }

    public RemoveMarkerAction createRemovePOIAction(
            int x,
            int y,
            int z,
            WorldMap map) {
        return new RemoveMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(map, MarkerType.POI)));
    }

    public UpdateMarkerAction createUpdatePOIAction(
            int x,
            int y,
            int z,
            WorldMap map,
            String newLabel,
            String newDetail) {
        return new UpdateMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(map, MarkerType.POI)),
                newLabel,
                newDetail);
    }
}
