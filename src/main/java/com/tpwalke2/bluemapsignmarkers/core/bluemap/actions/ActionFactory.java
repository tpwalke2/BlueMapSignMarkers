package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.BlockPosition;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;

import java.util.stream.Stream;

public class ActionFactory {
    private final MarkerSetIdentifierCollection markerSetIdentifierCollection;

    public ActionFactory(MarkerSetIdentifierCollection markerSetIdentifierCollection) {
        this.markerSetIdentifierCollection = markerSetIdentifierCollection;
    }

    public AddLineMarkerAction createAddLineAction(
            int x,
            int y,
            int z,
            String mapId,
            String label,
            MarkerGroup markerGroup,
            Stream<BlockPosition> points
    ) {
        return new AddLineMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)),
                label,
                points.toArray(BlockPosition[]::new));
    }

    public AddPOIMarkerAction createAddPOIAction(
            int x,
            int y,
            int z,
            String mapId,
            String label,
            String detail,
            MarkerGroup markerGroup) {
        return new AddPOIMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)),
                label,
                detail);
    }

    public RemoveMarkerAction createRemoveMarkerAction(
            int x,
            int y,
            int z,
            String mapId,
            MarkerGroup markerGroup) {
        return new RemoveMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)));
    }

    public UpdateMarkerAction createUpdatePOIAction(
            int x,
            int y,
            int z,
            String mapId,
            String newLabel,
            String newDetail,
            MarkerGroup markerGroup) {
        return new UpdateMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)),
                newLabel,
                newDetail);
    }
}
