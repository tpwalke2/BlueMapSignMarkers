package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerOperation;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerCollection;

public record RemovePOIAction(double x, double y, double z) implements MarkerAction {
    @Override
    public MarkerOperation getOperation() {
        return null;
    }

    @Override
    public MarkerCollection getMarkerSet() {
        return null;
    }
}
