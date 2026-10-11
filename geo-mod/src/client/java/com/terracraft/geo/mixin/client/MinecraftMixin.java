package com.terracraft.geo.mixin.client;

import com.terracraft.geo.content.Sights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arme en main : le bouton du milieu sert à viser, pas à « choisir le bloc » (qui changerait l'objet tenu). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public @Nullable LocalPlayer player;

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void terracraft$aimInsteadOfPick(CallbackInfo ci) {
        if (player != null && Sights.accepts(player.getMainHandItem())) {
            ci.cancel();
        }
    }
}
