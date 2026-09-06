package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupMatchType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorResolverTest {

    private static final String MAP = "minecraft:overworld";

    private static MarkerGroup lineGroup(boolean allowPlayerColors) {
        return new MarkerGroup("[trail]", MarkerGroupMatchType.STARTS_WITH, MarkerGroupType.LINE,
                "name", null, 0, 0, false, 0.0, 10000000.0, 2, "#00A2FFFF", "#FFA50040", 0, true, true, List.of(),
                allowPlayerColors);
    }

    private static SignEntry member(int x, String dye, long createdAtMillis) {
        return new SignEntry(
                new SignEntryKey(x, 64, 0, MAP),
                "unknown",
                new SignLinesParseResult("[trail]", "Ridge", "detail"),
                new SignLinesParseResult(null, "", ""),
                createdAtMillis,
                null,
                null,
                dye,
                "BLACK");
    }

    @Test
    void allowPlayerColorsOffReturnsConfiguredColorsUnchangedEvenWhenDyed() {
        var group = lineGroup(false);
        var members = List.of(member(0, "RED", 1000L));

        var resolved = ColorResolver.resolve(members, group);

        assertEquals("#00A2FFFF", resolved.lineColor());
        assertEquals("#FFA50040", resolved.fillColor());
    }

    @Test
    void allowPlayerColorsOnWithNoDyedMembersReturnsConfiguredColorsUnchanged() {
        var group = lineGroup(true);
        var members = List.of(member(0, "BLACK", 1000L), member(1, "BLACK", 2000L));

        var resolved = ColorResolver.resolve(members, group);

        assertEquals("#00A2FFFF", resolved.lineColor());
        assertEquals("#FFA50040", resolved.fillColor());
    }

    @Test
    void allowPlayerColorsOnWithADyedMemberReplacesHueButKeepsConfiguredAlpha() {
        var group = lineGroup(true);
        var members = List.of(member(0, "RED", 1000L));

        var resolved = ColorResolver.resolve(members, group);

        assertEquals("#B02E26FF", resolved.lineColor());
        assertEquals("#B02E2640", resolved.fillColor());
    }

    @Test
    void earliestPlacedDyedMemberWinsOverALaterDyedMember() {
        var group = lineGroup(true);
        var members = List.of(member(0, "RED", 1000L), member(1, "BLUE", 2000L));

        var resolved = ColorResolver.resolve(members, group);

        assertEquals("#B02E26FF", resolved.lineColor());
    }

    @Test
    void resolvesTheEarliestPlacedWinnerEvenWhenMembersAreNotPassedInPlacementOrder() {
        var group = lineGroup(true);
        var members = List.of(member(1, "BLUE", 2000L), member(0, "RED", 1000L));

        var resolved = ColorResolver.resolve(members, group);

        assertEquals("#B02E26FF", resolved.lineColor());
    }

    @Test
    void anUndyedEarlierMemberDoesNotBlockALaterDyedMemberFromWinning() {
        var group = lineGroup(true);
        var members = List.of(member(0, "BLACK", 1000L), member(1, "BLUE", 2000L));

        var resolved = ColorResolver.resolve(members, group);

        assertEquals("#3C44AAFF", resolved.lineColor());
    }
}
