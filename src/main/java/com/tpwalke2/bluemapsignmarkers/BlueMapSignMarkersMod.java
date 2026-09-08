package com.tpwalke2.bluemapsignmarkers;

import com.tpwalke2.bluemapsignmarkers.common.SafeCall;
import com.tpwalke2.bluemapsignmarkers.common.ServerPathResolver;
import com.tpwalke2.bluemapsignmarkers.core.signs.PlayerIds;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.SignProvider;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class BlueMapSignMarkersMod implements DedicatedServerModInitializer, ServerPathProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

	@Override
	public void onInitializeServer() {
		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(this::onBlockEntityLoad);
		ServerChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);
	}

	private void onServerStarting(MinecraftServer server) {
		SafeCall.run("onServerStarting",
				() -> SignProvider.loadSigns(getMarkerStorageRoot(server), getLegacyMarkerFilePath(server)));
	}

	private void onServerStopping(MinecraftServer server) {
		SafeCall.run("onServerStopping.saveSigns", () -> SignProvider.saveSigns(getMarkerStorageRoot(server)));

		SafeCall.run("onServerStopping.stop", SignManager::stop);
	}

	@Override
	public Path getMarkerStorageRoot(MinecraftServer server) {
		return ServerPathResolver.resolveMarkerStorageRoot(server.getWorldPath(LevelResource.ROOT).toAbsolutePath());
	}

	private String getLegacyMarkerFilePath(MinecraftServer server) {
		return ServerPathResolver.resolveLegacyMarkerFilePath(server.getWorldPath(LevelResource.ROOT).toAbsolutePath());
	}

	private void onBlockEntityLoad(BlockEntity blockEntity, ServerLevel world) {
		if (!(blockEntity instanceof SignBlockEntity castBlockEntity)) return;

		SafeCall.run("onBlockEntityLoad",
				() -> SignManager.addOrUpdate(SignHelper.createSignEntry(castBlockEntity, PlayerIds.UNKNOWN)));
	}

	// No special case for a newly-generated chunk (generated == true): that flag also fires when a chunk's saved
	// data is missing and Minecraft regenerates it fresh - exactly the "region file deleted externally" case this
	// reconciliation targets. Skipping it there would defeat the main use case, and there's no perf reason to:
	// getKeysInChunk is a single hashmap lookup, so the cost is the same either way.
	private void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		SafeCall.run("onChunkLoad", () -> {
			var parentMap = SignHelper.getSignParentMap(level);
			var chunkPos = chunk.getPos();

			for (var key : SignManager.getKeysInChunk(parentMap, chunkPos.x(), chunkPos.z())) {
				if (!(chunk.getBlockEntity(new BlockPos(key.x(), key.y(), key.z())) instanceof SignBlockEntity)) {
					LOGGER.info("Removing stale sign marker at {} - no sign block found on chunk load "
							+ "(external deletion/regen?)", key);
					SignManager.remove(key);
				}
			}
		});
	}
}
