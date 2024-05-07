package com.tpwalke2.bluemapsignmarkers.core.markers;

public record MarkerGroupColor(int red, int green, int blue, float alpha) {
    public MarkerGroupColor {
        if (red < 0 || red > 255) {
            throw new IllegalArgumentException("red must be between 0 and 255");
        }
        if (green < 0 || green > 255) {
            throw new IllegalArgumentException("green must be between 0 and 255");
        }
        if (blue < 0 || blue > 255) {
            throw new IllegalArgumentException("blue must be between 0 and 255");
        }
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("alpha must be between 0 and 1");
        }
    }
}
