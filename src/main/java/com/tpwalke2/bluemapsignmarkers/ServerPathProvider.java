package com.tpwalke2.bluemapsignmarkers;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

public interface ServerPathProvider {
    Path getMarkerStorageRoot(MinecraftServer server);
}
