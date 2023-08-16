package com.tpwalke2.bluemapsignmarkers;

import com.tpwalke2.bluemapsignmarkers.core.SignManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.world.ServerWorld;

public class BlueMapSignMarkersMod implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(this::onBlockEntityLoad);
	}

	private void onBlockEntityLoad(BlockEntity blockEntity, ServerWorld world) {
		if (!(blockEntity instanceof SignBlockEntity castBlockEntity)) return;

		SignManager.getInstance().addSign(SignBlockEntityHelper.createSignEntry(castBlockEntity, true));
		SignManager.getInstance().addSign(SignBlockEntityHelper.createSignEntry(castBlockEntity, false));
	}
}
