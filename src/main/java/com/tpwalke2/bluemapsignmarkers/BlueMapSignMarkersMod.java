package com.tpwalke2.bluemapsignmarkers;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignProvider;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

public class BlueMapSignMarkersMod implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(this::onBlockEntityLoad);
	}

	private void onServerStarting(MinecraftServer server) {
		SignProvider.loadSigns(getMarkerFilePath(server));
	}

	private void onServerStopping(MinecraftServer server) {
		SignProvider.saveSigns(getMarkerFilePath(server));

		SignManager.stop();
	}

	private String getMarkerFilePath(MinecraftServer server) {
		var worldSaveName = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().getParent().getFileName();
		return String.format("config/%s/%s/signs.json", Constants.MOD_ID, worldSaveName);
	}

	private void onBlockEntityLoad(BlockEntity blockEntity, ServerWorld world) {
		if (!(blockEntity instanceof SignBlockEntity castBlockEntity)) return;

		SignManager.addOrUpdate(SignHelper.createSignEntry(castBlockEntity, "unknown"));
	}
}
