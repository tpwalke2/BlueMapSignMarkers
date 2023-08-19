package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerOperation;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerCollection;

public record UpdatePOIAction(double x, double y, double z, String newLabel, String newDetail) implements MarkerAction {
    @Override
    public MarkerOperation getOperation() {
        return MarkerOperation.UPDATE;
    }

    @Override
    public MarkerCollection getMarkerSet() {
        return MarkerCollection.POI;
    }
}
