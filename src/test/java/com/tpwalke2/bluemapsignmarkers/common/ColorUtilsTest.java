package com.tpwalke2.bluemapsignmarkers.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ColorUtilsTest {

    @Test
    void parseHexParsesEightDigitFormWithAlpha() {
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorUtils.parseHex("#FF0000FF"));
    }

    @Test
    void parseHexParsesMixedCase() {
        assertArrayEquals(new int[]{18, 52, 86, 128}, ColorUtils.parseHex("#12345680"));
    }

    @Test
    void parseHexDefaultsAlphaToOpaqueForSixDigitForm() {
        assertArrayEquals(new int[]{0, 255, 0, 255}, ColorUtils.parseHex("#00FF00"));
    }

    @Test
    void parseHexAcceptsMissingLeadingHash() {
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorUtils.parseHex("FF0000FF"));
    }

    @Test
    void parseHexFallsBackToOpaqueRedForNull() {
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorUtils.parseHex(null));
    }

    @Test
    void parseHexFallsBackToOpaqueRedForWrongLength() {
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorUtils.parseHex("#FFF"));
    }

    @Test
    void parseHexFallsBackToOpaqueRedForNonHexCharacters() {
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorUtils.parseHex("#ZZZZZZ"));
    }
}
