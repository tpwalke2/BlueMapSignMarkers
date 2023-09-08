package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Arrays;

public class SignHelper {
    private SignHelper() {}

    public static SignEntry createSignEntry(SignBlockEntity signBlockEntity, String playerId) {
        var pos = signBlockEntity.getPos();

        return new SignEntry(
                new SignEntryKey(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        getSignParentMap(signBlockEntity.getWorld())),
                playerId,
                getParsedSignText(signBlockEntity.getFrontText()),
                getParsedSignText(signBlockEntity.getBackText()));
    }

    public static WorldMap getSignParentMap(World world) {
        if (world == null) return WorldMap.UNKNOWN;

        var registryKey = world.getRegistryKey();

        if (registryKey.equals(World.NETHER)) return WorldMap.NETHER;
        if (registryKey.equals(World.END)) return WorldMap.END;
        if (registryKey.equals(World.OVERWORLD)) return WorldMap.OVERWORLD;

        return WorldMap.UNKNOWN;
    }

    private static SignLinesParseResult getParsedSignText(SignText signText) {
        return SignLinesParser
                .parse(Arrays.stream(signText.getMessages(false))
                        .map(Text::getString)
                        .toArray(String[]::new));
    }
}