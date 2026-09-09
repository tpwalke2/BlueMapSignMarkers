package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.flowpowered.math.vector.Vector3d;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.ColorUtils;
import com.tpwalke2.bluemapsignmarkers.common.HtmlUtils;
import com.tpwalke2.bluemapsignmarkers.common.LogUtils;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
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
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifier;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;
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
    private static final String MAP_NOT_FOUND = "Map not found: {}";
    private static final String WORLD_NOT_FOUND = "World not found: {}";
    private static final String WORLD_MAPS_EMPTY = "World maps empty: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    // Fixed on Fabric - BlueMap's own per-map config directory, read for each real map's render-mask
    // (see RenderMaskEvaluator). No BlueMap API accessor exposes this path or a bounds check directly.
    private static final Path MAPS_CONFIG_DIR = Path.of("config", "bluemap", "maps");
    // volatile: resetQueue()/onEnable() always replace these fields with a brand-new object rather than
    // mutating the existing one, so all correctness requires is that a reader sees the latest *reference* —
    // that's what volatile guarantees. It says nothing about the referenced objects themselves, which are
    // freely mutated afterward through their own thread-safe methods (ReactiveQueue.enqueue()/process(),
    // ConcurrentHashMap.get()/putIfAbsent()). No reader — dispatch()/onDisable()/onEnable() for
    // markerActionQueue, getMarkerSets() for markerSetsCache — needs a joint snapshot of more than one
    // of these fields at once, so per-field visibility is enough; a shared lock
    // would additionally serialize dispatch() (hot path, every sign event) behind processMarkerAction()'s
    // BlueMap API calls, an unrelated critical section (findings #11 and #12,
    // plans/codebase-review-2026-07-11.md).
    private volatile ReactiveQueue<MarkerAction> markerActionQueue;
    private volatile Map<MarkerSetIdentifier, List<MappedMarkerSet>> markerSetsCache;
    // Parsed render-mask per real BlueMapMap id, invalidated alongside markerSetsCache (config
    // reload, genuine BlueMap disable/enable) rather than re-read/re-parsed on every dispatch.
    private volatile Map<String, RenderMaskEvaluator.RenderMask> renderMaskCache;
    // Set by onDisable() from markerActionQueue.shutdown()'s return value; read by the next onEnable() to
    // decide whether to warn that a non-interruptible straggler task may still race the reset replay below
    // (findings 26/27, agent-context/reviews/full-codebase-review_2026-09-07_0900.md). There's no better
    // recovery available - a task that ignores interruption can't be forced to stop - so this only makes
    // the violation observable rather than blocking onEnable() indefinitely.
    private volatile boolean lastShutdownConfirmedClean = true;
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

        BlueMapAPI.onEnable(onEnableListener);
        BlueMapAPI.onDisable(onDisableListener);
    }

    // Also retires markerActionQueue (awaiting up to its configured shutdownAwaitSeconds) rather than just
    // unregistering the BlueMap listeners - previously this left the queue's fixed thread pool alive and
    // any in-flight marker action unawaited on server stop, so BlueMapSignMarkersMod.onServerStopping's
    // configured shutdown timeout had no effect at all. Mirrors onDisable()'s own shutdown() call, but
    // this path runs on server stop regardless of whether BlueMap ever fired onDisable first.
    public void shutdown() {
        BlueMapAPI.unregisterListener(onEnableListener);
        BlueMapAPI.unregisterListener(onDisableListener);

        if (!markerActionQueue.shutdown()) {
            LOGGER.warn("Marker action queue shutdown could not confirm all in-flight tasks stopped before "
                    + "server stop completed");
        }
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
                this::onError,
                ConfigManager.get().getShutdownAwaitSeconds()
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

    // Package-private (not private) specifically so it's directly testable - see the
    // resolveExtrudeHeightRange comment below and BlueMapAPIConnectorTest.
    static List<LinePoint> pointOf(MarkerIdentifier identifier) {
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
        return isInsideRenderBounds(getRenderMask(mapId), points);
    }

    // Pure point-vs-mask test split out of the mapId/cache-lookup overload above so it's directly
    // testable without a live BlueMapAPIConnector instance (bluemap-api is compileOnly, not on the
    // test classpath - see BlueMapAPIConnectorTest).
    static boolean isInsideRenderBounds(RenderMaskEvaluator.RenderMask mask, List<LinePoint> points) {
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
        } else if (identifier instanceof MultiPointMarkerIdentifier multiPointMarkerIdentifier) {
            position = switch (action) {
                case SetLineMarkerAction setAction -> String.format(" label='%s' with %d point(s)", LogUtils.sanitizeForLog(setAction.getLabel()), setAction.getPoints().size());
                case SetShapeMarkerAction setAction -> String.format(" label='%s' with %d point(s)", LogUtils.sanitizeForLog(setAction.getLabel()), setAction.getPoints().size());
                case SetExtrudeMarkerAction setAction -> String.format(" label='%s' with %d point(s)", LogUtils.sanitizeForLog(setAction.getLabel()), setAction.getPoints().size());
                default -> String.format(" label='%s'", LogUtils.sanitizeForLog(multiPointMarkerIdentifier.label()));
            };
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
        if (action.getPoints().size() < 2) {
            // defensive - SignManager should never dispatch below 2; warn so a regression is visible.
            LOGGER.warn("Refusing to set line marker '{}' with fewer than 2 points ({})",
                    LogUtils.sanitizeForLog(action.getLabel()), action.getPoints().size());
            return;
        }

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
        if (action.getPoints().size() < 3) {
            // defensive - SignManager should never dispatch below 3; warn so a regression is visible.
            LOGGER.warn("Refusing to set shape marker '{}' with fewer than 3 points ({})",
                    LogUtils.sanitizeForLog(action.getLabel()), action.getPoints().size());
            return;
        }

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

    // Floor/ceiling of an extrude volume - a plain value holder (no bluemap-api types), so
    // resolveExtrudeHeightRange stays testable even though bluemap-api is compileOnly and not on the test
    // classpath (see AGENTS.md's testable-vs-game-coupled split).
    record ExtrudeHeightRange(float minY, float maxY) {}

    // Floor/ceiling anchor to the lowest/tallest member respectively, so the volume always spans the full
    // height range its members were placed at, independent of placement order. All members at the same Y
    // (e.g. one floor) would otherwise collapse the extrusion to zero height, rendering nothing on the map
    // with no indication why - so give it a one-block floor instead.
    static ExtrudeHeightRange resolveExtrudeHeightRange(String label, List<LinePoint> points) {
        var minY = (float) points.stream().mapToInt(LinePoint::y).min().orElseThrow();
        var maxY = (float) points.stream().mapToInt(LinePoint::y).max().orElseThrow();
        if (maxY <= minY) {
            LOGGER.debug("Extrude marker label='{}' has all members at Y={}; giving it a minimum 1-block height",
                    LogUtils.sanitizeForLog(label), minY);
            maxY = minY + 1;
        }
        return new ExtrudeHeightRange(minY, maxY);
    }

    private static void setExtrudeMarker(SetExtrudeMarkerAction action, Map<String, Marker> markers) {
        LOGGER.debug("Setting extrude marker...");
        if (action.getPoints().size() < 3) {
            // defensive - SignManager should never dispatch below 3; warn so a regression is visible.
            LOGGER.warn("Refusing to set extrude marker '{}' with fewer than 3 points ({})",
                    LogUtils.sanitizeForLog(action.getLabel()), action.getPoints().size());
            return;
        }

        var points = action.getPoints();
        var shape = new Shape(points.stream().map(p -> new Vector2d(p.x(), p.z())).toList());
        var heightRange = resolveExtrudeHeightRange(action.getLabel(), points);
        var lineColor = ColorUtils.parseHex(action.getLineColor());
        var fillColor = ColorUtils.parseHex(action.getFillColor());
        var markerGroup = action.getMarkerIdentifier().parentSet().markerGroup();

        var marker = ExtrudeMarker.builder()
                .label(action.getLabel())
                .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                .shape(shape, heightRange.minY(), heightRange.maxY())
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
        var identifier = addAction.getMarkerIdentifier();
        var markerGroup = identifier.parentSet().markerGroup();
        if (markerGroup.type() != MarkerGroupType.POI) {
            LOGGER.warn("Refusing to add a POI marker for non-POI marker group '{}' (type {})",
                    markerGroup.name(), markerGroup.type());
            return;
        }

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

    private void onError(Throwable throwable) {
        LOGGER.error("Error processing marker action", throwable);
    }

    // Genuine BlueMap disable/re-enable cycle (a real reload, which must resetQueue()/fireReset() to
    // re-diff signCache against the reloaded config) vs. the very first onEnable() a server ever sees: told
    // apart by markerActionQueue.isShutdown(), read here before resetQueue() replaces the reference below.
    // Correct now that isShutdown() reports true only for a genuine shutdown() call rather than also
    // conflating "never started" (finding 63,
    // .scratch/concurrency-pass-2026-09/issues/04-reactivequeue-isshutdown-semantics.md) - this used to need
    // a dedicated disabledSinceLastEnable flag instead, precisely because the old isShutdown() would also
    // report true for a brand-new queue whose executor was never lazily created (e.g. SERVER_STARTING
    // dispatching actions for every migrated/loaded sign before BlueMap is available - process() returns
    // early via shouldRun() without ever creating an executor), which mistook first startup for a reload and
    // discarded every action enqueued during sign load before a single one was ever processed.
    private void onEnable(BlueMapAPI api) {
        // synchronized: BlueMap's listener dispatch is presumed single-threaded today, but the
        // isShutdown() check-then-act (resetQueue()/fireReset()) isn't atomic on its own - a
        // hypothetical concurrent onEnable() call could otherwise also observe isShutdown()==true and
        // double-fire both (finding 44, agent-context/reviews/full-codebase-review_2026-09-07_0900.md).
        // Shares the same lock as processMarkerAction/applySingleAction's marker mutations, which is
        // fine here - onEnable() only runs on a genuine BlueMap enable/reload, never on the hot path.
        synchronized (this) {
            var isGenuineReload = markerActionQueue.isShutdown();
            // Checked (and cleared) even on the very first onEnable(), before isGenuineReload's branch
            // below can replace markerActionQueue with a fresh, never-overflowed one: capacity rejection
            // permanently drops a message (enqueue() has no failure signal back to its caller, and this
            // queue has no retry/replay path of its own), so a large sign count enqueuing every migrated/
            // loaded sign's add action while BlueMap is still unavailable at startup can silently overflow
            // - and since this is the very first enable, isGenuineReload is false and fireReset() would
            // otherwise never run to recover the dropped markers. See the "capacity does not bound..."
            // finding, agent-context/reviews/copilotreview.2026-09-09.md.
            var overflowedBeforeAvailable = markerActionQueue.consumeOverflowSinceLastCheck();

            if (isGenuineReload) {
                if (!lastShutdownConfirmedClean) {
                    LOGGER.warn("Resuming after a BlueMap disable whose shutdown() could not confirm every "
                            + "in-flight marker action had stopped; a straggler task may still race this reset's "
                            + "replay of marker state");
                }

                // Reload config before resetQueue() rather than after: resetQueue() reads
                // ConfigManager.get().getShutdownAwaitSeconds() to build the new queue, and fireReset()
                // (below) is what would otherwise reload config via SignManager.reloadConfig() - too
                // late, since the queue's shutdownAwaitSeconds is fixed at construction. Without this,
                // editing that setting and running /bluemap reload had no effect until a later
                // disable/re-enable cycle. fireReset() still runs its own ConfigManager.reload() right
                // after (harmless - just re-reads the same just-reloaded file) since it also needs to
                // rebuild SignManager's parser/prefix map from the reloaded config.
                ConfigManager.reload();
                resetQueue();

                fireReset();
            } else if (overflowedBeforeAvailable) {
                LOGGER.warn("Marker action queue dropped one or more actions to capacity before BlueMap became "
                        + "available; replaying every currently tracked sign's marker state to recover them");
                fireReset();
            }
        }

        markerActionQueue.process();
    }

    private void onDisable(BlueMapAPI api) {
        lastShutdownConfirmedClean = markerActionQueue.shutdown();
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
                markerSet.setLabel(markerSetIdentifier.markerGroup().name());
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
        // Re-fetch rather than trust a cached BlueMapAPI reference: a disable/re-enable between
        // processMarkerAction's guard check and this call could otherwise operate against a defunct
        // instance (finding 43, agent-context/reviews/full-codebase-review_2026-09-07_0900.md).
        var apiInstance = BlueMapAPI.getInstance();
        if (apiInstance.isEmpty()) {
            LOGGER.debug("BlueMap API not present; skipping map lookup for {}", mapId);
            return Optional.empty();
        }

        var world = apiInstance.get().getWorld(mapId);

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
