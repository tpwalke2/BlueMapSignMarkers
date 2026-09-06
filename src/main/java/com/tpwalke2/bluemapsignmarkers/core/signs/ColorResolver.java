package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.common.ColorUtils;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Resolves a LINE/SHAPE/EXTRUDE marker's rendered colour from its current membership, same shape as
// LineGroupResolver/ShapeGroupResolver/ExtrudeGroupResolver (plain Java, no Minecraft/Fabric/BlueMap
// types - directly unit-testable). See agent-context/plans/player-marker-colors/spec.md "Conflict
// resolution".
//
// The dye-to-RGB table below duplicates net.minecraft.world.item.DyeColor.getTextureDiffuseColor()'s
// values (read from the decompiled DyeColor source for the Minecraft version pinned in
// gradle.properties) rather than depending on that (Minecraft) type directly, keeping this class plain
// Java.
public class ColorResolver {
    private ColorResolver() {
    }

    public record ResolvedColors(String lineColor, String fillColor) {
    }

    private static final Map<String, int[]> DYE_RGB = Map.ofEntries(
            Map.entry("WHITE", new int[]{0xF9, 0xFF, 0xFE}),
            Map.entry("ORANGE", new int[]{0xF9, 0x80, 0x1D}),
            Map.entry("MAGENTA", new int[]{0xC7, 0x4E, 0xBD}),
            Map.entry("LIGHT_BLUE", new int[]{0x3A, 0xB3, 0xDA}),
            Map.entry("YELLOW", new int[]{0xFE, 0xD8, 0x3D}),
            Map.entry("LIME", new int[]{0x80, 0xC7, 0x1F}),
            Map.entry("PINK", new int[]{0xF3, 0x8B, 0xAA}),
            Map.entry("GRAY", new int[]{0x47, 0x4F, 0x52}),
            Map.entry("LIGHT_GRAY", new int[]{0x9D, 0x9D, 0x97}),
            Map.entry("CYAN", new int[]{0x16, 0x9C, 0x9C}),
            Map.entry("PURPLE", new int[]{0x89, 0x32, 0xB8}),
            Map.entry("BLUE", new int[]{0x3C, 0x44, 0xAA}),
            Map.entry("BROWN", new int[]{0x83, 0x54, 0x32}),
            Map.entry("GREEN", new int[]{0x5E, 0x7C, 0x16}),
            Map.entry("RED", new int[]{0xB0, 0x2E, 0x26}),
            Map.entry("BLACK", new int[]{0x1D, 0x1D, 0x21}));

    // Sorts by createdAtMillis itself rather than trusting members to already be ordered - callers
    // (LineGroupResolver/ShapeGroupResolver/ExtrudeGroupResolver.members) happen to return
    // earliest-placed-first today, but the "earliest-placed wins" rule shouldn't be silently contingent
    // on that convention holding at every call site.
    public static ResolvedColors resolve(List<SignEntry> members, MarkerGroup group) {
        var unchanged = new ResolvedColors(group.lineColor(), group.fillColor());
        if (!group.allowPlayerColors()) return unchanged;

        var winnerDye = members.stream()
                .sorted(Comparator.comparingLong(SignEntry::createdAtMillis))
                .map(SignEntryHelper::getDye)
                .filter(dye -> !SignEntryHelper.UNDYED_DYE.equals(dye))
                .findFirst()
                .orElse(null);
        if (winnerDye == null) return unchanged;

        var rgb = DYE_RGB.get(winnerDye);
        if (rgb == null) return unchanged;

        var lineAlpha = ColorUtils.parseHex(group.lineColor())[3];
        var fillAlpha = ColorUtils.parseHex(group.fillColor())[3];
        return new ResolvedColors(
                ColorUtils.toHex(rgb[0], rgb[1], rgb[2], lineAlpha),
                ColorUtils.toHex(rgb[0], rgb[1], rgb[2], fillAlpha));
    }
}
