package com.tpwalke2.bluemapsignmarkers.core.bounds;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// Answers "is (x, y, z) inside this BlueMap map's render bounds?" by reading and evaluating the
// map's own config/bluemap/maps/<id>.conf file, since BlueMap's API exposes no bounds accessor.
// Fails open (unbounded) on any missing/unreadable/unparseable config or unmatched map id -
// dependency-free and stateless per call; caching per real map id is the caller's responsibility.
public class RenderMaskEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private static final String CONF_EXTENSION = ".conf";
    private static final Pattern RENDER_MASK_KEY = Pattern.compile("render-mask\\s*:");
    private static final Pattern SHAPE_KEY = Pattern.compile("shape\\s*:");
    private static final Pattern TYPE_PATTERN = Pattern.compile("type\\s*:\\s*\"?([A-Za-z]+)\"?");
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("([A-Za-z][\\w-]*)\\s*:\\s*(-?\\d+(?:\\.\\d+)?|true|false)");

    private RenderMaskEvaluator() {}

    public static boolean isInsideRenderBounds(String mapId, Path mapsConfigDir, int x, int y, int z) {
        return load(mapId, mapsConfigDir).contains(x, y, z);
    }

    // A parsed, ready-to-query render-mask for one map. Callers that test many points against the
    // same map (e.g. BlueMapAPIConnector, once per real BlueMapMap id) should load() once and reuse
    // the result instead of re-reading/re-parsing the .conf file on every point test.
    public static final class RenderMask {
        private final List<RenderMaskShape> shapes;

        private RenderMask(List<RenderMaskShape> shapes) {
            this.shapes = shapes;
        }

        public boolean contains(int x, int y, int z) {
            return evaluate(shapes, x, y, z);
        }
    }

    public static RenderMask load(String mapId, Path mapsConfigDir) {
        return new RenderMask(loadRenderMask(mapId, mapsConfigDir));
    }

    // BlueMap's own CombinedMask algorithm: walk the entry list from last entry to first; the
    // first entry in that reverse walk that contains the point decides the verdict via its own
    // subtract flag, regardless of shape. If no entry matches, the verdict is "included" only
    // when the list is empty, or when the first entry (in list order) is a subtract - which
    // implicitly inserts an "include everything" layer beneath it.
    private static boolean evaluate(List<RenderMaskShape> shapes, int x, int y, int z) {
        if (shapes.isEmpty()) {
            return true;
        }

        for (var i = shapes.size() - 1; i >= 0; i--) {
            var shape = shapes.get(i);
            if (shape.contains(x, y, z)) {
                return !shape.subtract();
            }
        }

        return shapes.get(0).subtract();
    }

    private static List<RenderMaskShape> loadRenderMask(String mapId, Path mapsConfigDir) {
        var configFile = findConfigFile(mapId, mapsConfigDir);
        if (configFile == null) {
            return List.of();
        }

        String content;
        try {
            content = Files.readString(configFile);
        } catch (IOException e) {
            LOGGER.warn(
                    "Failed to read map config {} while evaluating render bounds for map '{}'; treating its "
                            + "bounds as unbounded", configFile, mapId, e);
            return List.of();
        }

        try {
            return parseRenderMask(content);
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "Failed to parse render-mask in {} while evaluating render bounds for map '{}'; treating its "
                            + "bounds as unbounded", configFile, mapId, e);
            return List.of();
        }
    }

    // Mirrors BlueMapConfigManager.sanitiseMapId (\W -> _) so an oddly-named config file still
    // resolves to the same id BlueMap itself computed, rather than assuming literal identity.
    private static Path findConfigFile(String mapId, Path mapsConfigDir) {
        if (!Files.isDirectory(mapsConfigDir)) {
            return null;
        }

        try (var files = Files.list(mapsConfigDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(CONF_EXTENSION))
                    .filter(path -> sanitizeMapId(stripConfExtension(path.getFileName().toString())).equals(mapId))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            LOGGER.warn(
                    "Failed to list map config directory {} while evaluating render bounds for map '{}'; "
                            + "treating its bounds as unbounded", mapsConfigDir, mapId, e);
            return null;
        }
    }

    private static String stripConfExtension(String fileName) {
        return fileName.substring(0, fileName.length() - CONF_EXTENSION.length());
    }

    private static String sanitizeMapId(String id) {
        return id.replaceAll("\\W", "_");
    }

    private static List<RenderMaskShape> parseRenderMask(String content) {
        var stripped = stripComments(content);

        var keyMatcher = RENDER_MASK_KEY.matcher(stripped);
        if (!keyMatcher.find()) {
            return List.of();
        }

        var arrayStart = stripped.indexOf('[', keyMatcher.end());
        if (arrayStart < 0) {
            throw new IllegalStateException("render-mask key found but no opening '[' after it");
        }

        var arrayEnd = findMatchingBracket(stripped, arrayStart, '[', ']');
        var arrayContent = stripped.substring(arrayStart + 1, arrayEnd);

        var shapes = new ArrayList<RenderMaskShape>();
        for (var chunk : splitObjectChunks(arrayContent)) {
            shapes.add(parseEntry(chunk));
        }
        return shapes;
    }

    private static String stripComments(String content) {
        var result = new StringBuilder(content.length());
        for (var line : content.split("\n", -1)) {
            var hashIndex = line.indexOf('#');
            result.append(hashIndex >= 0 ? line.substring(0, hashIndex) : line).append('\n');
        }
        return result.toString();
    }

    private static int findMatchingBracket(String s, int openIndex, char open, char close) {
        var depth = 0;
        for (var i = openIndex; i < s.length(); i++) {
            var c = s.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Unbalanced '" + open + "'/'" + close + "' in render-mask array");
    }

    private static List<String> splitObjectChunks(String arrayContent) {
        var chunks = new ArrayList<String>();
        var depth = 0;
        var start = -1;
        for (var i = 0; i < arrayContent.length(); i++) {
            var c = arrayContent.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i + 1;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    chunks.add(arrayContent.substring(start, i));
                } else if (depth < 0) {
                    throw new IllegalStateException("Unbalanced '{'/'}' in render-mask array");
                }
            }
        }
        if (depth != 0) {
            throw new IllegalStateException("Unbalanced '{'/'}' in render-mask array");
        }
        return chunks;
    }

    // Dispatches on the entry's "type" field (defaulting to "box" when absent, matching today's
    // implicit behavior) to the matching shape parser. An unrecognized type throws, which the
    // caller (loadRenderMask) catches and logs, failing the whole map open.
    private static RenderMaskShape parseEntry(String chunk) {
        var type = extractType(chunk);
        var fields = extractFields(chunk);
        var subtract = Boolean.parseBoolean(fields.getOrDefault("subtract", "false"));

        return switch (type) {
            case "box" -> parseBox(fields, subtract);
            case "circle" -> parseCircle(fields, subtract);
            case "ellipse" -> parseEllipse(fields, subtract);
            case "polygon" -> parsePolygon(chunk, fields, subtract);
            default -> throw new IllegalStateException("Unrecognized render-mask entry type '" + type + "'");
        };
    }

    private static String extractType(String chunk) {
        var matcher = TYPE_PATTERN.matcher(chunk);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "box";
    }

    private static Map<String, String> extractFields(String chunk) {
        var fields = new HashMap<String, String>();
        var matcher = FIELD_PATTERN.matcher(chunk);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        return fields;
    }

    private static int intField(Map<String, String> fields, String key, int defaultValue) {
        var value = fields.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static double requiredDoubleField(Map<String, String> fields, String key) {
        var value = fields.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required render-mask field '" + key + "'");
        }
        return Double.parseDouble(value);
    }

    private static RenderMaskBox parseBox(Map<String, String> fields, boolean subtract) {
        return new RenderMaskBox(
                intField(fields, "min-x", Integer.MIN_VALUE),
                intField(fields, "max-x", Integer.MAX_VALUE),
                intField(fields, "min-y", Integer.MIN_VALUE),
                intField(fields, "max-y", Integer.MAX_VALUE),
                intField(fields, "min-z", Integer.MIN_VALUE),
                intField(fields, "max-z", Integer.MAX_VALUE),
                subtract);
    }

    private static RenderMaskCircle parseCircle(Map<String, String> fields, boolean subtract) {
        return new RenderMaskCircle(
                requiredDoubleField(fields, "center-x"),
                requiredDoubleField(fields, "center-z"),
                requiredDoubleField(fields, "radius"),
                intField(fields, "min-y", Integer.MIN_VALUE),
                intField(fields, "max-y", Integer.MAX_VALUE),
                subtract);
    }

    private static RenderMaskEllipse parseEllipse(Map<String, String> fields, boolean subtract) {
        return new RenderMaskEllipse(
                requiredDoubleField(fields, "center-x"),
                requiredDoubleField(fields, "center-z"),
                requiredDoubleField(fields, "radius-x"),
                requiredDoubleField(fields, "radius-z"),
                intField(fields, "min-y", Integer.MIN_VALUE),
                intField(fields, "max-y", Integer.MAX_VALUE),
                subtract);
    }

    private static RenderMaskPolygon parsePolygon(String chunk, Map<String, String> fields, boolean subtract) {
        var points = parsePolygonPoints(chunk);
        if (points.size() < 3) {
            throw new IllegalStateException("polygon render-mask entry requires at least 3 points");
        }

        return new RenderMaskPolygon(
                points,
                intField(fields, "min-y", Integer.MIN_VALUE),
                intField(fields, "max-y", Integer.MAX_VALUE),
                subtract);
    }

    private static List<RenderMaskPoint> parsePolygonPoints(String chunk) {
        var shapeKeyMatcher = SHAPE_KEY.matcher(chunk);
        if (!shapeKeyMatcher.find()) {
            throw new IllegalStateException("polygon render-mask entry missing 'shape' array");
        }

        var arrayStart = chunk.indexOf('[', shapeKeyMatcher.end());
        if (arrayStart < 0) {
            throw new IllegalStateException("polygon 'shape' key found but no opening '[' after it");
        }

        var arrayEnd = findMatchingBracket(chunk, arrayStart, '[', ']');
        var arrayContent = chunk.substring(arrayStart + 1, arrayEnd);

        var points = new ArrayList<RenderMaskPoint>();
        for (var pointChunk : splitObjectChunks(arrayContent)) {
            var pointFields = extractFields(pointChunk);
            points.add(new RenderMaskPoint(
                    requiredDoubleField(pointFields, "x"), requiredDoubleField(pointFields, "z")));
        }
        return points;
    }
}
