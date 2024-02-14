package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BlueMapAPIConnector {
    public static final String MAP_NOT_FOUND = "Map not found: {}";
    public static final String WORLD_NOT_FOUND = "World not found: {}";
    public static final String WORLD_MAPS_EMPTY = "World maps empty: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private final ReactiveQueue<MarkerAction> markerActionQueue;
    private final Map<MarkerSetIdentifier, MarkerSet> markerSets;
    private BlueMapAPI blueMapAPI;

    public BlueMapAPIConnector() {
        markerActionQueue = new ReactiveQueue<>(
                () -> BlueMapAPI.getInstance().isPresent(),
                this::processMarkerAction,
                this::onError
        );

        markerSets = new ConcurrentHashMap<>();

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
            LOGGER.debug("Marker set not found.");
            return;
        }

        LOGGER.debug("Marker set found.");
        var markerSetMap = markerSet.get().getMarkers();

        if (markerAction instanceof AddMarkerAction addAction) {
            LOGGER.debug("Adding marker...");
            if (addAction.getMarkerIdentifier().parentSet().markerType() == MarkerType.POI) {
                var marker = POIMarker.builder()
                        .position(addAction.getX(), addAction.getY(), addAction.getZ())
                        .label(addAction.getLabel())
                        .detail(addAction.getDetail())
                        .build();
                markerSetMap.put(addAction.getMarkerIdentifier().getId(), marker);
            }
        } else if (markerAction instanceof RemoveMarkerAction removeAction) {
            LOGGER.debug("Removing marker...");
            markerSetMap.remove(removeAction.getMarkerIdentifier().getId());
        } else if (markerAction instanceof UpdateMarkerAction updateAction) {
            LOGGER.debug("Updating marker...");
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

    private synchronized Optional<MarkerSet> getMarkerSet(MarkerSetIdentifier markerSetIdentifier) {
        var result = Optional.ofNullable(markerSets.get(markerSetIdentifier));

        if (result.isPresent()) return result;

        LOGGER.debug("Marker set not found. Attempting to build marker set: {}", markerSetIdentifier);
        var map = getMap(markerSetIdentifier.mapId());
        if (map.isEmpty()) {
            LOGGER.warn(MAP_NOT_FOUND, markerSetIdentifier.mapId());
            return result;
        }

        var markerSet = Optional.ofNullable(markerSets.get(markerSetIdentifier))
                .or(() -> Optional.ofNullable(map.get().getMarkerSets().get(markerSetIdentifier.markerType().id)))
                .orElseGet(() -> MarkerSet.builder()
                        .label(markerSetIdentifier.markerType().label)
                        .build());

        LOGGER.debug("Caching marker set: {}", markerSetIdentifier);
        markerSets.putIfAbsent(markerSetIdentifier, markerSet);
        map.get().getMarkerSets().putIfAbsent(markerSetIdentifier.markerType().id, markerSet);

        return Optional.of(markerSet);
    }

    private Optional<BlueMapMap> getMap(String mapId) {
        var result = this.blueMapAPI.getMap(mapId);

        if (result.isPresent()) return result;

        var world = this.blueMapAPI.getWorld(mapId);

        if (world.isEmpty()) {
            LOGGER.warn(WORLD_NOT_FOUND, mapId);
            return Optional.empty();
        }

        var maps = world.get().getMaps();
        if (maps.isEmpty()) {
            LOGGER.warn(WORLD_MAPS_EMPTY, mapId);
            return Optional.empty();
        }

        return maps.stream().findFirst();
    }
}