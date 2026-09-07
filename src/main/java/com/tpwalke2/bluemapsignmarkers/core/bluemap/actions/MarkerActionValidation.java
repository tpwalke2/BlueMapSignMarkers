package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import java.util.List;
import java.util.Objects;

// Shared validation for MarkerAction constructors - kept as static helpers rather than base-class logic
// per AGENTS.md's "no base-class refactor" scope note for MarkerAction subtypes.
final class MarkerActionValidation {
    private MarkerActionValidation() {
    }

    static <T> List<T> requireMinPoints(List<T> points, int minPoints, String actionName) {
        Objects.requireNonNull(points, actionName + " requires a non-null points list");
        if (points.size() < minPoints) {
            throw new IllegalArgumentException(
                    actionName + " requires at least " + minPoints + " points, got " + points.size());
        }
        return List.copyOf(points);
    }

    // Null-only, not blank: SignEntryHelper.getLabel/getDetail legitimately return "" for a bare-prefix
    // sign (e.g. "[poi]" with no label/detail text), so rejecting blank would break that real, documented
    // case.
    static String requireNonNullField(String value, String fieldName, String actionName) {
        return Objects.requireNonNull(value, actionName + " requires a non-null " + fieldName);
    }
}
