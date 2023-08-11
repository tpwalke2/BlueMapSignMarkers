package com.tpwalke2.bluemapsignmarkers;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class BlueMapSignMarkersMod implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {
		registerEventListeners();
	}

	private void registerEventListeners() {
		// Listen for server starting
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
	}

	private void handleSignPlacement(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
		// TODO setup MIXIN to intercept SignBlockEntity changeText
		// Implement your logic to handle sign placement here
		// Check the text of the sign and create POI markers if necessary using the BlueMap API.
	}

	private void onServerStarted(MinecraftServer server) {
		// Implement your logic to handle existing signs during mod initialization
		// Scan loaded chunks for existing signs and create POI markers if necessary using the BlueMap API.
	}
}
