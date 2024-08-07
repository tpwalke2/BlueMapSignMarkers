package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Arrays;

public class SignHelper {
    private SignHelper() {
    }

    private static final SignLinesParser signLinesParser = new SignLinesParser(Arrays.asList(ConfigManager.get().getMarkerGroups()));

    public static SignEntry createSignEntry(
            SignBlockEntity signBlockEntity,
            String playerId) {
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

    public static String getSignParentMap(World world) {
        if (world == null) return WorldMap.UNKNOWN;

        return world.getRegistryKey().getValue().toString();
    }

    private static SignLinesParseResult getParsedSignText(SignText signText) {
        return signLinesParser
                .parse(Arrays.stream(signText.getMessages(false))
                        .map(Text::getString)
                        .toArray(String[]::new));
    }
}