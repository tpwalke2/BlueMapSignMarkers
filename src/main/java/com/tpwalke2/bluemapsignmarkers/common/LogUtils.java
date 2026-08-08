package com.tpwalke2.bluemapsignmarkers.common;

import java.util.regex.Pattern;

public class LogUtils {

    private static final char ESC = 27;

    // Matches ANSI CSI escape sequences (e.g. color/cursor codes, private-mode sequences like "[?25l")
    // a player could embed in sign text to corrupt or spoof terminal/log-viewer output. Follows the
    // ECMA-48 CSI grammar: ESC '[' then parameter bytes (0x30-0x3F), intermediate bytes (0x20-0x2F),
    // then a single final byte (0x40-0x7E) - rather than the narrower "digits/semicolons then a letter"
    // shape, which misses sequences using ':', '<', '=', '>', or '?' parameter bytes. Built from the ESC
    // char code point rather than a literal so the pattern text itself can't be mistaken for one of the
    // sequences it strips.
    private static final Pattern ANSI_ESCAPE =
            Pattern.compile(Pattern.quote(String.valueOf(ESC)) + "\\[[0-9:;<=>?]*[ -/]*[@-~]");

    private LogUtils() {
    }

    public static String sanitizeForLog(String text) {
        return ANSI_ESCAPE.matcher(text)
                .replaceAll("")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
