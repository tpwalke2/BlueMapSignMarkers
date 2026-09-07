package com.tpwalke2.bluemapsignmarkers.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void isValidHexAcceptsSixAndEightDigitForms() {
        assertTrue(ColorUtils.isValidHex("#00FF00"));
        assertTrue(ColorUtils.isValidHex("#FF0000FF"));
        assertTrue(ColorUtils.isValidHex("FF0000FF"));
    }

    @Test
    void isValidHexRejectsNull() {
        assertFalse(ColorUtils.isValidHex(null));
    }

    @Test
    void isValidHexRejectsWrongLength() {
        assertFalse(ColorUtils.isValidHex("#FFF"));
    }

    @Test
    void isValidHexRejectsNonHexCharacters() {
        assertFalse(ColorUtils.isValidHex("#ZZZZZZ"));
    }

    // "-1-1-1-1" is length 8 (passes the length check) and each 2-char component parses as decimal -1
    // under radix 16 with no NumberFormatException, so a naive Integer.parseInt-only check wrongly accepts it.
    @Test
    void isValidHexRejectsAMinusSignInAComponent() {
        assertFalse(ColorUtils.isValidHex("#-1-1-1-1"));
    }

    @Test
    void parseHexFallsBackToOpaqueRedForAMinusSignInAComponent() {
        assertArrayEquals(new int[]{255, 0, 0, 255}, ColorUtils.parseHex("#-1-1-1-1"));
    }
}
