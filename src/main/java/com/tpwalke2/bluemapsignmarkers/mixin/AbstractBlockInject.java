package com.tpwalke2.bluemapsignmarkers.mixin;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryKey;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.class)
public class AbstractBlockInject {
    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (world.isClient) return;

        if (!(state.getBlock() instanceof AbstractSignBlock)) return;
        if (state.getBlock() == newState.getBlock()) return;

        SignManager.remove(new SignEntryKey(pos.getX(), pos.getY(), pos.getZ(), SignHelper.getSignParentMap(world)));
    }
}
