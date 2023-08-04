package com.tpwalke2.bluemapsignmarkers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockPlacementCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class BlueMapSignMarkersMod implements ModInitializer {

	@Override
	public void onInitialize() {
		// Register event listeners
		registerEventListeners();
	}

	private void registerEventListeners() {
		// Listen for server starting
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

		// Listen for player block placement
		PlayerBlockPlacementCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient) return ActionResult.PASS;

			BlockPos pos = hitResult.getBlockPos();
			BlockState blockState = world.getBlockState(pos);
			if (blockState.getBlock() instanceof SignBlock) {
				// Handle sign placement event
				handleSignPlacement(player, (ServerWorld) world, pos);
			}

			return ActionResult.PASS;
		});
	}

	private void handleSignPlacement(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
		// Implement your logic to handle sign placement here
		// Check the text of the sign and create POI markers if necessary using the BlueMap API.
	}

	private void onServerStarted(MinecraftServer server) {
		// Implement your logic to handle existing signs during mod initialization
		// Scan loaded chunks for existing signs and create POI markers if necessary using the BlueMap API.
	}
}
