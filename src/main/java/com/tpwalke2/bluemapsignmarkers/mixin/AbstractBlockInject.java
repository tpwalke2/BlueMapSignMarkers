package com.tpwalke2.bluemapsignmarkers.mixin;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public class AbstractBlockInject {
    @Inject(method = "affectNeighborsAfterRemoval", at = @At("HEAD"))
    public void onStateReplaced(BlockState state, ServerLevel world, BlockPos pos, boolean moved, CallbackInfo ci) {
        if (!(state.getBlock() instanceof SignBlock)) return;

        SignManager.remove(new SignEntryKey(pos.getX(), pos.getY(), pos.getZ(), SignHelper.getSignParentMap(world)));
    }
}
