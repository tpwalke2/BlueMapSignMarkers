package com.tpwalke2.bluemapsignmarkers.mixin;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.filter.FilteredMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SignBlockEntity.class)
public class SignBlockEntityInject {
    @Inject(method = "tryChangeText", at = @At("TAIL"))
    void onTryChangeText(PlayerEntity player, boolean front, List<FilteredMessage> messages, CallbackInfo cir) {
        SignManager.addOrUpdate(SignHelper.createSignEntry((SignBlockEntity) (Object) this, player.getUuidAsString()));
    }
}
