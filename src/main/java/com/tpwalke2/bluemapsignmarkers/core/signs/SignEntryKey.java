package com.tpwalke2.bluemapsignmarkers.core.signs;

import net.minecraft.world.World;

public record SignEntryKey(int x, int y, int z, String parentMap) {
    public String getParentMap() {
        return parentMap.toLowerCase();
    }

    private static final String NETHER = "nether";
    private static final String END = "end";
    private static final String OVERWORLD = "overworld";

    public SignEntryKey withNormalizedMapId()
    {
        return new SignEntryKey(x, y, z, getNormalizedMapId(parentMap));
    }

    private String getNormalizedMapId(String mapId) {
        var result = mapId.toLowerCase();

        if (result.equals(NETHER)) return World.NETHER.getValue().toString();
        if (result.equals(END)) return World.END.getValue().toString();
        if (result.equals(OVERWORLD)) return World.OVERWORLD.getValue().toString();

        return result;
    }
}
