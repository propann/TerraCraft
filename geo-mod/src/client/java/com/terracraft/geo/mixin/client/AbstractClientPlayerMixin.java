package com.terracraft.geo.mixin.client;

import com.terracraft.geo.SightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom des viseurs : même mécanisme que la longue-vue (modificateur de champ de vision), réglage FOV intact. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void terracraft$sightZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        float zoom = SightClient.zoom();
        if (firstPerson && zoom > 1 && (Object) this == Minecraft.getInstance().player) {
            cir.setReturnValue(cir.getReturnValue() / zoom);
        }
    }
}
