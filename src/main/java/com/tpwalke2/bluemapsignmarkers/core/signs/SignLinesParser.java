package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SignLinesParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    // Java's trim() only strips chars <= U+0020; NBSP/zero-width-space/ideographic-space are pasteable
    // from clipboards but otherwise invisible, so without this a line made of only one of them looks
    // "non-blank" to the parser and permanently derails it into INVALID.
    private static final Pattern INVISIBLE_WHITESPACE_AT_EDGES =
            Pattern.compile("^[\\s\\u00A0\\u200B\\u3000]+|[\\s\\u00A0\\u200B\\u3000]+$");

    // Vanilla sign editing fits far fewer characters per line than this - a generous cap, not a
    // mirror of any exact client-side constant. A REGEX marker group's prefix pattern runs against
    // raw, player-controlled sign text via line.matches(...); an admin-configured pattern can hit
    // catastrophic backtracking on a long enough adversarial line (e.g. a sign edited via
    // commands/NBT to bypass the client's width limit). Capping length here bounds that blowup for
    // the realistic case (a short, accidentally-pathological prefix pattern) without eliminating it
    // for a deliberately-adversarial admin-authored pattern (e.g. several unbounded capturing groups
    // in a row), which is an accepted residual risk - see
    // docs/adr/0005-redos-mitigation-length-cap-only.md.
    private static final int MAX_LINE_LENGTH = 100;

    private enum ParseStates {
        START,
        HAS_MARKER_TYPE,
        INVALID
    }

    private final List<MarkerGroup> markerGroups;

    public SignLinesParser(List<MarkerGroup> markerGroups) {
        this.markerGroups = markerGroups.stream()
                .filter(SignLinesParser::hasValidPrefix)
                .toList();
    }

    private static boolean hasValidPrefix(MarkerGroup markerGroup) {
        if (markerGroup.prefix() == null) {
            LOGGER.warn("Marker group '{}' has no prefix configured, it will be ignored.", markerGroup.name());
            return false;
        }

        if (markerGroup.matchType() != MarkerGroupMatchType.REGEX) {
            return true;
        }

        try {
            Pattern.compile(markerGroup.prefix());
            return true;
        } catch (PatternSyntaxException e) {
            LOGGER.warn(
                    "Marker group '{}' has an invalid REGEX prefix '{}', it will be ignored: {}",
                    markerGroup.name(), markerGroup.prefix(), e.getMessage());
            return false;
        }
    }

    public SignLinesParseResult parse(String[] lines) {
        var state = ParseStates.START;

        var context = new ParsingContext();

        for (String line : lines) {
            line = trimLine(truncateLine(line));
            if (state == ParseStates.START) {
                state = processStartState(line, context, markerGroups);
            } else if (state == ParseStates.HAS_MARKER_TYPE) {
                processHasMarkerType(line, context);
            }
        }

        return state == ParseStates.INVALID
                ? new SignLinesParseResult(null, "", "")
                : context.buildResult();
    }

    private static String trimLine(String line) {
        return INVISIBLE_WHITESPACE_AT_EDGES.matcher(line).replaceAll("");
    }

    private static String truncateLine(String line) {
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }

        int end = Character.isHighSurrogate(line.charAt(MAX_LINE_LENGTH - 1)) ? MAX_LINE_LENGTH - 1 : MAX_LINE_LENGTH;
        return line.substring(0, end);
    }

    private static ParseStates processStartState(
            String line,
            ParsingContext context,
            List<MarkerGroup> markerGroups) {
        if (line.isEmpty()) {
            return ParseStates.START;
        }

        context.setMarkerGroup(getMarkerGroup(line, markerGroups));
        if (context.getMarkerGroup() == null) {
            return ParseStates.INVALID;
        }

        context.setLabel(getLabel(line, context.getMarkerGroup()));
        if (!context.getLabel().isEmpty()) {
            context.appendDetail(context.getLabel());
        }
        return ParseStates.HAS_MARKER_TYPE;
    }

    private static void processHasMarkerType(String line, ParsingContext context) {
        if (line.isEmpty()) {
            return;
        }

        if (context.getLabel().isEmpty()) {
            context.setLabel(line);
        }

        context.appendDetail(line);
    }

    private static MarkerGroup getMarkerGroup(String line, List<MarkerGroup> markerGroups) {
        return markerGroups.stream()
                .filter(markerGroup -> lineMatchesMarkerGroup(line, markerGroup))
                .findFirst()
                .orElse(null);
    }

    private static boolean lineMatchesMarkerGroup(String line, MarkerGroup markerGroup) {
        if (markerGroup.matchType() == MarkerGroupMatchType.REGEX) {
            return line.matches(markerGroup.prefix());
        }

        // Default match type -> STARTS_WITH
        return line.startsWith(markerGroup.prefix());
    }

    private static String getLabel(String line, MarkerGroup markerGroup) {
        if (markerGroup.matchType() == MarkerGroupMatchType.REGEX) {
            return line.replaceAll(markerGroup.prefix(), "").trim();
        }

        // Default match type -> STARTS_WITH
        return line.substring(markerGroup.prefix().length()).trim();
    }
}
