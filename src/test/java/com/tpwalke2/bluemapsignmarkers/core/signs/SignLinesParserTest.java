package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SignLinesParserTest {

    private static MarkerGroup startsWithGroup(String prefix, String name) {
        return new MarkerGroup(prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, name, null, 0, 0, false, 0.0, 10000000.0);
    }

    private static MarkerGroup regexGroup(String pattern, String name) {
        return new MarkerGroup(pattern, MarkerGroupMatchType.REGEX, MarkerGroupType.POI, name, null, 0, 0, false, 0.0, 10000000.0);
    }

    @Test
    void labelOnPrefixLine() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"[poi] Town Hall"});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
        assertEquals("Town Hall", result.detail());
    }

    @Test
    void labelOnFollowingLine() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"[poi]", "Town Hall"});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
        assertEquals("Town Hall", result.detail());
    }

    @Test
    void multiLineDetailIsJoinedAndTrimmed() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"[poi]", "Town Hall", "Open 9-5", "Ask for Bob"});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
        assertEquals("Town Hall\nOpen 9-5\nAsk for Bob", result.detail());
    }

    @Test
    void leadingBlankLinesAreSkipped() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"", "", "[poi] Town Hall"});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
    }

    @Test
    void blankLinesBetweenContentAreSkipped() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"[poi]", "Town Hall", "", "Open 9-5"});

        assertEquals("Town Hall\nOpen 9-5", result.detail());
    }

    @Test
    void noMatchingGroupReturnsEmptyResult() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"Just a regular sign", "with some text"});

        assertNull(result.prefix());
        assertEquals("", result.label());
        assertEquals("", result.detail());
    }

    @Test
    void allBlankSignReturnsEmptyResult() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"", "", "", ""});

        assertNull(result.prefix());
        assertEquals("", result.label());
        assertEquals("", result.detail());
    }

    @Test
    void regexMatchTypeRequiresWholeLineMatch() {
        var parser = new SignLinesParser(List.of(regexGroup("\\[[vV][iI][lL][lL][aA][gG][eE]\\]", "Villages")));

        // Unlike STARTS_WITH, REGEX uses line.matches(...), which requires the whole line to match -
        // trailing text on the prefix line means no match at all.
        var noMatchResult = parser.parse(new String[]{"[Village] Riverside"});
        assertNull(noMatchResult.prefix());

        var result = parser.parse(new String[]{"[Village]", "Riverside"});

        assertEquals("\\[[vV][iI][lL][lL][aA][gG][eE]\\]", result.prefix());
        assertEquals("Riverside", result.label());
        assertEquals("Riverside", result.detail());
    }

    @Test
    void malformedRegexPrefixIsSkippedInsteadOfThrowing() {
        var parser = new SignLinesParser(List.of(
                regexGroup("[unclosed", "Broken"),
                startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"[poi] Town Hall"});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
        assertEquals("Town Hall", result.detail());
    }

    @Test
    void firstMatchingGroupWinsWhenMultipleConfigured() {
        var parser = new SignLinesParser(List.of(
                startsWithGroup("[poi]", "Points of Interest"),
                startsWithGroup("[poi", "Almost POI")));

        var result = parser.parse(new String[]{"[poi] Town Hall"});

        assertEquals("[poi]", result.prefix());
    }

    @Test
    void whitespaceAroundLinesIsTolerated() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"  [poi] Town Hall  ", "  Open 9-5  "});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
        assertEquals("Town Hall\nOpen 9-5", result.detail());
    }
}
