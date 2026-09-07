package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SignLinesParserTest {

    private static MarkerGroup startsWithGroup(String prefix, String name) {
        return new MarkerGroup(prefix, MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.POI, name, null, 0, 0, false, 0.0, 10000000.0, 2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
    }

    private static MarkerGroup regexGroup(String pattern, String name) {
        return new MarkerGroup(pattern, MarkerGroupMatchType.REGEX, MarkerGroupType.POI, name, null, 0, 0, false, 0.0, 10000000.0, 2, "#FF0000FF", "#FF000033", 0, true, true, List.of(), false);
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
    void nullPrefixIsSkippedInsteadOfThrowing() {
        var parser = new SignLinesParser(List.of(
                startsWithGroup(null, "Broken"),
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

    @Test
    void nonAsciiInvisibleWhitespaceOnlyLineIsTreatedAsBlank() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        // NBSP, zero-width space, ideographic space - all pasteable but invisible.
        var result = parser.parse(new String[]{"\u00A0", "\u200B", "\u3000", "[poi] Town Hall"});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
        assertEquals("Town Hall", result.detail());
    }

    @Test
    void nonAsciiInvisibleWhitespaceSurroundingPrefixLineIsTrimmed() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var result = parser.parse(new String[]{"\u00A0[poi] Town Hall\u3000"});

        assertEquals("[poi]", result.prefix());
        assertEquals("Town Hall", result.label());
    }

    @Test
    void regexPrefixWithTrailingLabelTextOnTheSameLineResultsInBlankLabel() {
        // Known limitation (see AGENTS.md): REGEX uses line.matches(...), which requires the whole
        // line to match. A pattern loosened to also accept trailing label text on the prefix line is
        // the only way to make matches() accept it, but getLabel's replaceAll then greedily strips the
        // entire line - including the label - leaving it blank. This test locks in that behavior rather
        // than silently regressing further.
        var parser = new SignLinesParser(List.of(regexGroup("\\[poi\\].*", "Points of Interest")));

        var result = parser.parse(new String[]{"[poi] Town Hall"});

        assertEquals("\\[poi\\].*", result.prefix());
        assertEquals("", result.label());
        assertEquals("", result.detail());
    }

    @Test
    @Timeout(5)
    void pathologicalRegexAgainstOversizedLineDoesNotHang() {
        // A single nested quantifier ((a+)+) is the textbook catastrophic-backtracking example, but
        // the JVM's own regex engine already special-cases that shape. Several sequential unbounded
        // capturing groups over the same character class ((a*)(a*)(a*)(a*)) still exhibits genuine
        // combinatorial blowup that grows sharply with input length - without a cap, matching this
        // against tens of thousands of characters would not finish in any practical time. The
        // line-length cap truncates the line to a fixed size before it ever reaches line.matches(...),
        // so the match cost stays bounded regardless of how long the attacking sign text is.
        var parser = new SignLinesParser(List.of(regexGroup("(a*)(a*)(a*)(a*)b", "Pathological")));

        var adversarialLine = "a".repeat(50_000);
        var result = parser.parse(new String[]{adversarialLine});

        assertNull(result.prefix());
    }

    @Test
    void oversizedLineIsTruncatedBeforeMatching() {
        var parser = new SignLinesParser(List.of(startsWithGroup("[poi]", "Points of Interest")));

        var prefix = "[poi] ";
        var longLabel = "x".repeat(200);
        var result = parser.parse(new String[]{prefix + longLabel});

        assertEquals("[poi]", result.prefix());
        assertEquals(100 - prefix.length(), result.label().length());
    }
}
