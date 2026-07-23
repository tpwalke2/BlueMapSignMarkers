package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.HtmlUtils;
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

    // Captures the field under the same lock resetQueue()/getMarkerSets() use before calling enqueue()
    // outside it — dispatch() is on the hot path (every sign add/update/remove), so the lock is held only
    // long enough to read a consistent reference, not across enqueue()'s own work (finding #11,
    // plans/codebase-review-2026-07-11.md).
    public void dispatch(MarkerAction action) {
        ReactiveQueue<MarkerAction> queue;
        synchronized (this) {
            queue = markerActionQueue;
        }

        queue.enqueue(action);
    }

    public void addResetHandler(IResetHandler handler) {
        resetHandlers.add(handler);
    }

    private void fireReset() {
        resetHandlers.forEach(IResetHandler::reset);
    }

    // synchronized so this can't interleave with getMarkerSets()/processMarkerAction() reading
    // markerActionQueue/markerSetsCache mid-reassignment (finding #11). Safe to hold across resetQueue()'s
    // and fireReset()'s work from onEnable(): neither blocks waiting on another thread — enqueue()/process()
    // only submit to the executor, they don't await the submitted task, so a worker thread blocked trying to
    // enter the synchronized processMarkerAction() just waits for this method to finish, it never needs this
    // method to make further progress.
    private synchronized void resetQueue() {
        markerActionQueue = new ReactiveQueue<>(
                () -> BlueMapAPI.getInstance().isPresent(),
                this::processMarkerAction,
                this::onError
        );

        markerSetsCache = new ConcurrentHashMap<>();
    }

    // synchronized so addMarker/updateMarker/removeMarker's mutation of a MarkerSet's marker Map can
    // never run concurrently with another dispatched action against the same (or a different) MarkerSet.
    // ReactiveQueue's executor is sized to availableProcessors(), so without this, two actions dispatched
    // close together — e.g. many signs loading at server startup — can race on the same underlying Map,
    // whose thread-safety is controlled by BlueMap's API, not this mod (findings #5 and the bulk-load
    // fanout item, plans/codebase-review-2026-07-11.md).
    private synchronized void processMarkerAction(MarkerAction markerAction) {
        // ReactiveQueue.shutdown() only stops new submissions — already-submitted tasks still run, and
        // now that this method is synchronized, several can be queued behind the monitor for a while.
        // Re-check the same condition ReactiveQueue's shouldRunCallback gates on so one of those tasks
        // can't mutate a MarkerSet after BlueMap has actually disabled in the meantime.
        if (BlueMapAPI.getInstance().isEmpty()) {
            LOGGER.debug("BlueMap API not present; skipping already-queued marker action.");
            return;
        }

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
                poiMarker.setDetail(HtmlUtils.toHtmlDetail(updateAction.getNewDetails()));
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
                    .detail(HtmlUtils.toHtmlDetail(addAction.getDetail()));

            if (markerGroup.icon() != null && !markerGroup.icon().isEmpty()) {
                markerBuilder.icon(markerGroup.icon(), markerGroup.offsetX(), markerGroup.offsetY());
            }

            LOGGER.debug("Adding marker (id {}) to marker set: {}", addAction.getMarkerIdentifier().getId(), markerSetMaps);
            markerSetMaps.forEach(stringMarkerMap -> {
                var marker = markerBuilder.build();
                marker.setMinDistance(markerGroup.minDistance());
                marker.setMaxDistance(markerGroup.maxDistance());
                stringMarkerMap.put(addAction.getMarkerIdentifier().getId(), marker);
            });
        }
    }

    private void onError(Throwable throwable) {
        LOGGER.error("Error processing marker action", throwable);
    }

    // synchronized for the same reason as resetQueue() — this reassigns blueMapAPI and (via resetQueue())
    // markerActionQueue/markerSetsCache, all fields getMarkerSets() reads under its own synchronized block.
    private synchronized void onEnable(BlueMapAPI api) {
        this.blueMapAPI = api;

        if (markerActionQueue.isShutdown()) {
            resetQueue();

            fireReset();
        }

        markerActionQueue.process();
    }

    // Not synchronized as a whole: ReactiveQueue.shutdown() blocks (awaitTermination) until in-flight
    // processMarkerAction() tasks finish, and those tasks need this same monitor to run — holding it across
    // that wait would deadlock (the in-flight task can never acquire the lock the waiting thread refuses to
    // release, and shutdownNow()'s interrupt can't free a thread blocked entering a synchronized block).
    // Instead, only the field read is synchronized (finding #11), and shutdown() itself runs lock-free.
    private void onDisable(BlueMapAPI api) {
        ReactiveQueue<MarkerAction> queue;
        synchronized (this) {
            queue = markerActionQueue;
        }

        queue.shutdown();
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

        var markerSetsToReturn = new ArrayList<MarkerSet>();

        maps.get().forEach(blueMapMap -> {
            var markerSet = blueMapMap
                    .getMarkerSets()
                    .get(markerSetIdentifier.markerGroup().name());
            if (markerSet == null) {
                markerSet = MarkerSet
                        .builder()
                        .label(markerSetIdentifier.markerGroup().name())
                        .defaultHidden(markerSetIdentifier.markerGroup().defaultHidden())
                        .build();
                blueMapMap.getMarkerSets().putIfAbsent(markerSetIdentifier.markerGroup().name(), markerSet);
            }
            LOGGER.debug("Caching marker set: {}", markerSetIdentifier);
            markerSetsToReturn.add(markerSet);
            markerSetsCache.putIfAbsent(markerSetIdentifier, markerSetsToReturn);
        });

        return Optional.of(markerSetsToReturn);
    }

    private Optional<Collection<BlueMapMap>> getMaps(String mapId) {
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
