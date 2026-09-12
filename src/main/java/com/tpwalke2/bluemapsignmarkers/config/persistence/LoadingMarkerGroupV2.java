package com.tpwalke2.bluemapsignmarkers.config.persistence;

import com.google.gson.JsonElement;

import java.util.List;

// Fields prone to a type mismatch in hand-edited JSON (matchType, type, offsetX/offsetY, defaultHidden,
// minDistance/maxDistance, lineWidth, sorting) are read as raw JsonElement rather than their real Java type,
// so a malformed value fails to resolve just that one field (ConfigProvider degrades it to a safe default
// with a warning) instead of throwing out of GSON.fromJson and wiping the entire config back to one default
// [poi] group.
public record LoadingMarkerGroupV2(
        String prefix,
        JsonElement matchType,
        JsonElement type,
        String name,
        String icon,
        JsonElement offsetX,
        JsonElement offsetY,
        JsonElement defaultHidden,
        JsonElement minDistance,
        JsonElement maxDistance,
        JsonElement lineWidth,
        String lineColor,
        String fillColor,
        JsonElement sorting,
        Boolean toggleable,
        Boolean depthTest,
        List<String> cssClasses,
        Boolean allowPlayerColors) {
}
