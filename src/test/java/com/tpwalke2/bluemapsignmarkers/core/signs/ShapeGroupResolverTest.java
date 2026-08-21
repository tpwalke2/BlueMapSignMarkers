package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Mirrors LineGroupResolverTest - ShapeGroupResolver.members resolves identically to LineGroupResolver.members
// (see docs/adr/0002-shape-duplicates-line-pattern.md).
class ShapeGroupResolverTest {

    private static SignEntry signEntry(int x, int y, int z, String parentMap, String prefix, String label, long createdAtMillis) {
        return new SignEntry(
                new SignEntryKey(x, y, z, parentMap),
                "unknown",
                new SignLinesParseResult(prefix, label, "detail"),
                new SignLinesParseResult(null, "", ""),
                createdAtMillis,
                null,
                null);
    }

    @Test
    void returnsOnlyEntriesMatchingParentMapPrefixAndLabel() {
        var member = signEntry(0, 64, 0, "minecraft:overworld", "[region]", "North Farm", 1000L);
        var otherMap = signEntry(1, 64, 1, "minecraft:the_nether", "[region]", "North Farm", 1001L);
        var otherPrefix = signEntry(2, 64, 2, "minecraft:overworld", "[poi]", "North Farm", 1002L);
        var otherLabel = signEntry(3, 64, 3, "minecraft:overworld", "[region]", "South Farm", 1003L);

        var result = ShapeGroupResolver.members(
                List.of(member, otherMap, otherPrefix, otherLabel), "minecraft:overworld", "[region]", "North Farm");

        assertEquals(List.of(member), result);
    }

    @Test
    void ordersMembersByCreatedAtMillisAscending() {
        var third = signEntry(0, 64, 0, "minecraft:overworld", "[region]", "North Farm", 3000L);
        var first = signEntry(1, 64, 1, "minecraft:overworld", "[region]", "North Farm", 1000L);
        var second = signEntry(2, 64, 2, "minecraft:overworld", "[region]", "North Farm", 2000L);

        var result = ShapeGroupResolver.members(
                List.of(third, first, second), "minecraft:overworld", "[region]", "North Farm");

        assertEquals(List.of(first, second, third), result);
    }

    @Test
    void breaksTiesOnDuplicateCreatedAtMillisByPositionXThenYThenZ() {
        var higherZ = signEntry(0, 64, 2, "minecraft:overworld", "[region]", "North Farm", 1000L);
        var higherY = signEntry(0, 65, 0, "minecraft:overworld", "[region]", "North Farm", 1000L);
        var higherX = signEntry(1, 64, 0, "minecraft:overworld", "[region]", "North Farm", 1000L);
        var lowest = signEntry(0, 64, 0, "minecraft:overworld", "[region]", "North Farm", 1000L);

        var result = ShapeGroupResolver.members(
                List.of(higherZ, higherY, higherX, lowest), "minecraft:overworld", "[region]", "North Farm");

        assertEquals(List.of(lowest, higherZ, higherY, higherX), result);
    }

    @Test
    void emptyInputProducesNoMembers() {
        var result = ShapeGroupResolver.members(List.of(), "minecraft:overworld", "[region]", "North Farm");

        assertEquals(List.of(), result);
    }
}
