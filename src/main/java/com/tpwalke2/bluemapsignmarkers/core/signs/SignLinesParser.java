package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;

public class SignLinesParser {
    private enum ParseStates {
        START,
        HAS_MARKER_TYPE,
        INVALID
    }

    private SignLinesParser() {
    }

    private static class ParsingContext {
        private MarkerType markerType;
        private String label;
        private final StringBuilder detailBuilder;

        public ParsingContext() {
            this.markerType = null;
            this.label = "";
            this.detailBuilder = new StringBuilder();
        }

        public void setMarkerType(MarkerType markerType) {
            this.markerType = markerType;
        }

        public MarkerType getMarkerType() {
            return this.markerType;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }

        public void appendDetail(String detail) {
            this.detailBuilder.append(detail).append("\n");
        }

        public SignLinesParseResult buildResult() {
            return new SignLinesParseResult(this.markerType, this.label, this.detailBuilder.toString().trim());
        }
    }

    public static SignLinesParseResult parse(String[] lines) {
        var state = ParseStates.START;

        var context = new ParsingContext();

        for (String line : lines) {
            line = line.trim();
            if (state == ParseStates.START) {
                state = processStartState(line, context);
            } else if (state == ParseStates.HAS_MARKER_TYPE) {
                processHasMarkerType(line, context);
            }
        }

        return state == ParseStates.INVALID
                ? new SignLinesParseResult(null, "", "")
                : context.buildResult();
    }

    private static ParseStates processStartState(String line, ParsingContext context) {
        if (line.isEmpty()) {
            return ParseStates.START;
        }

        context.setMarkerType(getMarkerType(line));
        if (context.getMarkerType() == null) {
            return ParseStates.INVALID;
        }

        context.setLabel(line.substring(context.getMarkerType().prefix.length()).trim());
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

    private static MarkerType getMarkerType(String line) {
        for (MarkerType markerType : MarkerType.values()) {
            if (line.startsWith(markerType.prefix)) {
                return markerType;
            }
        }

        return null;
    }
}
