package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerMap;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerOperation;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerSetId;

public record RemovePOIAction(
        double x,
        double y,
        double z,
        MarkerMap markerMap) implements MarkerAction  {
    @Override
    public MarkerMap getMarkerMap() {
        return this.markerMap();
    }

    @Override
    public MarkerOperation getOperation() {
        return MarkerOperation.REMOVE;
    }

    @Override
    public MarkerSetId getMarkerSetId() {
        return MarkerSetId.POI;
    }
}
