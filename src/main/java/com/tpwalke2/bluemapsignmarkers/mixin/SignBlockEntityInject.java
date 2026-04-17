package com.tpwalke2.bluemapsignmarkers.mixin;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;

@Mixin(SignBlockEntity.class)
public class SignBlockEntityInject {
    @Inject(method = "updateSignText", at = @At("TAIL"))
    void onTryChangeText(
            Player player,
            boolean frontText,
            List<FilteredText> lines,
            CallbackInfo cir) {
        SignManager.addOrUpdate(SignHelper.createSignEntry(
                (SignBlockEntity) (Object) this,
                player.getStringUUID()));
    }
}
