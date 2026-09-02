package com.tpwalke2.bluemapsignmarkers.core.signs;

import java.util.Collection;
import java.util.List;

// Mirrors ShapeGroupResolver/LineGroupResolver (see docs/adr/0002-shape-duplicates-line-pattern.md) - an
// EXTRUDE's members are resolved identically to a SHAPE's (same parent map/prefix/label filter, same
// createdAtMillis-then-position ordering), so this delegates to the shared implementation rather than
// duplicating the stream pipeline.
public class ExtrudeGroupResolver {
    private ExtrudeGroupResolver() {
    }

    public static List<SignEntry> members(Collection<SignEntry> allSigns, String parentMap, String prefix, String label) {
        return LineGroupResolver.members(allSigns, parentMap, prefix, label);
    }
}
