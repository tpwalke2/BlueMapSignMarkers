package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.flowpowered.math.vector.Vector3d;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.ColorUtils;
import com.tpwalke2.bluemapsignmarkers.common.HtmlUtils;
import com.tpwalke2.bluemapsignmarkers.common.LogUtils;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.GroupTransitionMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveLineMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.SetLineMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.DispatchedMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LineMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class BlueMapAPIConnector {
    public static final String MAP_NOT_FOUND = "Map not found: {}";
    public static final String WORLD_NOT_FOUND = "World not found: {}";
    public static final String WORLD_MAPS_EMPTY = "World maps empty: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    // volatile: resetQueue()/onEnable() always replace these fields with a brand-new object rather than
    // mutating the existing one, so all correctness requires is that a reader sees the latest *reference* —
    // that's what volatile guarantees. It says nothing about the referenced objects themselves, which are
    // freely mutated afterward through their own thread-safe methods (ReactiveQueue.enqueue()/process(),
    // ConcurrentHashMap.get()/putIfAbsent()). No reader — dispatch()/onDisable()/onEnable() for
    // markerActionQueue, getMarkerSets() for markerSetsCache, getMaps() for blueMapAPI — needs a joint
    // snapshot of more than one of these fields at once, so per-field visibility is enough; a shared lock
    // would additionally serialize dispatch() (hot path, every sign event) behind processMarkerAction()'s
    // BlueMap API calls, an unrelated critical section (findings #11 and #12,
    // plans/codebase-review-2026-07-11.md).
    private volatile ReactiveQueue<MarkerAction> markerActionQueue;
    private volatile Map<MarkerSetIdentifier, List<MarkerSet>> markerSetsCache;
    private volatile BlueMapAPI blueMapAPI;
    // Tracks whether onDisable() has actually run since the last onEnable(), so onEnable() can tell a
    // genuine BlueMap disable/re-enable cycle (a real reload, which must resetQueue()/fireReset() to
    // re-diff signCache against the reloaded config) apart from the very first onEnable() a server ever
    // sees. markerActionQueue.isShutdown() used to be used for this instead, but it also reports true for
    // a brand-new queue whose executor was never lazily created - which is exactly what happens when
    // SERVER_STARTING dispatches actions for every migrated/loaded sign before BlueMap is available:
    // process() returns early (shouldRun() false) without ever creating an executor, so the first onEnable()
    // saw isShutdown()==true and mistook startup for a reload, replacing markerActionQueue with an empty one
    // and discarding every action enqueued during sign load before a single one was ever processed.
    private volatile boolean disabledSinceLastEnable;
    private final List<IResetHandler> resetHandlers = new ArrayList<>();
    // BlueMapAPI.unregisterListener(Consumer) removes by equals/hashCode, and a method reference has no
    // custom equals - two `this::onEnable` expressions are distinct objects under default identity equality.
    // Registering and unregistering the *same* Consumer instances (rather than re-evaluating the method
    // reference at each call site) is what makes shutdown() actually detach these listeners.
    private final Consumer<BlueMapAPI> onEnableListener = this::onEnable;
    private final Consumer<BlueMapAPI> onDisableListener = this::onDisable;

    public BlueMapAPIConnector() {
        resetQueue();
        // BlueMap may already be enabled by the time this connector is constructed (e.g. mod reload); don't
        // rely solely on the onEnable callback below to set blueMapAPI, or a dispatch() landing before that
        // callback runs would see BlueMapAPI.getInstance().isPresent() true but this.blueMapAPI still null.
        blueMapAPI = BlueMapAPI.getInstance().orElse(null);

        BlueMapAPI.onEnable(onEnableListener);
        BlueMapAPI.onDisable(onDisableListener);
    }

    public void shutdown() {
        BlueMapAPI.unregisterListener(onEnableListener);
        BlueMapAPI.unregisterListener(onDisableListener);
    }

    public void dispatch(MarkerAction action) {
        markerActionQueue.enqueue(action);
    }

    public void addResetHandler(IResetHandler handler) {
        resetHandlers.add(handler);
    }

    // Called on a live config reload (SignManager.reloadConfig()) rather than resetQueue() - resetQueue()
    // also replaces markerActionQueue, abandoning its executor (never shut down) and any messages still
    // queued on it. A config reload only needs stale MarkerSet entries evicted so the next getMarkerSets()
    // call re-derives them (icon/offset/visibility/name) from the reloaded MarkerGroup.
    public void clearMarkerSetsCache() {
        markerSetsCache = new ConcurrentHashMap<>();
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

        if (markerAction instanceof GroupTransitionMarkerAction transitionAction) {
            // Each effect runs here, inside the single synchronized processMarkerAction() call that
            // dispatched the transition, so it's never observable half-applied (e.g. present in both the
            // old and new marker group, or missing from both).
            transitionAction.effects().forEach(this::applySingleAction);
            return;
        }

        applySingleAction(markerAction);
    }

    private void applySingleAction(MarkerAction markerAction) {
        logProcessingMessage(markerAction);

        switch (markerAction) {
            case AddMarkerAction addAction ->
                    applyToMarkerSets(addAction.getMarkerIdentifier(), markerSetMaps -> addMarker(addAction, markerSetMaps));
            case RemoveMarkerAction removeAction ->
                    applyToMarkerSets(removeAction.getMarkerIdentifier(), markerSetMaps -> removeMarker(removeAction, markerSetMaps));
            case UpdateMarkerAction updateAction ->
                    applyToMarkerSets(updateAction.getMarkerIdentifier(), markerSetMaps -> updateMarker(updateAction, markerSetMaps));
            case SetLineMarkerAction setAction ->
                    applyToMarkerSets(setAction.getMarkerIdentifier(), markerSetMaps -> setLineMarker(setAction, markerSetMaps));
            case RemoveLineMarkerAction removeAction ->
                    applyToMarkerSets(removeAction.getMarkerIdentifier(), markerSetMaps -> removeMarkerById(removeAction.getMarkerIdentifier().getId(), markerSetMaps));
            default -> LOGGER.warn("Unknown marker action: {}", markerAction);
        }
    }

    private void applyToMarkerSets(DispatchedMarkerIdentifier markerIdentifier, Consumer<Stream<Map<String, Marker>>> consumer) {
        var markerSets = getMarkerSets(markerIdentifier.parentSet());

        if (markerSets.isEmpty()) {
            LOGGER.debug("Marker sets not found.");
            return;
        }

        LOGGER.debug("Marker sets found.");
        consumer.accept(markerSets.get().stream().map(MarkerSet::getMarkers));
    }

    private void logProcessingMessage(MarkerAction action) {
        var operation = switch (action) {
            case AddMarkerAction ignored -> "Adding";
            case RemoveMarkerAction ignored -> "Removing";
            case UpdateMarkerAction ignored -> "Updating";
            case RemoveLineMarkerAction ignored -> "Removing";
            case SetLineMarkerAction setAction -> setAction.isFirstAppearance() ? "Adding" : "Updating";
            default -> "Processing";
        };

        var detail = "";
        if (action instanceof AddMarkerAction addAction) {
            detail = " with detail='" + LogUtils.sanitizeForLog(addAction.getDetail()) + "'";
        } else if (action instanceof UpdateMarkerAction updateAction) {
            detail = " to detail='" + LogUtils.sanitizeForLog(updateAction.getNewDetails()) + "'";
        }

        var identifier = action.getMarkerIdentifier();
        var position = "";
        if (identifier instanceof MarkerIdentifier markerIdentifier) {
            position = String.format(" at x=%d y=%d z=%d", markerIdentifier.x(), markerIdentifier.y(), markerIdentifier.z());
        } else if (identifier instanceof LineMarkerIdentifier && action instanceof SetLineMarkerAction setAction) {
            position = String.format(" label='%s' with %d point(s)", setAction.getLabel(), setAction.getPoints().size());
        } else if (identifier instanceof LineMarkerIdentifier lineMarkerIdentifier) {
            position = String.format(" label='%s'", lineMarkerIdentifier.label());
        }

        LOGGER.info("{} {} type marker in {}{}{}",
                operation,
                identifier.parentSet().markerGroup().type(),
                identifier.parentSet().mapId(),
                position,
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
        removeMarkerById(removeAction.getMarkerIdentifier().getId(), markerSetMaps);
    }

    private static void removeMarkerById(String id, Stream<Map<String, Marker>> markerSetMaps) {
        markerSetMaps.forEach(stringMarkerMap -> stringMarkerMap.remove(id));
    }

    private static void setLineMarker(SetLineMarkerAction action, Stream<Map<String, Marker>> markerSetMaps) {
        LOGGER.debug("Setting line marker...");
        if (action.getPoints().size() < 2) return; // defensive - SignManager should never dispatch below 2

        var line = new Line(action.getPoints().stream().map(p -> new Vector3d(p.x(), p.y(), p.z())).toList());
        var color = ColorUtils.parseHex(action.getLineColor());

        markerSetMaps.forEach(markers -> markers.put(action.getMarkerIdentifier().getId(),
                LineMarker.builder()
                        .label(action.getLabel())
                        .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                        .line(line)
                        .lineWidth(action.getLineWidth())
                        .lineColor(new Color(color[0], color[1], color[2], color[3]))
                        .build()));
    }

    private static void addMarker(AddMarkerAction addAction, Stream<Map<String, Marker>> markerSetMaps) {
        LOGGER.debug("Adding marker...");
        var identifier = (MarkerIdentifier) addAction.getMarkerIdentifier();
        var markerGroup = identifier.parentSet().markerGroup();
        if (markerGroup.type() == MarkerGroupType.POI) {
            LOGGER.debug("Adding POI marker...");
            var markerBuilder = POIMarker.builder()
                    .position(identifier.x(), identifier.y(), identifier.z())
                    .label(addAction.getLabel())
                    .detail(HtmlUtils.toHtmlDetail(addAction.getDetail()));

            if (markerGroup.icon() != null && !markerGroup.icon().isEmpty()) {
                markerBuilder.icon(markerGroup.icon(), markerGroup.offsetX(), markerGroup.offsetY());
            }

            LOGGER.debug("Adding marker (id {}) to marker set: {}", identifier.getId(), markerSetMaps);
            markerSetMaps.forEach(stringMarkerMap -> {
                var marker = markerBuilder.build();
                marker.setMinDistance(markerGroup.minDistance());
                marker.setMaxDistance(markerGroup.maxDistance());
                stringMarkerMap.put(identifier.getId(), marker);
            });
        }
    }

    private void onError(Throwable throwable) {
        LOGGER.error("Error processing marker action", throwable);
    }

    private void onEnable(BlueMapAPI api) {
        this.blueMapAPI = api;

        if (disabledSinceLastEnable) {
            disabledSinceLastEnable = false;
            resetQueue();

            fireReset();
        }

        markerActionQueue.process();
    }

    private void onDisable(BlueMapAPI api) {
        disabledSinceLastEnable = true;
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
            markerSetsToReturn.add(markerSet);
        });

        LOGGER.debug("Caching marker set: {}", markerSetIdentifier);
        markerSetsCache.putIfAbsent(markerSetIdentifier, markerSetsToReturn);

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
