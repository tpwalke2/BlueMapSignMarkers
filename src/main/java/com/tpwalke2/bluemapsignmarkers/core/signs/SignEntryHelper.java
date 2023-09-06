package com.tpwalke2.bluemapsignmarkers.core.signs;

public class SignEntryHelper {
    private SignEntryHelper() {}

    private static boolean startsWithSentinel(String[] lines, String sentinel) {
        for (var line : lines) {
            if (line.isBlank()) continue;
            return line.trim().startsWith(sentinel);
        }

        return false;
    }

    public static boolean hasSentinel(SignEntry signEntry, String sentinel) {
        return startsWithSentinel(signEntry.frontTextLines(), sentinel)
                || startsWithSentinel(signEntry.backTextLines(), sentinel);
    }

    private static String getFirstPhraseAfterSentinel(String[] lines, String sentinel) {
        var phraseFoundOnPreviousLine = false;
        for (var line : lines) {
            var trimmed = line.trim();
            if (phraseFoundOnPreviousLine) {
                return trimmed;
            }

            if (trimmed.startsWith(sentinel)) {
                var phrase = trimmed.substring(sentinel.length()).trim();

                if (phrase.isBlank()) {
                    phraseFoundOnPreviousLine = true;
                    continue;
                }

                return phrase;
            }
        }

        return "";
    }

    public static String getLabel(SignEntry signEntry, String sentinel) {
        var frontLabel = getFirstPhraseAfterSentinel(signEntry.frontTextLines(), sentinel);
        var backLabel = getFirstPhraseAfterSentinel(signEntry.backTextLines(), sentinel);

        if (!frontLabel.isBlank()) {
            return frontLabel;
        }

        return backLabel.isBlank() ? "" : backLabel;
    }

    private static String getDetail(String[] lines, String sentinel) {
        var detail = new StringBuilder();
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.startsWith(sentinel)) {
                detail.append(trimmed.substring(sentinel.length()).trim()).append("\n");
            } else {
                detail.append(trimmed).append("\n");
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
