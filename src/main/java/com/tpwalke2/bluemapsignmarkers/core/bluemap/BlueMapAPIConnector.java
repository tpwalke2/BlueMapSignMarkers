package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Optional;

public class BlueMapAPIConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private final ReactiveQueue<MarkerAction> markerActionQueue;
    private final HashMap<MarkerSetIdentifier, MarkerSet> markerSets;
    private BlueMapAPI blueMapAPI;

    public BlueMapAPIConnector() {
        markerActionQueue = new ReactiveQueue<>(
                () -> BlueMapAPI.getInstance().isPresent(),
                this::processMarkerAction,
                this::onError
        );

        markerSets = new HashMap<>();

        BlueMapAPI.onEnable(this::onEnable);
        BlueMapAPI.onDisable(this::onDisable);
    }

    public void shutdown() {
        BlueMapAPI.unregisterListener(this::onEnable);
        BlueMapAPI.unregisterListener(this::onDisable);
    }

    public void dispatch(MarkerAction action) {
        markerActionQueue.enqueue(action);
    }

    private void processMarkerAction(MarkerAction markerAction) {
        LOGGER.info("Processing marker action: {}", markerAction);

        var markerSet = getMarkerSet(markerAction.getMarkerIdentifier().parentSet());

        if (markerSet.isEmpty()) {
            LOGGER.warn("Marker set not found.");
            return;
        }

        LOGGER.info("Marker set found.");
        var markerSetMap = markerSet.get().getMarkers();

        if (markerAction instanceof AddMarkerAction addAction) {
            LOGGER.info("Adding marker...");
            if (addAction.getMarkerIdentifier().parentSet().markerType() == MarkerType.POI) {
                var marker = POIMarker.builder()
                        .position(addAction.getX(), addAction.getY(), addAction.getZ())
                        .label(addAction.getLabel())
                        .detail(addAction.getDetail())
                        .build();
                markerSetMap.put(addAction.getMarkerIdentifier().getId(), marker);
            }
        } else if (markerAction instanceof RemoveMarkerAction removeAction) {
            LOGGER.info("Removing marker...");
            markerSetMap.remove(removeAction.getMarkerIdentifier().getId());
        } else if (markerAction instanceof UpdateMarkerAction updateAction) {
            LOGGER.info("Updating marker...");
            var marker = Optional.of(markerSetMap.get(markerAction.getMarkerIdentifier().getId())).get();
            marker.setLabel(updateAction.getNewLabel());
            if (marker instanceof POIMarker poiMarker) {
                poiMarker.setDetail(updateAction.getNewDetails());
            }
        } else {
            LOGGER.warn("Unknown marker action: {}", markerAction);
        }
    }

    private void onError(Throwable throwable) {
        LOGGER.error("Error processing marker action", throwable);
    }

    private void onEnable(BlueMapAPI api) {
        this.blueMapAPI = api;
        markerActionQueue.process();
    }

    private void onDisable(BlueMapAPI api) {
        markerActionQueue.shutdown();
    }

    private Optional<MarkerSet> getMarkerSet(MarkerSetIdentifier markerSetIdentifier) {
        var result = Optional.ofNullable(markerSets.get(markerSetIdentifier));

        if (result.isPresent()) return result;
        if (markerSetIdentifier.map() == WorldMap.UNKNOWN) {
            LOGGER.warn("Unknown map: {}", markerSetIdentifier.map());
            return result;
        }

        LOGGER.info("Marker set not found. Attempting to build marker set: {}", markerSetIdentifier);

        var map = this.blueMapAPI.getMap(markerSetIdentifier.map().id.toLowerCase());
        if (map.isEmpty()) {
            LOGGER.warn("Map not found: {}", markerSetIdentifier.map());
            return result;
        }

        var markerSet = Optional.ofNullable(markerSets.get(markerSetIdentifier))
                .or(() -> Optional.ofNullable(map.get().getMarkerSets().get(markerSetIdentifier.markerType().id)))
                .orElseGet(() -> MarkerSet.builder()
                        .label(markerSetIdentifier.markerType().label)
                        .build());

        LOGGER.info("Caching marker set: {}", markerSetIdentifier);
        markerSets.putIfAbsent(markerSetIdentifier, markerSet);
        map.get().getMarkerSets().putIfAbsent(markerSetIdentifier.markerType().id, markerSet);

        return Optional.of(markerSet);
    }
}