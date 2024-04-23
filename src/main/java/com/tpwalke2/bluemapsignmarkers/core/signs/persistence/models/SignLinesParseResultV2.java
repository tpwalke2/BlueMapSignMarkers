package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models;

import org.jetbrains.annotations.Nullable;

public record SignLinesParseResultV2(@Nullable MarkerTypeV2 markerType, String label, String detail) {
}
