package com.tpwalke2.bluemapsignmarkers.core.markers;

import java.util.List;
import java.util.Objects;

public record MarkerGroup(
        String prefix,
        MarkerGroupMatchType matchType,
        MarkerGroupType type,
        String name,
        String icon,
        int offsetX,
        int offsetY,
        boolean defaultHidden,
        double minDistance,
        double maxDistance,
        int lineWidth,
        String lineColor,
        String fillColor,
        int sorting,
        boolean toggleable,
        boolean depthTest,
        List<String> cssClasses,
        boolean allowPlayerColors) {
    // icon is genuinely allowed to be null (POI groups without an icon, LINE/SHAPE/EXTRUDE groups where
    // it's ignored - see ConfigProvider.warnOnTypeFieldMismatches). prefix is also allowed to be null -
    // SignLinesParser deliberately tolerates a marker group with no prefix configured, logging a warning
    // and ignoring that group rather than crashing the server (see SignLinesParser.hasValidPrefix). Every
    // other field is load-bearing wherever a MarkerGroup is actually used, so a null there is rejected
    // here instead of surfacing as an NPE deep in whichever code path first dereferences it. cssClasses is
    // defensively copied since ConfigProvider currently always passes an immutable list, but nothing else
    // enforces that.
    public MarkerGroup {
        Objects.requireNonNull(matchType, "matchType");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lineColor, "lineColor");
        Objects.requireNonNull(fillColor, "fillColor");
        cssClasses = List.copyOf(Objects.requireNonNull(cssClasses, "cssClasses"));
    }

    public static final MarkerGroup DEFAULT_POI_GROUP = new MarkerGroup(
            "[poi]",
            MarkerGroupMatchType.STARTS_WITH,
            MarkerGroupType.POI,
            "Points of Interest",
            null,
            0,
            0,
            false,
            0.0,
            10000000.0,
            2,
            "#FF0000FF",
            "#FF000033",
            0,
            true,
            true,
            List.of(),
            false);
}
