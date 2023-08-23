package com.tpwalke2.bluemapsignmarkers.core.bluemap.markers;

import com.tpwalke2.bluemapsignmarkers.core.WorldMap;

public record MarkerSetIdentifier(String worldId, WorldMap map, MarkerType markerType) {}
