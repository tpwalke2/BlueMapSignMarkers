package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LineMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import com.tpwalke2.bluemapsignmarkers.core.markers.ShapeMarkerIdentifier;

import java.util.List;

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
            String detail,
            MarkerGroup markerGroup) {
        return new AddMarkerAction(
                new MarkerIdentifier(
                        x,
                        y,
                        z,
                        markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)),
                label,
                detail);
    }

    public RemoveMarkerAction createRemovePOIAction(
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

    public GroupTransitionMarkerAction createChangeGroupPOIAction(
            int x,
            int y,
            int z,
            String mapId,
            String label,
            String detail,
            MarkerGroup oldMarkerGroup,
            MarkerGroup newMarkerGroup) {
        var oldIdentifier = new MarkerIdentifier(
                x,
                y,
                z,
                markerSetIdentifierCollection.getIdentifier(mapId, oldMarkerGroup));
        var newIdentifier = new MarkerIdentifier(
                x,
                y,
                z,
                markerSetIdentifierCollection.getIdentifier(mapId, newMarkerGroup));

        return new GroupTransitionMarkerAction(List.of(
                new RemoveMarkerAction(oldIdentifier),
                new AddMarkerAction(newIdentifier, label, detail)));
    }

    public SetLineMarkerAction createSetLineAction(
            String mapId,
            MarkerGroup markerGroup,
            String label,
            String detail,
            List<LinePoint> points,
            boolean isFirstAppearance) {
        return new SetLineMarkerAction(
                new LineMarkerIdentifier(label, markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)),
                label,
                detail,
                points,
                markerGroup.lineWidth(),
                markerGroup.lineColor(),
                isFirstAppearance);
    }

    public RemoveLineMarkerAction createRemoveLineAction(String mapId, MarkerGroup markerGroup, String label) {
        return new RemoveLineMarkerAction(
                new LineMarkerIdentifier(label, markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)));
    }

    public SetShapeMarkerAction createSetShapeAction(
            String mapId,
            MarkerGroup markerGroup,
            String label,
            String detail,
            List<LinePoint> points,
            boolean isFirstAppearance) {
        return new SetShapeMarkerAction(
                new ShapeMarkerIdentifier(label, markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)),
                label,
                detail,
                points,
                markerGroup.lineWidth(),
                markerGroup.lineColor(),
                markerGroup.fillColor(),
                isFirstAppearance);
    }

    public RemoveShapeMarkerAction createRemoveShapeAction(String mapId, MarkerGroup markerGroup, String label) {
        return new RemoveShapeMarkerAction(
                new ShapeMarkerIdentifier(label, markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)));
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
