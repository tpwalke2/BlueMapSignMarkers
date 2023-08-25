package com.tpwalke2.bluemapsignmarkers.core;

public class SignEntryHelper {
    private SignEntryHelper() {}

    private static boolean startsWithSentinel(String[] lines, String sentinel) {
        for (var line : lines) {
            if (line.isBlank()) continue;
            return line.trim().startsWith(sentinel);
        }

        return false;
    }

    public static boolean isPOIMarker(SignEntry signEntry, String sentinel) {
        return startsWithSentinel(signEntry.frontTextLines(), sentinel)
                || startsWithSentinel(signEntry.backTextLines(), sentinel);
    }

    private static String getFirstPhraseAfterSentinel(String[] lines, String sentinel) {
        for (var line : lines) {
            if (line.trim().startsWith(sentinel)) {
                return line.trim().substring(sentinel.length()).trim();
            }
        }

        return "";
    }

    public static String getLabel(SignEntry signEntry, String sentinel) {
        var frontLabel = getFirstPhraseAfterSentinel(signEntry.frontTextLines(), sentinel);
        var backLabel = getFirstPhraseAfterSentinel(signEntry.backTextLines(), sentinel);

        return frontLabel.isBlank() ? backLabel : frontLabel;
    }

    private static String getDetail(String[] lines, String sentinel) {
        var detail = new StringBuilder();
        for (var line : lines) {
            if (line.trim().startsWith(sentinel)) {
                detail.append(line.trim().substring(sentinel.length()).trim()).append("\n");
            } else {
                detail.append(line.trim()).append("\n");
            }
        }

        return detail.toString();
    }

    public static String getDetail(SignEntry signEntry, String sentinel) {
        var frontDetail = getDetail(signEntry.frontTextLines(), sentinel);
        var backDetail = getDetail(signEntry.backTextLines(), sentinel);

        return String.format("FRONT: %s%nBACK: %s", frontDetail, backDetail);
    }
}
