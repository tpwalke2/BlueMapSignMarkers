package com.tpwalke2.bluemapsignmarkers.common;

import com.tpwalke2.bluemapsignmarkers.Constants;

import java.nio.file.Path;

// Pure Path/String math extracted from BlueMapSignMarkersMod so it's directly unit testable (that
// class's own signature is Minecraft-coupled - see AGENTS.md's testable-vs-game-coupled split). Both
// methods take the same raw, un-normalized Path a caller gets from
// server.getWorldPath(LevelResource.ROOT).toAbsolutePath() - normalizing (or not) is each method's own
// concern below, not the caller's.
public class ServerPathResolver {
    private ServerPathResolver() {}

    public static Path resolveMarkerStorageRoot(Path rawLevelPath) {
        // normalize() is required: LevelResource.ROOT's relative path is ".", so without it rawLevelPath
        // keeps an unresolved trailing "." segment, shifting getParent()/getFileName() by one level
        // (serverRoot would resolve to the level dir itself, and levelName to "." instead of the level
        // name).
        var levelDir = rawLevelPath.normalize();
        var serverRoot = levelDir.getParent();
        var levelName = levelDir.getFileName();

        return serverRoot.resolve(Constants.MOD_ID).resolve(levelName);
    }

    // Pre-existing (buggy) formula kept as-is: it resolves to the run directory's name, not the level
    // name, which is exactly what's on disk for every install predating region-sharded storage.
    // Migration needs to find files at the path they were actually written to, not the corrected one
    // resolveMarkerStorageRoot uses above.
    public static String resolveLegacyMarkerFilePath(Path rawLevelPath) {
        var worldSaveName = rawLevelPath.getParent().getFileName();
        return String.format("config/%s/%s/signs.json", Constants.MOD_ID, worldSaveName);
    }
}
