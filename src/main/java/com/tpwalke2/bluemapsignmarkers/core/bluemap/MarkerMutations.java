package com.tpwalke2.bluemapsignmarkers.core.bluemap;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector3d;
import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.common.ColorUtils;
import com.tpwalke2.bluemapsignmarkers.common.HtmlUtils;
import com.tpwalke2.bluemapsignmarkers.common.LogUtils;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.AddMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.RemoveMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.SetMultiPointMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.UpdateMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointGroupThresholds;
import com.tpwalke2.bluemapsignmarkers.core.markers.MultiPointMarkerIdentifier;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// State-free marker-construction logic pulled out of BlueMapAPIConnector (ticket 04,
// .scratch/marker-action-consolidation/issues/04-shrink-bluemapapiconnector-marker-mutations.md) - every
// method here takes only the action being applied and the target MarkerSet's mutable marker Map, with no
// reference to BlueMapAPIConnector's queue/cache/lifecycle state, so mutation logic can be read (and
// changed) independent of dispatch/gating/cache concerns.
final class MarkerMutations {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private MarkerMutations() {
    }

    static void addMarker(AddMarkerAction addAction, Map<String, Marker> markers) {
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

    static void updateMarker(UpdateMarkerAction updateAction, Map<String, Marker> markers) {
        LOGGER.debug("Updating marker...");

        var marker = Optional.ofNullable(markers.get(updateAction.getMarkerIdentifier().getId()));
        if (marker.isEmpty()) return;
        marker.get().setLabel(updateAction.getNewLabel());
        if (marker.get() instanceof POIMarker poiMarker) {
            poiMarker.setDetail(HtmlUtils.toHtmlDetail(updateAction.getNewDetails()));
        }
    }

    static void removeMarker(RemoveMarkerAction removeAction, Map<String, Marker> markers) {
        LOGGER.debug("Removing marker...");
        removeMarkerById(removeAction.getMarkerIdentifier().getId(), markers);
    }

    static void removeMarkerById(String id, Map<String, Marker> markers) {
        markers.remove(id);
    }

    // Dispatches to the right BlueMap marker builder for this SetMultiPointMarkerAction's kind (set by
    // ActionFactory.createSetMultiPointAction on the MultiPointMarkerIdentifier) - a single action type
    // covers line/shape/extrude, so this is the one place that still needs to tell them apart. The shared
    // preamble (min-member gating, color parsing, min/max distance, storing the built marker) lives once
    // here; only the actual per-kind BlueMap builder call differs, since LineMarker.Builder/
    // ShapeMarker.Builder/ExtrudeMarker.Builder share no common builder supertype in bluemap-api.
    static void setMultiPointMarker(SetMultiPointMarkerAction action, Map<String, Marker> markers) {
        var identifier = (MultiPointMarkerIdentifier) action.getMarkerIdentifier();
        var kind = identifier.kind();
        var points = action.getPoints();
        var minMembers = minMembersFor(kind);
        if (minMembers < 0) {
            LOGGER.warn("Unknown multi-point marker kind '{}' for label='{}'",
                    kind, LogUtils.sanitizeForLog(action.getLabel()));
            return;
        }

        if (points.size() < minMembers) {
            // defensive - SignManager should never dispatch below the minimum; warn so a regression is visible.
            LOGGER.warn("Refusing to set {} marker '{}' with fewer than {} points ({})",
                    kind, LogUtils.sanitizeForLog(action.getLabel()), minMembers, points.size());
            return;
        }

        LOGGER.debug("Setting {} marker...", kind);
        var lineColor = toBlueMapColor(ColorUtils.parseHex(action.getLineColor()));
        var markerGroup = identifier.parentSet().markerGroup();

        // Each arm builds and stores its own concrete marker type rather than converging on a shared
        // ObjectMarker-typed variable - bluemap-api is compileOnly (absent from the test runtime
        // classpath), and a shared variable fed by LineMarker/ShapeMarker/ExtrudeMarker across these
        // branches forces the verifier to resolve their common supertype (ObjectMarker) the moment this
        // class is first touched, breaking MarkerMutationsTest's resolveExtrudeHeightRange coverage even
        // though that method never itself references a bluemap-api type.
        switch (kind) {
            case "line" -> {
                var marker = LineMarker.builder()
                        .label(action.getLabel())
                        .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                        .line(new Line(points.stream().map(p -> new Vector3d(p.x(), p.y(), p.z())).toList()))
                        .lineWidth(action.getLineWidth())
                        .lineColor(lineColor)
                        .depthTestEnabled(markerGroup.depthTest())
                        .build();
                marker.setMinDistance(markerGroup.minDistance());
                marker.setMaxDistance(markerGroup.maxDistance());
                markers.put(identifier.getId(), marker);
            }
            case "shape" -> {
                var marker = ShapeMarker.builder()
                        .label(action.getLabel())
                        .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                        // Shape height anchors to the tallest member rather than placement order, so the
                        // polygon always clears the terrain/builds of every sign that defines it.
                        .shape(shapeOf(points), maxY(points))
                        .lineWidth(action.getLineWidth())
                        .lineColor(lineColor)
                        .fillColor(toBlueMapColor(ColorUtils.parseHex(action.getFillColor())))
                        .depthTestEnabled(markerGroup.depthTest())
                        .build();
                marker.setMinDistance(markerGroup.minDistance());
                marker.setMaxDistance(markerGroup.maxDistance());
                markers.put(identifier.getId(), marker);
            }
            case "extrude" -> {
                var heightRange = resolveExtrudeHeightRange(action.getLabel(), points);
                var marker = ExtrudeMarker.builder()
                        .label(action.getLabel())
                        .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                        .shape(shapeOf(points), heightRange.minY(), heightRange.maxY())
                        .lineWidth(action.getLineWidth())
                        .lineColor(lineColor)
                        .fillColor(toBlueMapColor(ColorUtils.parseHex(action.getFillColor())))
                        .depthTestEnabled(markerGroup.depthTest())
                        .build();
                marker.setMinDistance(markerGroup.minDistance());
                marker.setMaxDistance(markerGroup.maxDistance());
                markers.put(identifier.getId(), marker);
            }
            default -> throw new IllegalStateException("Unreachable: unknown kind '" + kind + "' already handled above");
        }
    }

    private static int minMembersFor(String kind) {
        return switch (kind) {
            case "line" -> MultiPointGroupThresholds.LINE_MIN_MEMBERS;
            case "shape" -> MultiPointGroupThresholds.SHAPE_MIN_MEMBERS;
            case "extrude" -> MultiPointGroupThresholds.EXTRUDE_MIN_MEMBERS;
            default -> -1;
        };
    }

    private static Shape shapeOf(List<LinePoint> points) {
        return new Shape(points.stream().map(p -> new Vector2d(p.x(), p.z())).toList());
    }

    private static float maxY(List<LinePoint> points) {
        return (float) points.stream().mapToInt(LinePoint::y).max().orElseThrow();
    }

    // ColorUtils.parseHex returns alpha in the same 0-255 range as r/g/b, but BlueMap's Color(int, int, int,
    // float) constructor takes alpha in 0-1 - passing the raw 0-255 int straight through (as the earlier
    // Color(int, int, int, int alpha-as-int) overload does, treating alpha/255f) is NOT what an int
    // widening to float gives you: it silently widens to e.g. 51.0f instead of dividing by 255, which
    // BlueMap then clamps to fully opaque. Every translucent color (e.g. SHAPE's fillColor default) rendered
    // fully opaque as a result.
    static Color toBlueMapColor(int[] rgba) {
        return new Color(rgba[0], rgba[1], rgba[2], rgba[3] / 255f);
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
}
