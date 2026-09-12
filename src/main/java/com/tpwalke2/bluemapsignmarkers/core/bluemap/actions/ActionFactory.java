package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointGroupThresholds;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;

import java.util.List;

public class ActionFactory {
    private static final String LINE_KIND = "line";
    private static final String SHAPE_KIND = "shape";
    private static final String EXTRUDE_KIND = "extrude";

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
        return new AddMarkerAction(markerIdentifier(x, y, z, mapId, markerGroup), label, detail);
    }

    public RemoveMarkerAction createRemovePOIAction(
            int x,
            int y,
            int z,
            String mapId,
            MarkerGroup markerGroup) {
        return new RemoveMarkerAction(markerIdentifier(x, y, z, mapId, markerGroup));
    }

    public UpdateMarkerAction createUpdatePOIAction(
            int x,
            int y,
            int z,
            String mapId,
            String newLabel,
            String newDetail,
            MarkerGroup markerGroup) {
        return new UpdateMarkerAction(markerIdentifier(x, y, z, mapId, markerGroup), newLabel, newDetail);
    }

    public GroupTransitionMarkerAction createGroupTransitionPOIAction(
            int x,
            int y,
            int z,
            String mapId,
            String label,
            String detail,
            MarkerGroup oldMarkerGroup,
            MarkerGroup newMarkerGroup) {
        var oldIdentifier = markerIdentifier(x, y, z, mapId, oldMarkerGroup);
        var newIdentifier = markerIdentifier(x, y, z, mapId, newMarkerGroup);

        return new GroupTransitionMarkerAction(List.of(
                new RemoveMarkerAction(oldIdentifier),
                new AddMarkerAction(newIdentifier, label, detail)));
    }

    private MarkerIdentifier markerIdentifier(int x, int y, int z, String mapId, MarkerGroup markerGroup) {
        return new MarkerIdentifier(x, y, z, markerSetIdentifierCollection.getIdentifier(mapId, markerGroup));
    }

    public SetMultiPointMarkerAction createSetMultiPointAction(
            String mapId,
            MarkerGroup markerGroup,
            String label,
            String detail,
            List<LinePoint> points,
            String lineColor,
            String fillColor,
            boolean isFirstAppearance) {
        var kind = multiPointKind(markerGroup.type());
        return new SetMultiPointMarkerAction(
                new MultiPointMarkerIdentifier(kind.name(), label, markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)),
                label,
                detail,
                points,
                markerGroup.lineWidth(),
                lineColor,
                fillColor,
                kind.minMembers(),
                isFirstAppearance);
    }

    public RemoveMultiPointMarkerAction createRemoveMultiPointAction(String mapId, MarkerGroup markerGroup, String label) {
        var kind = multiPointKind(markerGroup.type());
        return new RemoveMultiPointMarkerAction(
                new MultiPointMarkerIdentifier(kind.name(), label, markerSetIdentifierCollection.getIdentifier(mapId, markerGroup)));
    }

    private record MultiPointKind(String name, int minMembers) {
    }

    private static MultiPointKind multiPointKind(MarkerGroupType type) {
        return switch (type) {
            case LINE -> new MultiPointKind(LINE_KIND, MultiPointGroupThresholds.LINE_MIN_MEMBERS);
            case SHAPE -> new MultiPointKind(SHAPE_KIND, MultiPointGroupThresholds.SHAPE_MIN_MEMBERS);
            case EXTRUDE -> new MultiPointKind(EXTRUDE_KIND, MultiPointGroupThresholds.EXTRUDE_MIN_MEMBERS);
            case POI -> throw new IllegalArgumentException(
                    "createSetMultiPointAction/createRemoveMultiPointAction require a LINE, SHAPE, or EXTRUDE marker group, got " + type);
        };
    }
}
