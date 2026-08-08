package com.tpwalke2.bluemapsignmarkers.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogUtilsTest {

    private static final char ESC = 27;

    @Test
    void newlinesAreEscapedRatherThanBreakingTheLogLine() {
        assertEquals("line1\\nline2", LogUtils.sanitizeForLog("line1\nline2"));
    }

    @Test
    void carriageReturnsAreEscapedRatherThanBreakingTheLogLine() {
        assertEquals("line1\\rline2", LogUtils.sanitizeForLog("line1\rline2"));
    }

    @Test
    void windowsLineEndingsAreEscapedAsASingleNewline() {
        assertEquals("line1\\nline2", LogUtils.sanitizeForLog("line1\r\nline2"));
    }

    @Test
    void ansiCsiEscapeSequencesAreStripped() {
        var redText = ESC + "[31mDANGER" + ESC + "[0m";

        assertEquals("DANGER", LogUtils.sanitizeForLog(redText));
    }

    @Test
    void ansiPrivateModeCsiSequencesAreStripped() {
        var hideCursorText = "hidden cursor: " + ESC + "[?25l" + "done";

        assertEquals("hidden cursor: done", LogUtils.sanitizeForLog(hideCursorText));
    }

    @Test
    void ordinaryBracketedTextIsLeftUntouched() {
        assertEquals("[poi] Town Hall", LogUtils.sanitizeForLog("[poi] Town Hall"));
    }
}
