package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddPOIAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemovePOIAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdatePOIAction;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.POIMarker;

public class BlueMapAPIConnector {
    private final ReactiveQueue<MarkerAction> markerActionQueue;

    public BlueMapAPIConnector() {
        markerActionQueue = new ReactiveQueue<>(
                () -> BlueMapAPI.getInstance().isPresent(),
                this::processMarkerAction
        );

        BlueMapAPI.onDisable(this::onDisable);
    }

    public void shutdown() {
        BlueMapAPI.unregisterListener(this::onDisable);
    }

    public void addPOIMarker(
            double x,
            double y,
            double z,
            String label,
            String detail,
            MarkerMap markerMap) {
        markerActionQueue.enqueue(new AddPOIAction(x, y, z, label, detail, markerMap));
    }

    public void removePOIMarker(
            double x,
            double y,
            double z,
            MarkerMap markerMap) {
        markerActionQueue.enqueue(new RemovePOIAction(x, y, z, markerMap));
    }

    public void updatePOIMarker(
            double x,
            double y,
            double z,
            String newLabel,
            String newDetail,
            MarkerMap markerMap) {
        markerActionQueue.enqueue(new UpdatePOIAction(x, y, z, newLabel, newDetail, markerMap));
    }

    private void processMarkerAction(MarkerAction markerAction) {
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

    private void onDisable(BlueMapAPI api) {
        markerActionQueue.shutdown();
    }
}