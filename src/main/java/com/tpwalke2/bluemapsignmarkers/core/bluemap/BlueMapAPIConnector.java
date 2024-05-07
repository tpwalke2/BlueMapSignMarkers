package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.flowpowered.math.vector.Vector3d;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.*;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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

    private void resetQueue() {
        markerActionQueue = new ReactiveQueue<>(
                () -> BlueMapAPI.getInstance().isPresent(),
                this::processMarkerAction,
                this::onError
        );

        markerSets = new ConcurrentHashMap<>();
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

        if (markerAction instanceof AddPOIMarkerAction addPOIMarkerAction) {
            LOGGER.debug("Adding POI marker...");
            var markerGroup = addPOIMarkerAction.getMarkerIdentifier().parentSet().markerGroup();
            var markerBuilder = POIMarker.builder()
                    .position(addPOIMarkerAction.getX(), addPOIMarkerAction.getY(), addPOIMarkerAction.getZ())
                    .label(addPOIMarkerAction.getLabel())
                    .detail(addPOIMarkerAction.getDetail());

            if (markerGroup.icon() != null && !markerGroup.icon().isEmpty()) {
                markerBuilder.icon(markerGroup.icon(), markerGroup.offsetX(), markerGroup.offsetY());
            }

            LOGGER.debug("Adding marker (id {}) to marker set: {}", addPOIMarkerAction.getMarkerIdentifier().getId(), markerSetMap);
            markerSetMap.put(addPOIMarkerAction.getMarkerIdentifier().getId(), markerBuilder.build());
        } else if (markerAction instanceof AddLineMarkerAction addLineMarkerAction) {
            LOGGER.debug("Adding line marker...");
            if (addLineMarkerAction.getPoints().length < 2) {
                LOGGER.warn("Line marker must have at least 2 points: {}", addLineMarkerAction);
                return;
            }

            var markerGroup = addLineMarkerAction.getMarkerIdentifier().parentSet().markerGroup();
            var line = new Line(
                    Arrays.stream(addLineMarkerAction.getPoints())
                            .map(blockPosition -> new Vector3d(blockPosition.x(), blockPosition.y(), blockPosition.z()))
                            .toArray(Vector3d[]::new));
            var lineBuilder = LineMarker.builder()
                    .position(line.getPoint(0))
                    .label(addLineMarkerAction.getLabel())
                    .line(line)
                    .lineColor(getColor(markerGroup))
                    .lineWidth(markerGroup.lineWidth());

            LOGGER.debug("Adding marker (id {}) to marker set: {}", addLineMarkerAction.getMarkerIdentifier().getId(), markerSetMap);
            markerSetMap.put(addLineMarkerAction.getMarkerIdentifier().getId(), lineBuilder.build());
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

    private Color getColor(MarkerGroup markerGroup) {
        return new Color(markerGroup.lineColor().red(), markerGroup.lineColor().green(), markerGroup.lineColor().blue(), markerGroup.lineColor().alpha());
    }

    private void onError(Throwable throwable) {
        LOGGER.error("Error processing marker action", throwable);
    }

    private void onEnable(BlueMapAPI api) {
        if (markerActionQueue.isShutdown()) {
            resetQueue();

            SignManager.reload();
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
