package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddPOIAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemovePOIAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdatePOIAction;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.POIMarker;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlueMapAPIConnector {

    private static final String POI_MARKER_SET_ID = "points_of_interest";
    private static final String POI_MARKER_SET_LABEL = "Points of Interest";
    private static final String POI_MARKER_SET_LAYER = "POI";

    private final Queue<MarkerAction> markerActionQueue;

    public BlueMapAPIConnector() {
        markerActionQueue = new ConcurrentLinkedQueue<>();

        BlueMapAPI.onEnable(blueMapAPI -> {
            // TODO start reactive queue processing task
        });

        BlueMapAPI.onDisable(blueMapAPI -> {
            // TODO stop reactive queue processing task
        });

        // TODO build method for unregistering BlueMapAPI.onEnable listener
        // TODO build method for unregistering BlueMapAPI.onDisable listener
    }

    public void addPOIMarker(
            double x,
            double y,
            double z,
            String label,
            String detail) {
        var marker = POIMarker.builder()
                .position(x, y, z)
                .label(label)
                .detail(detail)
                .build();

        markerActionQueue.add(new AddPOIAction(x, y, z, label, detail));
    }

    public void removePOIMarker(double x, double y, double z) {
        markerActionQueue.add(new RemovePOIAction(x, y, z));
    }

    public void updatePOIMarker(double x,
                                double y,
                                double z,
                                String newLabel,
                                String newDetail) {
        markerActionQueue.add(new UpdatePOIAction(x, y, z, newLabel, newDetail));
    }

    private void processMarkerActionQueue() {
        while (!markerActionQueue.isEmpty()) {
            var markerAction = markerActionQueue.poll();
            switch (markerAction.getOperation()) {
                case ADD -> {
                    var addPOIAction = (AddPOIAction) markerAction;
                    var marker = POIMarker.builder()
                            .position(addPOIAction.x(), addPOIAction.y(), addPOIAction.z())
                            .label(addPOIAction.label())
                            .detail(addPOIAction.detail())
                            .build();
                    // TODO add marker to marker set for correct map
                }
                case REMOVE -> {
                    var removePOIAction = (RemovePOIAction) markerAction;
                    // TODO remove marker from marker set on correct map
                }
                case UPDATE -> {
                    var updatePOIAction = (UpdatePOIAction) markerAction;
                    // TODO update marker in correct marker set on correct map
                }
            }
        }
    }
}