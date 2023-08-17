package com.tpwalke2.bluemapsignmarkers;

import com.tpwalke2.bluemapsignmarkers.core.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.SignParentMap;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public class SignBlockEntityHelper {
    public static SignEntry createSignEntry(SignBlockEntity signBlockEntity) {
        var pos = signBlockEntity.getPos();

        return new SignEntry(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                getSignParentMap(signBlockEntity.getWorld()),
                getSignText(signBlockEntity.getFrontText()),
                getSignText(signBlockEntity.getBackText()));
    }

    private static SignParentMap getSignParentMap(World world) {
        if (world == null) return SignParentMap.UNKNOWN;

        var registryKey = world.getRegistryKey();

        if (registryKey.equals(World.NETHER)) return SignParentMap.NETHER;
        if (registryKey.equals(World.END)) return SignParentMap.END;
        if (registryKey.equals(World.OVERWORLD)) return SignParentMap.OVERWORLD;

        return SignParentMap.UNKNOWN;
    }

    private static String getSignText(SignText signText) {
        var text = new StringBuilder();

        var messages = signText.getMessages(false);

        for (Text message : messages) {
            text.append(message.getString());
        }

        return text.toString().trim();
    }
}