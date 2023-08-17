package com.tpwalke2.bluemapsignmarkers.mixin;

import com.tpwalke2.bluemapsignmarkers.SignBlockEntityHelper;
import com.tpwalke2.bluemapsignmarkers.core.SignManager;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignBlockEntity.class)
public class SignBlockEntityInject {
    @Inject(method = "setText", at = @At("HEAD"))
    void onSetText(SignText text, boolean front, CallbackInfoReturnable<Boolean> cir) {
        SignManager.addOrUpdate(SignBlockEntityHelper.createSignEntry((SignBlockEntity) (Object) this));
    }
}
