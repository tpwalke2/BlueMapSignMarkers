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

    private static volatile SignLinesParser signLinesParser = buildParser();

    private static SignLinesParser buildParser() {
        return new SignLinesParser(Arrays.asList(ConfigManager.get().getMarkerGroups()));
    }

    public static void reloadParser() {
        signLinesParser = buildParser();
    }

    public static SignEntry createSignEntry(
            SignBlockEntity signBlockEntity,
            String playerId) {
        var pos = signBlockEntity.getBlockPos();
        var frontRawLines = getRawLines(signBlockEntity.getFrontText());
        var backRawLines = getRawLines(signBlockEntity.getBackText());

        return new SignEntry(
                new SignEntryKey(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        getSignParentMap(signBlockEntity.getLevel())),
                playerId,
                signLinesParser.parse(frontRawLines),
                signLinesParser.parse(backRawLines),
                System.currentTimeMillis(),
                frontRawLines,
                backRawLines);
    }

    public static String getSignParentMap(Level world) {
        if (world == null) return WorldMap.UNKNOWN;

        return world.dimension().identifier().toString();
    }

    private static String[] getRawLines(SignText signText) {
        return Arrays.stream(signText.getMessages(false))
                .map(Component::getString)
                .toArray(String[]::new);
    }
}