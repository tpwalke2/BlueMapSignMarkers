package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerMap;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerOperation;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerSetId;

public interface MarkerAction {
    MarkerMap getMarkerMap();
    MarkerOperation getOperation();
    MarkerSetId getMarkerSetId();
}
