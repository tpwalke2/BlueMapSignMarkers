package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerOperation;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerCollection;

public interface MarkerAction {
    MarkerOperation getOperation();
    MarkerCollection getMarkerSet();
}
