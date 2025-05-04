package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class BlueMapAPIConnector {
    public static final String MAP_NOT_FOUND = "Map not found: {}";
    public static final String WORLD_NOT_FOUND = "World not found: {}";
    public static final String WORLD_MAPS_EMPTY = "World maps empty: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private ReactiveQueue<MarkerAction> markerActionQueue;
    private Map<MarkerSetIdentifier, List<MarkerSet>> markerSetsCache;
    private BlueMapAPI blueMapAPI;
    private final List<IResetHandler> resetHandlers = new ArrayList<>();

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

        markerSetsCache = new ConcurrentHashMap<>();
    }

    private void processMarkerAction(MarkerAction markerAction) {
        logProcessingMessage(markerAction);

        var markerSets = getMarkerSets(markerAction.getMarkerIdentifier().parentSet());

        if (markerSets.isEmpty()) {
            LOGGER.debug("Marker sets not found.");
            return;
        }

        LOGGER.debug("Marker sets found.");
        var markerSetMaps = markerSets.get().stream().map(MarkerSet::getMarkers);

        switch (markerAction) {
            case AddMarkerAction addAction -> addMarker(addAction, markerSetMaps);
            case RemoveMarkerAction removeAction -> removeMarker(removeAction, markerSetMaps);
            case UpdateMarkerAction updateAction -> updateMarker(updateAction, markerSetMaps);
            default -> LOGGER.warn("Unknown marker action: {}", markerAction);
        }
    }

    private void logProcessingMessage(MarkerAction action) {
        var operation = switch (action) {
            case AddMarkerAction ignored -> "Adding";
            case RemoveMarkerAction ignored -> "Removing";
            case UpdateMarkerAction ignored -> "Updating";
            default -> "Processing";
        };

        var detail = "";
        if (action instanceof AddMarkerAction addAction) {
            detail = " with label='" + addAction.getDetail().replace("\n", "\\n") + "'";
        } else if (action instanceof UpdateMarkerAction updateAction) {
            detail = " to label='" + updateAction.getNewDetails().replace("\n", "\\n") + "'";
        }

        LOGGER.info("{} {} type marker in {} at x={} y={} z={}{}",
                operation,
                action.getMarkerIdentifier().parentSet().markerGroup().type(),
                action.getMarkerIdentifier().parentSet().mapId(),
                action.getX(),
                action.getY(),
                action.getZ(),
                detail);
    }

    private static void updateMarker(UpdateMarkerAction updateAction, Stream<Map<String, Marker>> markerSetMaps) {
        LOGGER.debug("Updating marker...");

        markerSetMaps.forEach(stringMarkerMap -> {
            var marker = Optional.ofNullable(stringMarkerMap.get(updateAction.getMarkerIdentifier().getId()));
            if (marker.isEmpty()) return;
            marker.get().setLabel(updateAction.getNewLabel());
            if (marker.get() instanceof POIMarker poiMarker) {
                poiMarker.setDetail(updateAction.getNewDetails());
            }
        });
    }

    private static void removeMarker(RemoveMarkerAction removeAction, Stream<Map<String, Marker>> markerSetMaps) {
        LOGGER.debug("Removing marker...");
        markerSetMaps.forEach(stringMarkerMap -> stringMarkerMap.remove(removeAction.getMarkerIdentifier().getId()));
    }

    private static void addMarker(AddMarkerAction addAction, Stream<Map<String, Marker>> markerSetMaps) {
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

            LOGGER.debug("Adding marker (id {}) to marker set: {}", addAction.getMarkerIdentifier().getId(), markerSetMaps);
            markerSetMaps.forEach(stringMarkerMap -> stringMarkerMap.put(addAction.getMarkerIdentifier().getId(), markerBuilder.build()));
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

    private synchronized Optional<List<MarkerSet>> getMarkerSets(MarkerSetIdentifier markerSetIdentifier) {
        var result = Optional.ofNullable(markerSetsCache.get(markerSetIdentifier));

        if (result.isPresent()) return result;

        LOGGER.debug("Marker set not found. Attempting to build marker set: {}", markerSetIdentifier);
        var maps = getMaps(markerSetIdentifier.mapId());
        if (maps.isEmpty()) {
            LOGGER.warn(MAP_NOT_FOUND, markerSetIdentifier.mapId());
            return result;
        }

        var existingMarkerSets = Optional.ofNullable(markerSetsCache.get(markerSetIdentifier));
        if (existingMarkerSets.isPresent()) return existingMarkerSets;

        var markerSetsToReturn = new ArrayList<MarkerSet>();

        maps.get().forEach(blueMapMap -> {
            var markerSet = blueMapMap
                    .getMarkerSets()
                    .get(markerSetIdentifier.markerGroup().prefix());
            if (markerSet == null) {
                markerSet = MarkerSet
                        .builder()
                        .label(markerSetIdentifier.markerGroup().name())
                        .defaultHidden(markerSetIdentifier.markerGroup().defaultHidden())
                        .build();
                blueMapMap.getMarkerSets().putIfAbsent(markerSetIdentifier.markerGroup().prefix(), markerSet);
            }
            LOGGER.debug("Caching marker set: {}", markerSetIdentifier);
            markerSetsCache.putIfAbsent(markerSetIdentifier, List.of(markerSet));
            markerSetsToReturn.add(markerSet);
        });

        return Optional.of(markerSetsToReturn);
    }

    private Optional<Collection<BlueMapMap>> getMaps(String mapId) {
        var result = this.blueMapAPI.getMap(mapId);

        if (result.isPresent()) return Optional.of(Collections.singletonList(result.get()));

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

        return Optional.of(maps);
    }
}
