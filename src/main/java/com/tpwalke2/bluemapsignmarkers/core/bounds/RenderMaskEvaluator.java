package com.tpwalke2.bluemapsignmarkers.core.bounds;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Answers "is (x, y, z) inside this BlueMap map's render bounds?" by reading and evaluating the
// map's own config/bluemap/maps/<id>.conf file, since BlueMap's API exposes no bounds accessor.
// Fails open (unbounded) on any missing/unreadable/unparseable config or unmatched map id -
// dependency-free and stateless per call; caching per real map id is the caller's responsibility.
public class RenderMaskEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private static final String CONF_EXTENSION = ".conf";
    private static final Pattern RENDER_MASK_KEY = Pattern.compile("render-mask\\s*:");
    private static final Pattern FIELD_PATTERN = Pattern.compile("([A-Za-z][\\w-]*)\\s*:\\s*(-?\\d+|true|false)");

    private RenderMaskEvaluator() {}

    public static boolean isInsideRenderBounds(String mapId, Path mapsConfigDir, int x, int y, int z) {
        var boxes = loadRenderMask(mapId, mapsConfigDir);
        return evaluate(boxes, x, y, z);
    }

    // BlueMap's own CombinedMask algorithm: walk the box list from last entry to first; the first
    // box in that reverse walk that contains the point decides the verdict via its own subtract
    // flag. If no box matches, the verdict is "included" only when the list is empty, or when the
    // first entry (in list order) is a subtract - which implicitly inserts an "include everything"
    // layer beneath it.
    private static boolean evaluate(List<RenderMaskBox> boxes, int x, int y, int z) {
        if (boxes.isEmpty()) {
            return true;
        }

        for (var i = boxes.size() - 1; i >= 0; i--) {
            var box = boxes.get(i);
            if (box.contains(x, y, z)) {
                return !box.subtract();
            }
        }

        return boxes.get(0).subtract();
    }

    private static List<RenderMaskBox> loadRenderMask(String mapId, Path mapsConfigDir) {
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

    private static List<RenderMaskBox> parseRenderMask(String content) {
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

        var boxes = new ArrayList<RenderMaskBox>();
        for (var chunk : splitObjectChunks(arrayContent)) {
            boxes.add(parseBox(chunk));
        }
        return boxes;
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

    private static RenderMaskBox parseBox(String chunk) {
        var minX = Integer.MIN_VALUE;
        var maxX = Integer.MAX_VALUE;
        var minY = Integer.MIN_VALUE;
        var maxY = Integer.MAX_VALUE;
        var minZ = Integer.MIN_VALUE;
        var maxZ = Integer.MAX_VALUE;
        var subtract = false;

        var matcher = FIELD_PATTERN.matcher(chunk);
        while (matcher.find()) {
            var value = matcher.group(2);
            switch (matcher.group(1)) {
                case "min-x" -> minX = Integer.parseInt(value);
                case "max-x" -> maxX = Integer.parseInt(value);
                case "min-y" -> minY = Integer.parseInt(value);
                case "max-y" -> maxY = Integer.parseInt(value);
                case "min-z" -> minZ = Integer.parseInt(value);
                case "max-z" -> maxZ = Integer.parseInt(value);
                case "subtract" -> subtract = Boolean.parseBoolean(value);
                default -> { /* unknown key within the block; ignore */ }
            }
        }

        return new RenderMaskBox(minX, maxX, minY, maxY, minZ, maxZ, subtract);
    }
}
