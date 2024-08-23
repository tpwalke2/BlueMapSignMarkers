package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BlueMapAPIConnector {
    public static final String MAP_NOT_FOUND = "Map not found: {}";
    public static final String WORLD_NOT_FOUND = "World not found: {}";
    public static final String WORLD_MAPS_EMPTY = "World maps empty: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private ReactiveQueue<MarkerAction> markerActionQueue;
    private Map<MarkerSetIdentifier, MarkerSet> markerSets;
    private BlueMapAPI blueMapAPI;
    private List<IResetHandler> resetHandlers = new ArrayList<>();

    public BlueMapAPIConnector() {
        resetQueue();

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

    public void addResetHandler(IResetHandler handler) {
        resetHandlers.add(handler);
    }

    private void fireReset() {
        resetHandlers.forEach(IResetHandler::reset);
    }

    private void resetQueue() {
        markerActionQueue = new ReactiveQueue<>(
                () -> BlueMapAPI.getInstance().isPresent(),
                this::processMarkerAction,
                this::onError
        );

        markerSets = new ConcurrentHashMap<>();
    }

    private void processMarkerAction(MarkerAction markerAction) {
        if (!ConfigManager.get().isSilentLogs())
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
            var markerGroup = addAction.getMarkerIdentifier().parentSet().markerGroup();
            if (markerGroup.type() == MarkerGroupType.POI) {
                LOGGER.debug("Adding POI marker...");
                var markerBuilder = POIMarker.builder()
                        .position(addAction.getX(), addAction.getY(), addAction.getZ())
                        .label(addAction.getLabel())
                        .detail(addAction.getDetail());

                if (markerGroup.icon() != null && !markerGroup.icon().isEmpty()) {
                    markerBuilder.icon(markerGroup.icon(), markerGroup.offsetX(), markerGroup.offsetY());
                }

                LOGGER.debug("Adding marker (id {}) to marker set: {}", addAction.getMarkerIdentifier().getId(), markerSetMap);
                markerSetMap.put(addAction.getMarkerIdentifier().getId(), markerBuilder.build());
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
        if (markerActionQueue.isShutdown()) {
            resetQueue();

            fireReset();
        }

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
                .or(() -> Optional.ofNullable(map.get().getMarkerSets().get(markerSetIdentifier.markerGroup().prefix())))
                .orElseGet(() -> MarkerSet.builder()
                        .label(markerSetIdentifier.markerGroup().name())
                        .build());

        LOGGER.debug("Caching marker set: {}", markerSetIdentifier);
        markerSets.putIfAbsent(markerSetIdentifier, markerSet);
        map.get().getMarkerSets().putIfAbsent(markerSetIdentifier.markerGroup().prefix(), markerSet);

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
