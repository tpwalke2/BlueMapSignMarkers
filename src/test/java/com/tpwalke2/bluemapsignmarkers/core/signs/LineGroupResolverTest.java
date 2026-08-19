package com.tpwalke2.bluemapsignmarkers.core.signs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineGroupResolverTest {

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
        var member = signEntry(0, 64, 0, "minecraft:overworld", "[trail]", "Ridge Trail", 1000L);
        var otherMap = signEntry(1, 64, 1, "minecraft:the_nether", "[trail]", "Ridge Trail", 1001L);
        var otherPrefix = signEntry(2, 64, 2, "minecraft:overworld", "[poi]", "Ridge Trail", 1002L);
        var otherLabel = signEntry(3, 64, 3, "minecraft:overworld", "[trail]", "Coast Trail", 1003L);

        var result = LineGroupResolver.members(
                List.of(member, otherMap, otherPrefix, otherLabel), "minecraft:overworld", "[trail]", "Ridge Trail");

        assertEquals(List.of(member), result);
    }

    @Test
    void ordersMembersByCreatedAtMillisAscending() {
        var third = signEntry(0, 64, 0, "minecraft:overworld", "[trail]", "Ridge Trail", 3000L);
        var first = signEntry(1, 64, 1, "minecraft:overworld", "[trail]", "Ridge Trail", 1000L);
        var second = signEntry(2, 64, 2, "minecraft:overworld", "[trail]", "Ridge Trail", 2000L);

        var result = LineGroupResolver.members(
                List.of(third, first, second), "minecraft:overworld", "[trail]", "Ridge Trail");

        assertEquals(List.of(first, second, third), result);
    }

    @Test
    void breaksTiesOnDuplicateCreatedAtMillisByPositionXThenYThenZ() {
        // Simulates two pre-existing signs migrated from different region files that happened to land on the
        // same fileLastModifiedMillis + indexInFile timestamp (see ticket 02's cross-file-ties note) - ordering
        // must still be deterministic rather than depending on input/iteration order.
        var higherZ = signEntry(0, 64, 2, "minecraft:overworld", "[trail]", "Ridge Trail", 1000L);
        var higherY = signEntry(0, 65, 0, "minecraft:overworld", "[trail]", "Ridge Trail", 1000L);
        var higherX = signEntry(1, 64, 0, "minecraft:overworld", "[trail]", "Ridge Trail", 1000L);
        var lowest = signEntry(0, 64, 0, "minecraft:overworld", "[trail]", "Ridge Trail", 1000L);

        var result = LineGroupResolver.members(
                List.of(higherZ, higherY, higherX, lowest), "minecraft:overworld", "[trail]", "Ridge Trail");

        assertEquals(List.of(lowest, higherZ, higherY, higherX), result);
    }

    @Test
    void emptyInputProducesNoMembers() {
        var result = LineGroupResolver.members(List.of(), "minecraft:overworld", "[trail]", "Ridge Trail");

        assertEquals(List.of(), result);
    }
}
