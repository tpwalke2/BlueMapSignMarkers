package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;

import java.util.Map;

public class SignLinesParser {
    private enum ParseStates {
        START,
        HAS_MARKER_TYPE,
        INVALID
    }

    private final Map<MarkerType, String> prefixMap;

    public SignLinesParser(Map<MarkerType, String> prefixMap) {
        this.prefixMap = prefixMap;
    }

    public SignLinesParseResult parse(String[] lines) {
        var state = ParseStates.START;

        var context = new ParsingContext();

        for (String line : lines) {
            line = line.trim();
            if (state == ParseStates.START) {
                state = processStartState(line, context, prefixMap);
            } else if (state == ParseStates.HAS_MARKER_TYPE) {
                processHasMarkerType(line, context);
            }
        }

        return state == ParseStates.INVALID
                ? new SignLinesParseResult(null, "", "")
                : context.buildResult();
    }

    private static ParseStates processStartState(
            String line,
            ParsingContext context,
            Map<MarkerType, String> prefixMap) {
        if (line.isEmpty()) {
            return ParseStates.START;
        }

        context.setMarkerType(getMarkerType(line, prefixMap));
        if (context.getMarkerType() == null) {
            return ParseStates.INVALID;
        }

        context.setLabel(line.substring(prefixMap.get(context.getMarkerType()).length()).trim());
        if (!context.getLabel().isEmpty()) {
            context.appendDetail(context.getLabel());
        }
        return ParseStates.HAS_MARKER_TYPE;
    }

    private static void processHasMarkerType(String line, ParsingContext context) {
        if (line.isEmpty()) {
            return;
        }

        if (context.getLabel().isEmpty()) {
            context.setLabel(line);
        }

        context.appendDetail(line);
    }

    private static MarkerType getMarkerType(String line, Map<MarkerType, String> prefixMap) {
        for (MarkerType markerType : prefixMap.keySet()) {
            if (line.startsWith(prefixMap.get(markerType))) {
                return markerType;
            }
        }

        return null;
    }
}
