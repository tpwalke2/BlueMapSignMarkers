package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;

public class ParsingContext {
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
