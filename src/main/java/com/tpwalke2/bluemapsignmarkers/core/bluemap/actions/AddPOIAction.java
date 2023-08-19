package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerOperation;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerCollection;

public record AddPOIAction(double x, double y, double z, String label, String detail) implements MarkerAction {
    @Override
    public MarkerOperation getOperation() {
        return MarkerOperation.ADD;
    }

    @Override
    public MarkerCollection getMarkerSet() {
        return MarkerCollection.POI;
    }
}