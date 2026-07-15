package com.tpwalke2.bluemapsignmarkers;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.SignProvider;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public class BlueMapSignMarkersMod implements DedicatedServerModInitializer, ServerPathProvider {

	@Override
	public void onInitializeServer() {
		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(this::onBlockEntityLoad);
	}

	private void onServerStarting(MinecraftServer server) {
		SignProvider.loadSigns(getMarkerStorageRoot(server), getLegacyMarkerFilePath(server));
	}

	private void onServerStopping(MinecraftServer server) {
		SignProvider.saveSigns(getMarkerStorageRoot(server));

		SignManager.stop();
	}

	@Override
	public Path getMarkerStorageRoot(MinecraftServer server) {
		// normalize() is required: LevelResource.ROOT's relative path is ".", so without it levelDir keeps an
		// unresolved trailing "." segment, shifting getParent()/getFileName() by one level (serverRoot would
		// resolve to the level dir itself, and levelName to "." instead of the level name).
		var levelDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		var serverRoot = levelDir.getParent();
		var levelName = levelDir.getFileName();

		return serverRoot.resolve(Constants.MOD_ID).resolve(levelName);
	}

	// Pre-existing (buggy) formula kept as-is: it resolves to the run directory's name, not the level name,
	// which is exactly what's on disk for every install predating region-sharded storage. Migration needs to
	// find files at the path they were actually written to, not the corrected one getMarkerStorageRoot uses.
	private String getLegacyMarkerFilePath(MinecraftServer server) {
		var worldSaveName = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().getParent().getFileName();
		return String.format("config/%s/%s/signs.json", Constants.MOD_ID, worldSaveName);
	}

	private void onBlockEntityLoad(BlockEntity blockEntity, ServerLevel world) {
		if (!(blockEntity instanceof SignBlockEntity castBlockEntity)) return;

		SignManager.addOrUpdate(SignHelper.createSignEntry(castBlockEntity, "unknown"));
	}
}
