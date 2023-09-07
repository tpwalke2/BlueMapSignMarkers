package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;
import org.jetbrains.annotations.Nullable;

public record SignLinesParseResult(@Nullable MarkerType markerType, String label, String detail) {}
