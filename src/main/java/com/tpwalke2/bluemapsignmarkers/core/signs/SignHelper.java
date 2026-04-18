package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import java.util.Arrays;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public class SignHelper {
    private SignHelper() {
    }

    private static final SignLinesParser signLinesParser = new SignLinesParser(Arrays.asList(ConfigManager.get().getMarkerGroups()));

    public static SignEntry createSignEntry(
            SignBlockEntity signBlockEntity,
            String playerId) {
        var pos = signBlockEntity.getBlockPos();

        return new SignEntry(
                new SignEntryKey(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        getSignParentMap(signBlockEntity.getLevel())),
                playerId,
                getParsedSignText(signBlockEntity.getFrontText()),
                getParsedSignText(signBlockEntity.getBackText()));
    }

    public static String getSignParentMap(Level world) {
        if (world == null) return WorldMap.UNKNOWN;

        return world.dimension().identifier().toString();
    }

    private static SignLinesParseResult getParsedSignText(SignText signText) {
        return signLinesParser
                .parse(Arrays.stream(signText.getMessages(false))
                        .map(Component::getString)
                        .toArray(String[]::new));
    }
}