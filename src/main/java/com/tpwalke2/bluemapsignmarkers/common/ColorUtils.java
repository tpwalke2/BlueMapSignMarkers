package com.tpwalke2.bluemapsignmarkers.common;

public class ColorUtils {
    private static final int[] DEFAULT_COLOR = {255, 0, 0, 255};

    private ColorUtils() {}

    // Accepts "#RRGGBB" or "#RRGGBBAA" (leading '#' optional). Falls back to opaque red on any
    // malformed input rather than throwing - a bad config value must not crash the server.
    public static int[] parseHex(String hex) {
        if (hex == null) return defaultColor();

        var stripped = hex.startsWith("#") ? hex.substring(1) : hex;

        if (stripped.length() != 6 && stripped.length() != 8) return defaultColor();

        try {
            var r = Integer.parseInt(stripped.substring(0, 2), 16);
            var g = Integer.parseInt(stripped.substring(2, 4), 16);
            var b = Integer.parseInt(stripped.substring(4, 6), 16);
            var a = stripped.length() == 8 ? Integer.parseInt(stripped.substring(6, 8), 16) : 255;
            return new int[]{r, g, b, a};
        } catch (NumberFormatException e) {
            return defaultColor();
        }
    }

    private static int[] defaultColor() {
        return DEFAULT_COLOR.clone();
    }

    // True for "#RRGGBB"/"#RRGGBBAA" (leading '#' optional), same shape parseHex accepts.
    public static boolean isValidHex(String hex) {
        if (hex == null) return false;

        var stripped = hex.startsWith("#") ? hex.substring(1) : hex;

        if (stripped.length() != 6 && stripped.length() != 8) return false;

        try {
            Integer.parseInt(stripped.substring(0, 2), 16);
            Integer.parseInt(stripped.substring(2, 4), 16);
            Integer.parseInt(stripped.substring(4, 6), 16);
            if (stripped.length() == 8) Integer.parseInt(stripped.substring(6, 8), 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
