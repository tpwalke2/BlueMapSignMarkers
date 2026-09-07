package com.tpwalke2.bluemapsignmarkers.mixin;

import com.tpwalke2.bluemapsignmarkers.common.SafeCall;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

@Mixin(SignBlockEntity.class)
public class SignBlockEntityInject {
    // Set for the duration of updateSignText so onUpdateText (below) can tell it's being called from
    // within a plain text edit rather than directly from a dye/ink-sac/glow-ink-sac click, and skip its
    // own dispatch - updateSignText's TAIL already dispatches once the full edit (including the nested
    // updateText call) has completed. Both hooks run on the server main thread for one sign at a time, so
    // this doesn't need to be volatile/synchronized.
    @Unique
    private boolean bluemapsignmarkers$inUpdateSignText;

    @Inject(method = "updateSignText", at = @At("HEAD"))
    void onBeginUpdateSignText(
            Player player,
            boolean frontText,
            List<FilteredText> lines,
            CallbackInfo cir) {
        bluemapsignmarkers$inUpdateSignText = true;
    }

    @Inject(method = "updateSignText", at = @At("TAIL"))
    void onTryChangeText(
            Player player,
            boolean frontText,
            List<FilteredText> lines,
            CallbackInfo cir) {
        bluemapsignmarkers$inUpdateSignText = false;
        SafeCall.run("onTryChangeText", () -> SignManager.addOrUpdate(SignHelper.createSignEntry(
                (SignBlockEntity) (Object) this,
                player.getStringUUID())));
    }

    // updateSignText (above) routes through this same method internally, so without the
    // bluemapsignmarkers$inUpdateSignText guard this would also fire - as a harmless but wasteful
    // duplicate, idempotent recompute - for every plain text edit. This is the only hook that fires for
    // dye/ink-sac/glow-ink-sac application, none of which call updateSignText - see
    // agent-context/plans/player-marker-colors/spec.md "Detecting a dye change: mixin". No Player is
    // available at this injection point, so playerId falls back to WorldMap.UNKNOWN, the same placeholder
    // BlueMapSignMarkersMod.onBlockEntityLoad uses when it has no live player context either.
    @Inject(method = "updateText", at = @At("RETURN"))
    void onUpdateText(
            UnaryOperator<SignText> function,
            boolean isFrontText,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (bluemapsignmarkers$inUpdateSignText) return;

        SafeCall.run("onUpdateText", () -> SignManager.addOrUpdate(SignHelper.createSignEntry(
                (SignBlockEntity) (Object) this,
                WorldMap.UNKNOWN)));
    }
}
