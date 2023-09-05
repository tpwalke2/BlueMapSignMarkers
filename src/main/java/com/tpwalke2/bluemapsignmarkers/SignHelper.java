package com.tpwalke2.bluemapsignmarkers;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Arrays;

public class SignHelper {
    private SignHelper() {}

    public static SignEntry createSignEntry(SignBlockEntity signBlockEntity) {
        var pos = signBlockEntity.getPos();

        return new SignEntry(
                new SignEntryKey(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        getSignParentMap(signBlockEntity.getWorld())),
                getSignText(signBlockEntity.getFrontText()),
                getSignText(signBlockEntity.getBackText()));
    }

    public static WorldMap getSignParentMap(World world) {
        if (world == null) return WorldMap.UNKNOWN;

        var registryKey = world.getRegistryKey();

        if (registryKey.equals(World.NETHER)) return WorldMap.NETHER;
        if (registryKey.equals(World.END)) return WorldMap.END;
        if (registryKey.equals(World.OVERWORLD)) return WorldMap.OVERWORLD;

        return WorldMap.UNKNOWN;
    }

    private static String[] getSignText(SignText signText) {
        return Arrays
                .stream(signText.getMessages(false))
                .map(Text::getString)
                .toArray(String[]::new);
    }
}