package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.flowpowered.math.vector.Vector3d;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.ColorUtils;
import com.tpwalke2.bluemapsignmarkers.common.HtmlUtils;
import com.tpwalke2.bluemapsignmarkers.common.LogUtils;
import com.tpwalke2.bluemapsignmarkers.core.bounds.RenderMaskEvaluator;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.GroupTransitionMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveExtrudeMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveLineMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveShapeMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.SetExtrudeMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.SetLineMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.SetShapeMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.DispatchedMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.ExtrudeMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LineMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.ShapeMarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.reactive.ReactiveQueue;
import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class BlueMapAPIConnector {
    public static final String MAP_NOT_FOUND = "Map not found: {}";
    public static final String WORLD_NOT_FOUND = "World not found: {}";
    public static final String WORLD_MAPS_EMPTY = "World maps empty: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    // Fixed on Fabric - BlueMap's own per-map config directory, read for each real map's render-mask
    // (see RenderMaskEvaluator). No BlueMap API accessor exposes this path or a bounds check directly.
    private static final Path MAPS_CONFIG_DIR = Path.of("config", "bluemap", "maps");
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
    private volatile Map<MarkerSetIdentifier, List<MappedMarkerSet>> markerSetsCache;
    // Parsed render-mask per real BlueMapMap id, invalidated alongside markerSetsCache (config
    // reload, genuine BlueMap disable/enable) rather than re-read/re-parsed on every dispatch.
    private volatile Map<String, RenderMaskEvaluator.RenderMask> renderMaskCache;
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

    // Pairs a cached MarkerSet with the real BlueMapMap id it came from - markerSetsCache used to
    // flatten this to a bare List<MarkerSet>, losing per-map identity that render-bounds gating
    // needs at apply-time.
    private record MappedMarkerSet(String mapId, MarkerSet markerSet) {}

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
        renderMaskCache = new ConcurrentHashMap<>();
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
        renderMaskCache = new ConcurrentHashMap<>();
    }

    // Not synchronized itself — prepareSingleAction resolves everything an action's mutation needs
    // (including render-mask gating decisions, which can do cold-cache disk I/O via getRenderMask) up
    // front, and the resulting Runnable is only then run inside a `synchronized (this)` block covering
    // the action's full mutation fan-out (across every one of its target maps). That keeps
    // addMarker/updateMarker/removeMarker's mutation of a MarkerSet's marker Map from running
    // concurrently with another dispatched action against the same (or a different) MarkerSet, and one
    // action's effect from ever being observably applied on some of its target maps but not others
    // (findings #5 and the bulk-load fanout item, plans/codebase-review-2026-07-11.md), while ensuring
    // the render-mask cold-load disk I/O never runs while holding that lock — including for
    // `GroupTransitionMarkerAction`, whose paired effects are each resolved unlocked, then applied
    // together under one lock acquisition. See the adversarial review finding this addressed
    // (agent-context/reviews/adversarial-review-feature-tpwalke2-67-map-bounds-2026-08-22.md, #1) and
    // its follow-up (agent-context/reviews/copilot-review-2026-08-22.md).
    private void processMarkerAction(MarkerAction markerAction) {
        // ReactiveQueue.shutdown() only stops new submissions — already-submitted tasks still run.
        // Re-check the same condition ReactiveQueue's shouldRunCallback gates on so one of those tasks
        // can't mutate a MarkerSet after BlueMap has actually disabled in the meantime.
        if (BlueMapAPI.getInstance().isEmpty()) {
            LOGGER.debug("BlueMap API not present; skipping already-queued marker action.");
            return;
        }

        if (markerAction instanceof GroupTransitionMarkerAction transitionAction) {
            // Each effect's render-mask gating is resolved (including any cold-cache disk I/O) before
            // any lock is taken, same as the single-action path below. The resulting mutations are then
            // applied together under one lock acquisition so the transition's effects are never
            // observable half-applied (e.g. present in both the old and new marker group, or missing
            // from both).
            var applies = transitionAction.effects().stream()
                    .peek(this::logProcessingMessage)
                    .map(this::prepareSingleAction)
                    .toList();
            synchronized (this) {
                applies.forEach(Runnable::run);
            }
            return;
        }

        applySingleAction(markerAction);
    }

    private void applySingleAction(MarkerAction markerAction) {
        logProcessingMessage(markerAction);
        var apply = prepareSingleAction(markerAction);
        synchronized (this) {
            apply.run();
        }
    }

    // Resolves everything an action's mutation needs (including render-mask gating decisions, which
    // can do cold-cache disk I/O via getRenderMask) up front, returning a Runnable that performs only
    // the actual MarkerSet mutation. Callers run that Runnable inside their own synchronized(this)
    // block, so the resolution work above never runs while holding the lock.
    private Runnable prepareSingleAction(MarkerAction markerAction) {
        return switch (markerAction) {
            case AddMarkerAction addAction ->
                    prepareGated(addAction.getMarkerIdentifier(), pointOf(addAction.getMarkerIdentifier()), markers -> addMarker(addAction, markers));
            case UpdateMarkerAction updateAction ->
                    prepareGated(updateAction.getMarkerIdentifier(), pointOf(updateAction.getMarkerIdentifier()), markers -> updateMarker(updateAction, markers));
            case SetLineMarkerAction setAction ->
                    prepareGated(setAction.getMarkerIdentifier(), setAction.getPoints(), markers -> setLineMarker(setAction, markers));
            case SetShapeMarkerAction setAction ->
                    prepareGated(setAction.getMarkerIdentifier(), setAction.getPoints(), markers -> setShapeMarker(setAction, markers));
            case SetExtrudeMarkerAction setAction ->
                    prepareGated(setAction.getMarkerIdentifier(), setAction.getPoints(), markers -> setExtrudeMarker(setAction, markers));
            // Explicit removes - the sign's representation is genuinely leaving, independent of
            // render bounds - so these apply unconditionally on every real map, no gating needed.
            case RemoveMarkerAction removeAction ->
                    prepareUngated(removeAction.getMarkerIdentifier(), markers -> removeMarker(removeAction, markers));
            case RemoveLineMarkerAction removeAction ->
                    prepareUngated(removeAction.getMarkerIdentifier(), markers -> removeMarkerById(removeAction.getMarkerIdentifier().getId(), markers));
            case RemoveShapeMarkerAction removeAction ->
                    prepareUngated(removeAction.getMarkerIdentifier(), markers -> removeMarkerById(removeAction.getMarkerIdentifier().getId(), markers));
            case RemoveExtrudeMarkerAction removeAction ->
                    prepareUngated(removeAction.getMarkerIdentifier(), markers -> removeMarkerById(removeAction.getMarkerIdentifier().getId(), markers));
            default -> {
                LOGGER.warn("Unknown marker action: {}", markerAction);
                yield () -> {};
            }
        };
    }

    private static List<LinePoint> pointOf(MarkerIdentifier identifier) {
        return List.of(new LinePoint(identifier.x(), identifier.y(), identifier.z()));
    }

    // Resolves the render-mask gating decision for an add/update/set effect on every real map up
    // front (a marker is only applied where at least one of the action's points is inside that map's
    // render bounds; otherwise the marker id is actively removed from that map instead of skipping the
    // effect - this is what sweeps a marker that's either newly out-of-bounds (moved sign) or was
    // created before this feature shipped, via SignManager.reset()'s existing reload-forced
    // re-dispatch). Returns a Runnable applying those already-resolved decisions, so a render-mask
    // cache miss's disk I/O (RenderMaskEvaluator.load) never runs while the caller's lock is held, while
    // still applying to all of this action's target maps as one atomic unit under that lock - so this
    // action's effect can never be observed as applied on some of its maps and not others if another
    // action for the same marker id interleaves.
    private Runnable prepareGated(
            DispatchedMarkerIdentifier markerIdentifier, List<LinePoint> points, Consumer<Map<String, Marker>> effect) {
        var markerSets = getMarkerSets(markerIdentifier.parentSet());

        if (markerSets.isEmpty()) {
            LOGGER.debug("Marker sets not found.");
            return () -> {};
        }

        LOGGER.debug("Marker sets found.");
        var decisions = markerSets.get().stream()
                .map(mapped -> Map.entry(mapped, isInsideRenderBounds(mapped.mapId(), points)))
                .toList();

        return () -> decisions.forEach(decision -> {
            var markers = decision.getKey().markerSet().getMarkers();
            if (decision.getValue()) {
                effect.accept(markers);
            } else {
                markers.remove(markerIdentifier.getId());
            }
        });
    }

    // Resolves an effect (always a removal) to every real map's MarkerSet unconditionally - used for
    // explicit remove actions, which are never gated against render bounds. Returns a Runnable the
    // caller runs under its own lock.
    private Runnable prepareUngated(DispatchedMarkerIdentifier markerIdentifier, Consumer<Map<String, Marker>> effect) {
        var markerSets = getMarkerSets(markerIdentifier.parentSet());

        if (markerSets.isEmpty()) {
            LOGGER.debug("Marker sets not found.");
            return () -> {};
        }

        LOGGER.debug("Marker sets found.");
        return () -> markerSets.get().forEach(mapped -> effect.accept(mapped.markerSet().getMarkers()));
    }

    private boolean isInsideRenderBounds(String mapId, List<LinePoint> points) {
        var mask = getRenderMask(mapId);
        return points.stream().anyMatch(p -> mask.contains(p.x(), p.y(), p.z()));
    }

    private RenderMaskEvaluator.RenderMask getRenderMask(String mapId) {
        return renderMaskCache.computeIfAbsent(mapId, id -> RenderMaskEvaluator.load(id, MAPS_CONFIG_DIR));
    }

    private void logProcessingMessage(MarkerAction action) {
        var operation = switch (action) {
            case AddMarkerAction ignored -> "Adding";
            case RemoveMarkerAction ignored -> "Removing";
            case UpdateMarkerAction ignored -> "Updating";
            case RemoveLineMarkerAction ignored -> "Removing";
            case SetLineMarkerAction setAction -> setAction.isFirstAppearance() ? "Adding" : "Updating";
            case RemoveShapeMarkerAction ignored -> "Removing";
            case SetShapeMarkerAction setAction -> setAction.isFirstAppearance() ? "Adding" : "Updating";
            case RemoveExtrudeMarkerAction ignored -> "Removing";
            case SetExtrudeMarkerAction setAction -> setAction.isFirstAppearance() ? "Adding" : "Updating";
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
            position = String.format(" label='%s' with %d point(s)", LogUtils.sanitizeForLog(setAction.getLabel()), setAction.getPoints().size());
        } else if (identifier instanceof LineMarkerIdentifier lineMarkerIdentifier) {
            position = String.format(" label='%s'", LogUtils.sanitizeForLog(lineMarkerIdentifier.label()));
        } else if (identifier instanceof ShapeMarkerIdentifier && action instanceof SetShapeMarkerAction setAction) {
            position = String.format(" label='%s' with %d point(s)", LogUtils.sanitizeForLog(setAction.getLabel()), setAction.getPoints().size());
        } else if (identifier instanceof ShapeMarkerIdentifier shapeMarkerIdentifier) {
            position = String.format(" label='%s'", LogUtils.sanitizeForLog(shapeMarkerIdentifier.label()));
        } else if (identifier instanceof ExtrudeMarkerIdentifier && action instanceof SetExtrudeMarkerAction setAction) {
            position = String.format(" label='%s' with %d point(s)", LogUtils.sanitizeForLog(setAction.getLabel()), setAction.getPoints().size());
        } else if (identifier instanceof ExtrudeMarkerIdentifier extrudeMarkerIdentifier) {
            position = String.format(" label='%s'", LogUtils.sanitizeForLog(extrudeMarkerIdentifier.label()));
        }

        LOGGER.debug("{} {} type marker in {}{}{}",
                operation,
                identifier.parentSet().markerGroup().type(),
                identifier.parentSet().mapId(),
                position,
                detail);
    }

    private static void updateMarker(UpdateMarkerAction updateAction, Map<String, Marker> markers) {
        LOGGER.debug("Updating marker...");

        var marker = Optional.ofNullable(markers.get(updateAction.getMarkerIdentifier().getId()));
        if (marker.isEmpty()) return;
        marker.get().setLabel(updateAction.getNewLabel());
        if (marker.get() instanceof POIMarker poiMarker) {
            poiMarker.setDetail(HtmlUtils.toHtmlDetail(updateAction.getNewDetails()));
        }
    }

    private static void removeMarker(RemoveMarkerAction removeAction, Map<String, Marker> markers) {
        LOGGER.debug("Removing marker...");
        removeMarkerById(removeAction.getMarkerIdentifier().getId(), markers);
    }

    private static void removeMarkerById(String id, Map<String, Marker> markers) {
        markers.remove(id);
    }

    // ColorUtils.parseHex returns alpha in the same 0-255 range as r/g/b, but BlueMap's Color(int, int, int,
    // float) constructor takes alpha in 0-1 - passing the raw 0-255 int straight through (as the earlier
    // Color(int, int, int, int alpha-as-int) overload does, treating alpha/255f) is NOT what an int
    // widening to float gives you: it silently widens to e.g. 51.0f instead of dividing by 255, which
    // BlueMap then clamps to fully opaque. Every translucent color (e.g. SHAPE's fillColor default) rendered
    // fully opaque as a result.
    private static Color toBlueMapColor(int[] rgba) {
        return new Color(rgba[0], rgba[1], rgba[2], rgba[3] / 255f);
    }

    private static void setLineMarker(SetLineMarkerAction action, Map<String, Marker> markers) {
        LOGGER.debug("Setting line marker...");
        if (action.getPoints().size() < 2) return; // defensive - SignManager should never dispatch below 2

        var line = new Line(action.getPoints().stream().map(p -> new Vector3d(p.x(), p.y(), p.z())).toList());
        var color = ColorUtils.parseHex(action.getLineColor());
        var markerGroup = action.getMarkerIdentifier().parentSet().markerGroup();

        var marker = LineMarker.builder()
                .label(action.getLabel())
                .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                .line(line)
                .lineWidth(action.getLineWidth())
                .lineColor(toBlueMapColor(color))
                .depthTestEnabled(markerGroup.depthTest())
                .build();
        marker.setMinDistance(markerGroup.minDistance());
        marker.setMaxDistance(markerGroup.maxDistance());
        markers.put(action.getMarkerIdentifier().getId(), marker);
    }

    private static void setShapeMarker(SetShapeMarkerAction action, Map<String, Marker> markers) {
        LOGGER.debug("Setting shape marker...");
        if (action.getPoints().size() < 3) return; // defensive - SignManager should never dispatch below 3

        var points = action.getPoints();
        var shape = new Shape(points.stream().map(p -> new Vector2d(p.x(), p.z())).toList());
        // Shape height anchors to the tallest member rather than placement order, so the polygon always
        // clears the terrain/builds of every sign that defines it.
        var shapeY = (float) points.stream().mapToInt(LinePoint::y).max().orElseThrow();
        var lineColor = ColorUtils.parseHex(action.getLineColor());
        var fillColor = ColorUtils.parseHex(action.getFillColor());
        var markerGroup = action.getMarkerIdentifier().parentSet().markerGroup();

        var marker = ShapeMarker.builder()
                .label(action.getLabel())
                .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                .shape(shape, shapeY)
                .lineWidth(action.getLineWidth())
                .lineColor(toBlueMapColor(lineColor))
                .fillColor(toBlueMapColor(fillColor))
                .depthTestEnabled(markerGroup.depthTest())
                .build();
        marker.setMinDistance(markerGroup.minDistance());
        marker.setMaxDistance(markerGroup.maxDistance());
        markers.put(action.getMarkerIdentifier().getId(), marker);
    }

    private static void setExtrudeMarker(SetExtrudeMarkerAction action, Map<String, Marker> markers) {
        LOGGER.debug("Setting extrude marker...");
        if (action.getPoints().size() < 3) return; // defensive - SignManager should never dispatch below 3

        var points = action.getPoints();
        var shape = new Shape(points.stream().map(p -> new Vector2d(p.x(), p.z())).toList());
        // Floor/ceiling anchor to the lowest/tallest member respectively, so the volume always spans the
        // full height range its members were placed at, independent of placement order.
        var minY = (float) points.stream().mapToInt(LinePoint::y).min().orElseThrow();
        var maxY = (float) points.stream().mapToInt(LinePoint::y).max().orElseThrow();
        var lineColor = ColorUtils.parseHex(action.getLineColor());
        var fillColor = ColorUtils.parseHex(action.getFillColor());
        var markerGroup = action.getMarkerIdentifier().parentSet().markerGroup();

        var marker = ExtrudeMarker.builder()
                .label(action.getLabel())
                .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                .shape(shape, minY, maxY)
                .lineWidth(action.getLineWidth())
                .lineColor(toBlueMapColor(lineColor))
                .fillColor(toBlueMapColor(fillColor))
                .depthTestEnabled(markerGroup.depthTest())
                .build();
        marker.setMinDistance(markerGroup.minDistance());
        marker.setMaxDistance(markerGroup.maxDistance());
        markers.put(action.getMarkerIdentifier().getId(), marker);
    }

    private static void addMarker(AddMarkerAction addAction, Map<String, Marker> markers) {
        LOGGER.debug("Adding marker...");
        var identifier = addAction.getMarkerIdentifier();
        var markerGroup = identifier.parentSet().markerGroup();
        if (markerGroup.type() == MarkerGroupType.POI) {
            LOGGER.debug("Adding POI marker...");
            var markerBuilder = POIMarker.builder()
                    .position((double) identifier.x(), (double) identifier.y(), (double) identifier.z())
                    .label(addAction.getLabel())
                    .detail(HtmlUtils.toHtmlDetail(addAction.getDetail()));

            if (markerGroup.icon() != null && !markerGroup.icon().isEmpty()) {
                markerBuilder.icon(markerGroup.icon(), markerGroup.offsetX(), markerGroup.offsetY());
            }

            if (!markerGroup.cssClasses().isEmpty()) {
                markerBuilder.styleClasses(markerGroup.cssClasses().toArray(new String[0]));
            }

            LOGGER.debug("Adding marker (id {}) to marker set", identifier.getId());
            var marker = markerBuilder.build();
            marker.setMinDistance(markerGroup.minDistance());
            marker.setMaxDistance(markerGroup.maxDistance());
            markers.put(identifier.getId(), marker);
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

    private synchronized Optional<List<MappedMarkerSet>> getMarkerSets(MarkerSetIdentifier markerSetIdentifier) {
        var result = Optional.ofNullable(markerSetsCache.get(markerSetIdentifier));

        if (result.isPresent()) return result;

        LOGGER.debug("Marker set not found. Attempting to build marker set: {}", markerSetIdentifier);
        var maps = getMaps(markerSetIdentifier.mapId());
        if (maps.isEmpty()) {
            LOGGER.warn(MAP_NOT_FOUND, markerSetIdentifier.mapId());
            return result;
        }

        var markerSetsToReturn = new ArrayList<MappedMarkerSet>();

        maps.get().forEach(blueMapMap -> {
            var markerSet = blueMapMap
                    .getMarkerSets()
                    .get(markerSetIdentifier.markerGroup().name());
            if (markerSet == null) {
                markerSet = MarkerSet
                        .builder()
                        .label(markerSetIdentifier.markerGroup().name())
                        .defaultHidden(markerSetIdentifier.markerGroup().defaultHidden())
                        .sorting(markerSetIdentifier.markerGroup().sorting())
                        .toggleable(markerSetIdentifier.markerGroup().toggleable())
                        .build();
                blueMapMap.getMarkerSets().putIfAbsent(markerSetIdentifier.markerGroup().name(), markerSet);
            } else {
                markerSet.setDefaultHidden(markerSetIdentifier.markerGroup().defaultHidden());
                markerSet.setSorting(markerSetIdentifier.markerGroup().sorting());
                markerSet.setToggleable(markerSetIdentifier.markerGroup().toggleable());
            }
            markerSetsToReturn.add(new MappedMarkerSet(blueMapMap.getId(), markerSet));
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
