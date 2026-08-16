package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

// Covers SignManager.reparseFromRawLines - the static, game-type-free core of the reload-time self-heal
// fix in agent-context/plans/stale-prefix-orphaned-signs-fix.md. SignManager itself can't be unit tested
// (its constructor touches live BlueMapAPI static state, see AGENTS.md), but this method needs no
// instance, so it's testable directly without triggering the singleton.
class SignManagerTest {

    private static final SignEntryKey KEY = new SignEntryKey(0, 64, 0, "minecraft:overworld");

    private static MarkerGroup regexGroup(String prefix) {
        return new MarkerGroup(prefix, MarkerGroupMatchType.REGEX, MarkerGroupType.POI,
                "name", null, 0, 0, false, 0.0, 10000000.0, 2, "#FF0000FF");
    }

    @Test
    void reparsesUnderTheNewConfigWhenRawLinesArePresent() {
        // Simulates a REGEX group's prefix text being edited between two parser generations - the old
        // parser produced a prefix that no longer matches the new group's pattern, orphaning the sign
        // unless it's reparsed from the raw text it was originally created from.
        var oldParser = new SignLinesParser(List.of(regexGroup("\\[poi-old\\]")));
        var newParser = new SignLinesParser(List.of(regexGroup("\\[poi-new\\]")));
        var frontRawLines = new String[]{"[poi-new]", "Shop"};
        var backRawLines = new String[]{"", "", "", ""};
        var staleEntry = new SignEntry(
                KEY, "unknown", oldParser.parse(frontRawLines), oldParser.parse(backRawLines), 1000L,
                frontRawLines, backRawLines);

        var reparsed = SignManager.reparseFromRawLines(staleEntry, newParser);

        // For a REGEX group, prefix() stores the regex pattern text itself (not the literal sign text) -
        // this is the exact identity-key fragility the fix works around.
        assertEquals("\\[poi-new\\]", reparsed.frontText().prefix());
        assertEquals("Shop", reparsed.frontText().label());
        assertEquals(KEY, reparsed.key());
        assertEquals(1000L, reparsed.createdAtMillis());
    }

    @Test
    void returnsTheSameInstanceUnchangedWhenRawLinesAreMissing() {
        var parser = new SignLinesParser(List.of(regexGroup("\\[poi\\]")));
        var preMigrationEntry = new SignEntry(
                KEY, "unknown", new SignLinesParseResult("[poi]", "Shop", "detail"),
                new SignLinesParseResult(null, "", ""), 1000L, null, null);

        var result = SignManager.reparseFromRawLines(preMigrationEntry, parser);

        assertSame(preMigrationEntry, result, "an entry with no raw text should fall back unchanged");
    }

    @Test
    void returnsTheSameInstanceUnchangedWhenOnlyOneSideHasRawLines() {
        var parser = new SignLinesParser(List.of(regexGroup("\\[poi\\]")));
        var partialEntry = new SignEntry(
                KEY, "unknown", new SignLinesParseResult("[poi]", "Shop", "detail"),
                new SignLinesParseResult(null, "", ""), 1000L, new String[]{"[poi]", "Shop"}, null);

        var result = SignManager.reparseFromRawLines(partialEntry, parser);

        assertSame(partialEntry, result, "a partial reparse must not be attempted - both sides parse together");
    }

    @Test
    void aSignThatNoLongerMatchesAnyGroupReparsesToANonMatchingResult() {
        var oldParser = new SignLinesParser(List.of(regexGroup("\\[poi\\]")));
        var newParser = new SignLinesParser(List.of(regexGroup("\\[line\\]")));
        var frontRawLines = new String[]{"[poi]", "Shop"};
        var backRawLines = new String[]{};
        var entry = new SignEntry(
                KEY, "unknown", oldParser.parse(frontRawLines), oldParser.parse(backRawLines), 1000L,
                frontRawLines, backRawLines);

        var reparsed = SignManager.reparseFromRawLines(entry, newParser);

        assertNull(reparsed.frontText().prefix());
    }
}
