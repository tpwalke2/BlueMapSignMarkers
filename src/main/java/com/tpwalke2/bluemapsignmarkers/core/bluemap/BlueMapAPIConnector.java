package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.markers.MarkerSetIdentifierCollection;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.markers.MarkerType;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Optional;

public class BlueMapAPIConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private final MarkerSetIdentifierCollection markerSetIdentifierCollection;
    private final ReactiveQueue<MarkerAction> markerActionQueue;
    private final HashMap<MarkerSetIdentifier, MarkerSet> markerSets;

    public BlueMapAPIConnector(MarkerSetIdentifierCollection markerSetIdentifierCollection) {
        this.markerSetIdentifierCollection = markerSetIdentifierCollection;

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

        var markerSet = Optional.ofNullable(markerSets.get(markerAction.getMarkerIdentifier().parentSet()));

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
        buildMarkerSets(api);
        markerActionQueue.process();
    }

    private void onDisable(BlueMapAPI api) {
        markerActionQueue.shutdown();
    }

    private WorldMap getMap(String mapId) {
        return Optional
                .ofNullable(WorldMap.valueOfId(mapId.toLowerCase()))
                .orElse(WorldMap.UNKNOWN);
    }

    private void buildMarkerSets(BlueMapAPI api) {
        LOGGER.info("Building marker sets...");
        for (BlueMapMap map : api.getMaps()) {
            var markerMap = getMap(map.getId());
            if (markerMap == WorldMap.UNKNOWN) continue;

            for (MarkerType markerType: MarkerType.values()) {
                var key = markerSetIdentifierCollection.getIdentifier(
                        markerMap,
                        markerType);

                var markerSet = Optional.ofNullable(markerSets.get(key))
                        .or(() -> Optional.ofNullable(map.getMarkerSets().get(markerType.id)))
                        .orElseGet(() -> MarkerSet.builder()
                        .label(markerType.label)
                        .build());

                LOGGER.info("Caching marker set: {}", key);
                markerSets.putIfAbsent(key, markerSet);
                map.getMarkerSets().putIfAbsent(markerType.id, markerSet);
            }
        }
    }
}