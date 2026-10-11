package com.terracraft.geo.mixin.client;

import com.terracraft.geo.SightClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** En visée, la souris ralentit d'autant que le zoom : la visée reste précise à x6. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
    private double terracraft$slowYaw(double yaw) {
        return yaw / SightClient.zoom();
    }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
    private double terracraft$slowPitch(double pitch) {
        return pitch / SightClient.zoom();
    }
}
